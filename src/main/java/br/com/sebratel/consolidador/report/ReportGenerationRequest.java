package br.com.sebratel.consolidador.report;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Body de POST /api/reports/jobs. Datas em formato ISO (yyyy-MM-dd).
 * `mode` e opcional ("api" ou "novnc" - ver ReportJob.getMode()); nulo/vazio
 * vira "api" por padrao.
 */
public record ReportGenerationRequest(
        @NotNull LocalDate dataInicio,
        @NotNull LocalDate dataFim,
        String mode
) {
}
