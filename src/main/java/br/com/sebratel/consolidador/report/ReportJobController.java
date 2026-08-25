package br.com.sebratel.consolidador.report;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/reports/jobs")
public class ReportJobController {

    private final ReportJobService service;
    private final AutomationClient automationClient;

    public ReportJobController(ReportJobService service, AutomationClient automationClient) {
        this.service = service;
        this.automationClient = automationClient;
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

    /**
     * Baixa o CSV bruto de UM dos relatorios concorrentes (chave
     * "atendimento"/"hsm"/"hsmPosInstalacao"). Os bytes vivem no servico de
     * automacao (container/porta 3212 - ver ReportJob.getAvailableReports),
     * entao este endpoint faz proxy da resposta em streaming, sem carregar
     * o arquivo inteiro na memoria do BFF (relatorios grandes podem passar
     * de 100MB).
     */
    @GetMapping("/{jobId}/download/{report}")
    public void downloadReport(@PathVariable String jobId, @PathVariable String report, HttpServletResponse response)
            throws IOException {
        ReportJob job = service.find(jobId)
                .orElseThrow(() -> new ReportJobNotFoundException(jobId));

        if (!job.getAvailableReports().contains(report)) {
            throw new ReportFileNotAvailableException(jobId, report);
        }

        automationClient.restClient().get()
                .uri("/jobs/{id}/download/{report}", job.getAutomationJobId(), report)
                .exchange((clientRequest, clientResponse) -> {
                    MediaType contentType = clientResponse.getHeaders().getContentType();
                    response.setContentType(contentType != null ? contentType.toString() : "text/csv");

                    String disposition = clientResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                    if (disposition != null) {
                        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
                    }

                    clientResponse.getBody().transferTo(response.getOutputStream());
                    return null;
                });
    }
}
