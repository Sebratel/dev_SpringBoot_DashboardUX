package br.com.sebratel.consolidador.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao de como chamar o servico HTTP de automacao (Node/Playwright,
 * ver automation/src/server.js), que roda no seu proprio container/porta
 * (3212) - o BFF nao spawna mais um subprocesso local (ver
 * HttpReportJobRunner, que substituiu o antigo NodeProcessReportJobRunner).
 */
@ConfigurationProperties(prefix = "automation")
public class AutomationProperties {

    /** URL base do servico de automacao (ex.: http://186.219.134.246:3212). */
    private String baseUrl = "http://localhost:3212";

    /**
     * Host usado para montar a URL publica do noVNC (ver ReauthController) -
     * por padrao o mesmo host de baseUrl, mas em ambientes onde o BFF chama
     * automation por um nome interno (ex.: rede docker-compose do e2e local,
     * "http://automation:3212") isso nao e alcancavel pelo browser do
     * usuario, entao precisa ser sobrescrito (ex.: "localhost").
     */
    private String publicHost;

    /** Intervalo entre polls ao status do job no servico de automacao (ms). */
    private long pollIntervalMs = 2000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPublicHost() {
        return publicHost;
    }

    public void setPublicHost(String publicHost) {
        this.publicHost = publicHost;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }
}
