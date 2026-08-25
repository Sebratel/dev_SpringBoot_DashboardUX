package br.com.sebratel.consolidador.report;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado de um job de geracao de relatorio, compartilhado entre a thread
 * HTTP que consulta o progresso e a thread que le o stdout do subprocesso
 * Node. Os campos mutaveis usam tipos atomicos porque sao escritos por uma
 * thread e lidos por varias, sem necessidade de um lock explicito.
 *
 * Alem do status "atual" (percent/message), tambem guarda um HISTORICO de
 * etapas (steps) e o PID do processo Node - dados usados so pela tela de
 * Auditoria (ver ReportJobStatusResponse), nao pelo fluxo normal de
 * polling da tela principal.
 */
public class ReportJob {

    private final String id = UUID.randomUUID().toString();
    private final LocalDate dataInicio;
    private final LocalDate dataFim;
    private final Instant createdAt = Instant.now();

    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.PENDING);
    private final AtomicInteger percent = new AtomicInteger(0);
    private final AtomicReference<String> message = new AtomicReference<>("Aguardando inicio...");
    private final AtomicReference<Path> resultFile = new AtomicReference<>();
    /**
     * Caminhos dos CSVs brutos de cada relatorio (chaves: "atendimento",
     * "hsm" - ver REPORT_DEFINITIONS no script Node) apos os dois downloads
     * concorrentes terminarem. So preenchido quando markDone(Map, String) e
     * usado (fluxo de dois relatorios); a consolidacao final num unico
     * arquivo baixavel (resultFile/getResultFile) e uma etapa futura.
     */
    private final AtomicReference<Map<String, Path>> resultFiles = new AtomicReference<>();

    private final AtomicLong pid = new AtomicLong(-1);
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>();
    private final List<Step> steps = new CopyOnWriteArrayList<>();

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

    public Instant getCreatedAt() {
        return createdAt;
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

    public Map<String, Path> getResultFiles() {
        return resultFiles.get();
    }

    /**
     * Registra os arquivos brutos ja baixados com sucesso, independente do
     * job terminar em DONE ou FAILED - por exemplo, quando so um dos dois
     * relatorios concorrentes teve sucesso (ver markFailed logo em
     * seguida), o arquivo do que deu certo nao deve se perder.
     */
    public void recordResultFiles(Map<String, Path> resultFiles) {
        this.resultFiles.set(resultFiles);
    }

    /** PID do processo `node automation/src/index.js`, ou -1 se ainda nao iniciado. */
    public long getPid() {
        return pid.get();
    }

    public Instant getFinishedAt() {
        return finishedAt.get();
    }

    public List<Step> getSteps() {
        return List.copyOf(steps);
    }

    /**
     * Tempo decorrido desde a criacao do job: ate agora, se ainda em
     * andamento, ou ate a conclusao/falha, se ja terminou.
     */
    public Duration getElapsed() {
        Instant end = finishedAt.get();
        return Duration.between(createdAt, end != null ? end : Instant.now());
    }

    public void setPid(long pid) {
        this.pid.set(pid);
    }

    public void markRunning() {
        status.set(JobStatus.RUNNING);
    }

    public void updateProgress(int newPercent, String newMessage) {
        percent.set(newPercent);
        message.set(newMessage);
        steps.add(new Step(newPercent, newMessage, Instant.now()));
    }

    public void markDone(Path filePath) {
        resultFile.set(filePath);
        percent.set(100);
        message.set("Relatorio gerado com sucesso.");
        status.set(JobStatus.DONE);
        finishedAt.set(Instant.now());
    }

    /**
     * Marca a conclusao do download dos relatorios brutos (atendimento +
     * HSM), antes da consolidacao final num unico arquivo baixavel - por
     * isso nao mexe em resultFile/downloadUrl, so registra onde os arquivos
     * ficaram para a proxima etapa (consolidacao) usar.
     */
    public void markDone(Map<String, Path> resultFiles, String message) {
        this.resultFiles.set(resultFiles);
        percent.set(100);
        this.message.set(message);
        status.set(JobStatus.DONE);
        finishedAt.set(Instant.now());
    }

    public void markFailed(String reason) {
        message.set(reason);
        status.set(JobStatus.FAILED);
        finishedAt.set(Instant.now());
    }

    /** Uma atualizacao de progresso registrada no historico, para a tela de Auditoria. */
    public record Step(int percent, String message, Instant timestamp) {
    }
}
