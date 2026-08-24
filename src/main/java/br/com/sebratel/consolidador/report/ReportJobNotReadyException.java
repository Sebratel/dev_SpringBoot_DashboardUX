package br.com.sebratel.consolidador.report;

/** Lancada quando o download e pedido antes do job chegar em DONE. */
public class ReportJobNotReadyException extends RuntimeException {

    public ReportJobNotReadyException(String jobId, JobStatus currentStatus) {
        super("Job " + jobId + " ainda nao esta pronto (status atual: " + currentStatus + ")");
    }
}
