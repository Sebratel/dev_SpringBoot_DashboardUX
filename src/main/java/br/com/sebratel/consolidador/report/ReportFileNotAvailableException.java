package br.com.sebratel.consolidador.report;

/** Lancada quando se pede o download de um relatorio (atendimento/hsm) que ainda nao foi baixado para esse job. */
public class ReportFileNotAvailableException extends RuntimeException {

    public ReportFileNotAvailableException(String jobId, String report) {
        super("Job " + jobId + " nao tem um arquivo baixado para o relatorio \"" + report + "\"");
    }
}
