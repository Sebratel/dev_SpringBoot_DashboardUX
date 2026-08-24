package br.com.sebratel.consolidador.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Executa automation/src/index.js como subprocesso e traduz seu stdout em
 * atualizacoes de ReportJob.
 *
 * Protocolo de linhas esperado no stdout do script (ver exportCsv.js):
 *   "PROGRESS {"percent":42,"message":"..."}"  -> progresso parcial
 *   "RESULT {"filePath":"C:\...\arquivo.csv"}" -> caminho do CSV final
 * Qualquer outra linha e apenas logada (texto livre de debug do script).
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
            readOutput(process, job);

            int exitCode = process.waitFor();
            if (exitCode != 0 && job.getStatus() != JobStatus.DONE) {
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

    private void readOutput(Process process, ReportJob job) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line, job);
            }
        }
    }

    private void handleLine(String line, ReportJob job) {
        log.debug("[automation:{}] {}", job.getId(), line);

        if (line.startsWith("PROGRESS ")) {
            parseProgress(line.substring("PROGRESS ".length()), job);
        } else if (line.startsWith("RESULT ")) {
            parseResult(line.substring("RESULT ".length()), job);
        }
    }

    private void parseProgress(String json, ReportJob job) {
        try {
            var node = objectMapper.readTree(json);
            job.updateProgress(node.get("percent").asInt(), node.get("message").asText());
        } catch (IOException e) {
            log.warn("Linha PROGRESS invalida, ignorando: {}", json);
        }
    }

    private void parseResult(String json, ReportJob job) {
        try {
            var node = objectMapper.readTree(json);
            job.markDone(Path.of(node.get("filePath").asText()));
        } catch (IOException e) {
            log.warn("Linha RESULT invalida: {}", json);
            job.markFailed("Relatorio gerado mas caminho do arquivo nao pode ser lido.");
        }
    }
}
