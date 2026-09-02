package br.com.sebratel.consolidador.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Body de resposta de GET /api/reports/jobs/{jobId} e GET /api/reports/jobs.
 *
 * Os campos pid/createdAt/elapsedSeconds/steps/reportDownloadUrls existem
 * para a tela de Auditoria (frontend) - o polling normal da tela principal
 * usa so status/percent/message/downloadUrl.
 */
public record ReportJobStatusResponse(
        String jobId,
        JobStatus status,
        int percent,
        String message,
        String downloadUrl,
        long pid,
        String createdAt,
        long elapsedSeconds,
        List<StepResponse> steps,
        Map<String, String> reportDownloadUrls,
        Map<String, String> errors
) {

    static ReportJobStatusResponse from(ReportJob job) {
        // getResultFile() so fica preenchido quando ha um CSV consolidado
        // final pronto para download (ver ReportJob.markDone(Path)) - o
        // fluxo de dois relatorios concorrentes (markDone(Map, String))
        // ainda nao gera esse arquivo, entao downloadUrl fica null ate a
        // etapa de consolidacao final ser implementada.
        String downloadUrl = job.getStatus() == JobStatus.DONE && job.getResultFile() != null
                ? "/api/reports/jobs/" + job.getId() + "/download"
                : null;

        List<StepResponse> steps = job.getSteps().stream()
                .map(step -> new StepResponse(step.percent(), step.message(), step.timestamp().toString()))
                .toList();

        // Um link por relatorio individual (atendimento/hsm/hsmPosInstalacao)
        // que ja terminou de baixar, mesmo que o job inteiro ainda nao
        // esteja DONE (falha parcial) ou que a consolidacao final
        // (resultFile acima) nao exista. Os bytes ficam no servico de
        // automacao - este link e sempre proxiado pelo BFF (ver
        // ReportJobController.downloadReport).
        Map<String, String> reportDownloadUrls = new LinkedHashMap<>();
        Set<String> availableReports = job.getAvailableReports();
        if (availableReports != null) {
            availableReports.forEach(report ->
                    reportDownloadUrls.put(report, "/api/reports/jobs/" + job.getId() + "/download/" + report));
        }

        return new ReportJobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getPercent(),
                job.getMessage(),
                downloadUrl,
                job.getPid(),
                job.getCreatedAt().toString(),
                job.getElapsed().getSeconds(),
                steps,
                reportDownloadUrls,
                job.getErrors()
        );
    }

    public record StepResponse(int percent, String message, String timestamp) {
    }
}
