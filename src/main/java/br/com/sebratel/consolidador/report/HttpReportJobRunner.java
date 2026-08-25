package br.com.sebratel.consolidador.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chama o servico HTTP de automacao (Node/Playwright, ver
 * automation/src/server.js, porta 3212) em vez de spawnar um subprocesso
 * local via ProcessBuilder (antigo NodeProcessReportJobRunner) - automation
 * agora roda no seu proprio container, entao a comunicacao e via REST:
 * POST /jobs cria o job la, GET /jobs/{id} e consultado em loop (polling)
 * ate DONE/FAILED, e o download de cada relatorio e proxiado por
 * ReportJobController.downloadReport.
 */
@Component
public class HttpReportJobRunner implements ReportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(HttpReportJobRunner.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestClient restClient;
    private final AutomationProperties properties;

    public HttpReportJobRunner(AutomationClient automationClient, AutomationProperties properties) {
        this.restClient = automationClient.restClient();
        this.properties = properties;
    }

    @Override
    public void run(ReportJob job) {
        job.markRunning();

        String automationJobId;
        try {
            AutomationJobCreatedResponse created = restClient.post()
                    .uri("/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AutomationJobRequest(DATE_FORMAT.format(job.getDataInicio()), DATE_FORMAT.format(job.getDataFim())))
                    .retrieve()
                    .body(AutomationJobCreatedResponse.class);
            automationJobId = created.jobId();
            job.setAutomationJobId(automationJobId);
        } catch (Exception e) {
            log.error("Falha ao criar job no servico de automacao", e);
            job.markFailed("Nao foi possivel iniciar a geracao do relatorio (servico de automacao indisponivel?): " + e.getMessage());
            return;
        }

        AutomationJobStatusDto status;
        try {
            do {
                Thread.sleep(properties.getPollIntervalMs());
                status = restClient.get()
                        .uri("/jobs/{id}", automationJobId)
                        .retrieve()
                        .body(AutomationJobStatusDto.class);

                job.setPid(status.pid());
                job.syncProgress(status.percent(), status.message(), toSteps(status.steps()));
            } while ("PENDING".equals(status.status()) || "RUNNING".equals(status.status()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.markFailed("Geracao do relatorio interrompida.");
            return;
        } catch (Exception e) {
            log.error("Falha ao consultar status do job no servico de automacao", e);
            job.markFailed("Falha ao acompanhar o progresso da geracao do relatorio: " + e.getMessage());
            return;
        }

        Set<String> availableReports = Set.copyOf(status.files());
        job.recordAvailableReports(availableReports);

        if ("DONE".equals(status.status())) {
            job.markDone(availableReports,
                    "Relatorios baixados com sucesso (" + String.join(", ", availableReports)
                            + "). A consolidacao final em um unico arquivo sera feita em uma proxima etapa.");
        } else if (status.errors().isEmpty()) {
            job.markFailed(status.message());
        } else {
            String errorList = status.errors().entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining(" | "));
            String successList = availableReports.isEmpty() ? "nenhum" : String.join(", ", availableReports);
            job.markFailed("Falha ao gerar relatorio(s) - " + errorList + ". Concluido(s) com sucesso: " + successList + ".");
        }
    }

    private List<ReportJob.Step> toSteps(List<AutomationStepDto> steps) {
        return steps.stream()
                .map(s -> new ReportJob.Step(s.percent(), s.message(), Instant.parse(s.timestamp())))
                .toList();
    }

    private record AutomationJobRequest(String dateFrom, String dateTo) {
    }

    private record AutomationJobCreatedResponse(String jobId) {
    }

    private record AutomationStepDto(int percent, String message, String timestamp) {
    }

    private record AutomationJobStatusDto(
            String jobId,
            String status,
            int percent,
            String message,
            long pid,
            String createdAt,
            String finishedAt,
            List<AutomationStepDto> steps,
            List<String> files,
            Map<String, String> errors
    ) {
    }
}
