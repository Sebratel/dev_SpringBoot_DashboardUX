package br.com.sebratel.consolidador.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guarda os jobs em memoria (Map, sem persistencia) e dispara sua execucao
 * numa thread separada, para que a request HTTP de criacao retorne na hora
 * (202 Accepted) em vez de bloquear pelos minutos que a exportacao real leva.
 *
 * O jobId e sempre logado em INFO na criacao (fica no log da aplicacao,
 * mesmo que a resposta do POST se perca no terminal do cliente), e listAll()
 * permite recuperar todos os ids conhecidos via GET /api/reports/jobs.
 */
@Service
public class ReportJobService {

    private static final Logger log = LoggerFactory.getLogger(ReportJobService.class);

    private final Map<String, ReportJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ReportJobRunner runner;

    public ReportJobService(ReportJobRunner runner) {
        this.runner = runner;
    }

    public ReportJob create(LocalDate dataInicio, LocalDate dataFim) {
        ReportJob job = new ReportJob(dataInicio, dataFim);
        jobs.put(job.getId(), job);
        log.info("Job criado: id={} dataInicio={} dataFim={}", job.getId(), dataInicio, dataFim);
        executor.submit(() -> runner.run(job));
        return job;
    }

    public Optional<ReportJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Collection<ReportJob> listAll() {
        return jobs.values();
    }
}
