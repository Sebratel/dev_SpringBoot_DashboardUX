package br.com.sebratel.consolidador.report;

/** Body de resposta de POST /api/reports/jobs. */
public record ReportJobCreatedResponse(String jobId) {

    static ReportJobCreatedResponse from(ReportJob job) {
        return new ReportJobCreatedResponse(job.getId());
    }
}
