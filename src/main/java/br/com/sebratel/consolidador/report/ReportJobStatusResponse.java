package br.com.sebratel.consolidador.report;

/** Body de resposta de GET /api/reports/jobs/{jobId}. */
public record ReportJobStatusResponse(
        String jobId,
        JobStatus status,
        int percent,
        String message,
        String downloadUrl
) {

    static ReportJobStatusResponse from(ReportJob job) {
        String downloadUrl = job.getStatus() == JobStatus.DONE
                ? "/api/reports/jobs/" + job.getId() + "/download"
                : null;

        return new ReportJobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getPercent(),
                job.getMessage(),
                downloadUrl
        );
    }
}
