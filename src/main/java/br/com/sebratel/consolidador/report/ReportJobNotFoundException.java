package br.com.sebratel.consolidador.report;

public class ReportJobNotFoundException extends RuntimeException {

    public ReportJobNotFoundException(String jobId) {
        super("Job nao encontrado: " + jobId);
    }
}
