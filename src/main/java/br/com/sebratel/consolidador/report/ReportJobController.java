package br.com.sebratel.consolidador.report;

import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/jobs")
public class ReportJobController {

    private final ReportJobService service;

    public ReportJobController(ReportJobService service) {
        this.service = service;
    }

    /** Lista todos os jobs conhecidos (desde o ultimo restart do BFF) - util para recuperar um jobId perdido. */
    @GetMapping
    public List<ReportJobStatusResponse> listAll() {
        return service.listAll().stream()
                .map(ReportJobStatusResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReportJobCreatedResponse create(@Valid @RequestBody ReportGenerationRequest request) {
        ReportJob job = service.create(request.dataInicio(), request.dataFim());
        return ReportJobCreatedResponse.from(job);
    }

    @GetMapping("/{jobId}")
    public ReportJobStatusResponse status(@PathVariable String jobId) {
        ReportJob job = service.find(jobId)
                .orElseThrow(() -> new ReportJobNotFoundException(jobId));
        return ReportJobStatusResponse.from(job);
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String jobId) {
        ReportJob job = service.find(jobId)
                .orElseThrow(() -> new ReportJobNotFoundException(jobId));

        if (job.getStatus() != JobStatus.DONE || job.getResultFile() == null) {
            throw new ReportJobNotReadyException(jobId, job.getStatus());
        }

        FileSystemResource file = new FileSystemResource(job.getResultFile());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + job.getResultFile().getFileName() + "\"")
                .body(file);
    }

    /**
     * Baixa o CSV bruto de UM dos relatorios concorrentes (chave "atendimento"
     * ou "hsm" - ver REPORT_DEFINITIONS no script Node) assim que ele termina,
     * mesmo antes da consolidacao final existir. Ver ReportJob.recordResultFiles.
     */
    @GetMapping("/{jobId}/download/{report}")
    public ResponseEntity<FileSystemResource> downloadReport(@PathVariable String jobId, @PathVariable String report) {
        ReportJob job = service.find(jobId)
                .orElseThrow(() -> new ReportJobNotFoundException(jobId));

        Map<String, Path> resultFiles = job.getResultFiles();
        Path file = resultFiles != null ? resultFiles.get(report) : null;
        if (file == null) {
            throw new ReportFileNotAvailableException(jobId, report);
        }

        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }
}
