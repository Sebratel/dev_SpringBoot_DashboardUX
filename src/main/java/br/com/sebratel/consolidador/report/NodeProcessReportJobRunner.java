package br.com.sebratel.consolidador.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Executa automation/src/index.js como subprocesso e traduz seu stdout em
 * atualizacoes de ReportJob.
 *
 * Protocolo de linhas esperado no stdout do script (ver exportCsv.js,
 * exportDataHub.js e index.js): o script roda os relatorios "atendimento",
 * "hsm" (Playwright/Matrix) e "hsmPosInstalacao" (API REST do Data Hub) em
 * paralelo e cada um reporta progresso independentemente, identificado pelo
 * campo "report":
 *   "PROGRESS {"report":"atendimento","percent":42,"message":"..."}"
 *   "PROGRESS {"report":"hsm","percent":10,"message":"..."}"
 *   "RESULT {"files":{"atendimento":"C:\...\a.csv","hsm":null},"errors":{"atendimento":null,"hsm":"mensagem do erro"}}"
 * Qualquer outra linha e apenas logada (texto livre de debug do script).
 *
 * `files`/`errors` sao independentes por relatorio - o script roda os dois
 * com Promise.allSettled (nao Promise.all), entao a falha de UM nao impede
 * o outro de terminar; RESULT sempre reflete o resultado real de cada um,
 * nunca "tudo ou nada".
 *
 * O percentual e a mensagem "ao vivo" (via PROGRESS) expostos no ReportJob
 * sao a agregacao dos dois relatorios (media simples de percentual,
 * mensagens concatenadas). O job so e marcado DONE se os dois relatorios
 * terminarem sem erro; se qualquer um falhar, o job e marcado FAILED com
 * uma mensagem que diz exatamente qual relatorio falhou e qual teve
 * sucesso - a consolidacao dos dois CSVs num unico arquivo baixavel fica
 * para uma proxima etapa (por isso RESULT ainda nao produz um
 * resultFile/downloadUrl mesmo quando os dois tem sucesso).
 */
@Component
public class NodeProcessReportJobRunner implements ReportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(NodeProcessReportJobRunner.class);
    private static final DateTimeFormatter CLI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final AutomationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NodeProcessReportJobRunner(AutomationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ReportJob job) {
        job.markRunning();

        ProcessBuilder builder = new ProcessBuilder(
                properties.getNodeExecutable(),
                properties.getScriptPath(),
                "--from=" + CLI_DATE_FORMAT.format(job.getDataInicio()),
                "--to=" + CLI_DATE_FORMAT.format(job.getDataFim())
        );
        builder.directory(new java.io.File(properties.getWorkingDir()));
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            job.setPid(process.pid());
            readOutput(process, job, new ReportProgressAggregator());

            int exitCode = process.waitFor();
            // parseResult ja deixa o job em DONE ou FAILED com uma mensagem
            // especifica assim que a linha RESULT chega - so cai nesse
            // fallback generico se o processo morreu ANTES de emitir RESULT
            // (job ainda em PENDING/RUNNING), por exemplo por um erro na
            // etapa de login.
            boolean resultAlreadyHandled = job.getStatus() == JobStatus.DONE || job.getStatus() == JobStatus.FAILED;
            if (exitCode != 0 && !resultAlreadyHandled) {
                job.markFailed("Processo de automacao terminou com codigo " + exitCode);
            }
        } catch (IOException e) {
            log.error("Falha ao iniciar processo de automacao", e);
            job.markFailed("Nao foi possivel iniciar o processo de geracao do relatorio.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.markFailed("Geracao do relatorio interrompida.");
        }
    }

    private void readOutput(Process process, ReportJob job, ReportProgressAggregator aggregator) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line, job, aggregator);
            }
        }
    }

    private void handleLine(String line, ReportJob job, ReportProgressAggregator aggregator) {
        log.debug("[automation:{}] {}", job.getId(), line);

        if (line.startsWith("PROGRESS ")) {
            parseProgress(line.substring("PROGRESS ".length()), job, aggregator);
        } else if (line.startsWith("RESULT ")) {
            parseResult(line.substring("RESULT ".length()), job);
        }
    }

    private void parseProgress(String json, ReportJob job, ReportProgressAggregator aggregator) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String report = node.get("report").asText();
            int percent = node.get("percent").asInt();
            String message = node.get("message").asText();

            aggregator.update(report, percent, message);
            job.updateProgress(aggregator.combinedPercent(), aggregator.combinedMessage());
        } catch (IOException | NullPointerException e) {
            log.warn("Linha PROGRESS invalida, ignorando: {}", json);
        }
    }

    private void parseResult(String json, ReportJob job) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode filesNode = root.get("files");
            JsonNode errorsNode = root.get("errors");

            Map<String, Path> resultFiles = new LinkedHashMap<>();
            filesNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull()) {
                    resultFiles.put(entry.getKey(), Path.of(entry.getValue().asText()));
                }
            });
            job.recordResultFiles(resultFiles);

            Map<String, String> errors = new LinkedHashMap<>();
            errorsNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull()) {
                    errors.put(entry.getKey(), entry.getValue().asText());
                }
            });

            if (errors.isEmpty()) {
                job.markDone(resultFiles, "Relatórios baixados com sucesso (" + String.join(", ", resultFiles.keySet())
                        + "). A consolidação final em um único arquivo será feita em uma próxima etapa.");
            } else {
                String successList = resultFiles.isEmpty() ? "nenhum" : String.join(", ", resultFiles.keySet());
                String errorList = errors.entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .collect(Collectors.joining(" | "));
                job.markFailed("Falha ao gerar relatório(s) - " + errorList + ". Concluído(s) com sucesso: " + successList + ".");
            }
        } catch (IOException | NullPointerException e) {
            log.warn("Linha RESULT invalida: {}", json);
            job.markFailed("Relatórios gerados mas caminho dos arquivos não pôde ser lido.");
        }
    }

    /** Combina o progresso de N relatorios concorrentes numa unica dupla (percent, message). */
    private static final class ReportProgressAggregator {
        private final Map<String, Integer> percents = new ConcurrentHashMap<>();
        private final Map<String, String> messages = new ConcurrentHashMap<>();

        void update(String report, int percent, String message) {
            percents.put(report, percent);
            messages.put(report, message);
        }

        int combinedPercent() {
            return (int) Math.round(percents.values().stream().mapToInt(Integer::intValue).average().orElse(0));
        }

        String combinedMessage() {
            return messages.values().stream().collect(Collectors.joining(" | "));
        }
    }
}
