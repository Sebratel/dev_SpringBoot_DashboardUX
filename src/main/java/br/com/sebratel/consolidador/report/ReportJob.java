package br.com.sebratel.consolidador.report;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado de um job de geracao de relatorio, compartilhado entre a thread
 * HTTP que consulta o progresso e a thread que le o stdout do subprocesso
 * Node. Os campos mutaveis usam tipos atomicos porque sao escritos por uma
 * thread e lidos por varias, sem necessidade de um lock explicito.
 */
public class ReportJob {

    private final String id = UUID.randomUUID().toString();
    private final LocalDate dataInicio;
    private final LocalDate dataFim;

    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.PENDING);
    private final AtomicInteger percent = new AtomicInteger(0);
    private final AtomicReference<String> message = new AtomicReference<>("Aguardando inicio...");
    private final AtomicReference<Path> resultFile = new AtomicReference<>();

    public ReportJob(LocalDate dataInicio, LocalDate dataFim) {
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public String getId() {
        return id;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public JobStatus getStatus() {
        return status.get();
    }

    public int getPercent() {
        return percent.get();
    }

    public String getMessage() {
        return message.get();
    }

    public Path getResultFile() {
        return resultFile.get();
    }

    public void markRunning() {
        status.set(JobStatus.RUNNING);
    }

    public void updateProgress(int newPercent, String newMessage) {
        percent.set(newPercent);
        message.set(newMessage);
    }

    public void markDone(Path filePath) {
        resultFile.set(filePath);
        percent.set(100);
        message.set("Relatorio gerado com sucesso.");
        status.set(JobStatus.DONE);
    }

    public void markFailed(String reason) {
        message.set(reason);
        status.set(JobStatus.FAILED);
    }
}
