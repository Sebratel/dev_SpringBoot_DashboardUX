package br.com.sebratel.consolidador.report;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP compartilhado para o servico de automacao (ver
 * automation/src/server.js), usado tanto por HttpReportJobRunner (criar job
 * e consultar progresso) quanto por ReportJobController (proxy de
 * download) - uma unica instancia de RestClient reaproveitada em vez de
 * cada um construir a sua.
 */
@Component
public class AutomationClient {

    private final RestClient restClient;

    public AutomationClient(AutomationProperties properties) {
        this.restClient = RestClient.create(properties.getBaseUrl());
    }

    public RestClient restClient() {
        return restClient;
    }
}
