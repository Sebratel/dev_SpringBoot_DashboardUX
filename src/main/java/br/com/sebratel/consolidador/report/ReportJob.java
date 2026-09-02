package br.com.sebratel.consolidador.report;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado de um job de geracao de relatorio, compartilhado entre a thread
 * HTTP que consulta o progresso e a thread que faz polling no servico de
 * automacao (ver HttpReportJobRunner). Os campos mutaveis usam tipos
 * atomicos porque sao escritos por uma thread e lidos por varias, sem
 * necessidade de um lock explicito.
 *
 * Alem do status "atual" (percent/message), tambem guarda um HISTORICO de
 * etapas (steps) e o PID do processo de automacao - dados usados so pela
 * tela de Auditoria (ver ReportJobStatusResponse), nao pelo fluxo normal de
 * polling da tela principal.
 */
public class ReportJob {

    private final String id = UUID.randomUUID().toString();
    private final LocalDate dataInicio;
    private final LocalDate dataFim;
    /** "api" ou "novnc" - ver runReportJob.js no servico de automacao. */
    private final String mode;
    private final Instant createdAt = Instant.now();

    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.PENDING);
    private final AtomicInteger percent = new AtomicInteger(0);
    private final AtomicReference<String> message = new AtomicReference<>("Aguardando inicio...");
    private final AtomicReference<Path> resultFile = new AtomicReference<>();
    /**
     * Chaves dos relatorios ("atendimento"/"hsm"/"hsmPosInstalacao") cujo
     * CSV bruto ja esta disponivel para download no servico de automacao -
     * os bytes em si vivem la, nao neste processo (ver
     * ReportJobController.downloadReport, que faz proxy). So preenchido
     * quando markDone(Set, String) e usado (fluxo dos 3 relatorios); a
     * consolidacao final num unico arquivo baixavel (resultFile/
     * getResultFile) e uma etapa futura.
     */
    private final AtomicReference<Set<String>> availableReports = new AtomicReference<>(Set.of());
    /**
     * Erro de CADA relatorio que falhou ("atendimento"/"hsm"/"hsmPosInstalacao"
     * -> mensagem), mesmo quando outros relatorios deram certo (falha
     * parcial) - permite o frontend mostrar um aviso pontual por relatorio
     * em vez de bloquear a tela inteira so porque um dos tres falhou. Ver
     * recordErrors()/HttpReportJobRunner.
     */
    private final AtomicReference<Map<String, String>> errors = new AtomicReference<>(Map.of());

    private final AtomicLong pid = new AtomicLong(-1);
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>();
    private final List<Step> steps = new CopyOnWriteArrayList<>();
    /** Id do job no servico de automacao (diferente deste id) - usado para proxiar downloads. */
    private final AtomicReference<String> automationJobId = new AtomicReference<>();

    public ReportJob(LocalDate dataInicio, LocalDate dataFim, String mode) {
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.mode = (mode == null || mode.isBlank()) ? "api" : mode;
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

    public String getMode() {
        return mode;
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

    public Set<String> getAvailableReports() {
        return availableReports.get();
    }

    /**
     * Registra quais relatorios brutos ja estao disponiveis, independente
     * do job terminar em DONE ou FAILED - por exemplo, quando so alguns dos
     * 3 relatorios concorrentes tiveram sucesso (ver markFailed logo em
     * seguida), os que deram certo nao devem se perder.
     */
    public void recordAvailableReports(Set<String> availableReports) {
        this.availableReports.set(availableReports);
    }

    public Map<String, String> getErrors() {
        return errors.get();
    }

    /** Erro por relatorio que falhou, independente do job terminar em DONE ou FAILED - ver campo `errors`. */
    public void recordErrors(Map<String, String> errors) {
        this.errors.set(errors);
    }

    /** PID do processo Node do servico de automacao, ou -1 se ainda nao conhecido. */
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

    public String getAutomationJobId() {
        return automationJobId.get();
    }

    public void setAutomationJobId(String automationJobId) {
        this.automationJobId.set(automationJobId);
    }

    public void markRunning() {
        status.set(JobStatus.RUNNING);
    }

    /**
     * Sincroniza percent/message/steps a partir da resposta mais recente do
     * servico de automacao (GET /jobs/{id}) - diferente do antigo modelo de
     * ler stdout linha a linha, aqui o servico ja devolve o HISTORICO
     * completo de steps a cada poll, entao so substituimos a lista local em
     * vez de acumular (evita duplicar steps ja vistos em polls anteriores).
     */
    public void syncProgress(int newPercent, String newMessage, List<Step> newSteps) {
        percent.set(newPercent);
        message.set(newMessage);
        steps.clear();
        steps.addAll(newSteps);
    }

    public void markDone(Path filePath) {
        resultFile.set(filePath);
        percent.set(100);
        message.set("Relatorio gerado com sucesso.");
        status.set(JobStatus.DONE);
        finishedAt.set(Instant.now());
    }

    /**
     * Marca a conclusao do download dos relatorios brutos, antes da
     * consolidacao final num unico arquivo baixavel - por isso nao mexe em
     * resultFile/downloadUrl, so registra quais relatorios ficaram
     * disponiveis para a proxima etapa (consolidacao) usar.
     */
    public void markDone(Set<String> availableReports, String message) {
        this.availableReports.set(availableReports);
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
