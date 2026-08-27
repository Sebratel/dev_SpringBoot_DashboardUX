package br.com.sebratel.consolidador.report;

import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Proxy fino para o fluxo de reautenticacao remota do servico de automacao
 * (ver automation/src/reauth.js) - so repassa start/status/stop e monta a
 * URL publica do noVNC (porta 6080 do mesmo host do servico de
 * automacao), que o frontend abre direto numa nova aba do browser do
 * usuario.
 */
@RestController
@RequestMapping("/api/reports/reauth")
public class ReauthController {

    private final AutomationClient automationClient;
    private final AutomationProperties properties;

    public ReauthController(AutomationClient automationClient, AutomationProperties properties) {
        this.automationClient = automationClient;
        this.properties = properties;
    }

    private String novncUrl() {
        String publicUrl = properties.getPublicVncUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl;
        }
        String host = properties.getPublicHost();
        if (host == null || host.isBlank()) {
            host = URI.create(properties.getBaseUrl()).getHost();
        }
        return "http://" + host + ":6080/vnc.html?autoconnect=true&resize=scale";
    }

    public record ReauthResponse(boolean active, boolean loggedIn, String novncUrl) {
    }

    @PostMapping("/start")
    public ReauthResponse start() {
        AutomationReauthStatus status = automationClient.restClient().post()
                .uri("/reauth/start")
                .retrieve()
                .body(AutomationReauthStatus.class);
        return new ReauthResponse(status.active(), status.loggedIn(), novncUrl());
    }

    @GetMapping("/status")
    public ReauthResponse status() {
        AutomationReauthStatus status = automationClient.restClient().get()
                .uri("/reauth/status")
                .retrieve()
                .body(AutomationReauthStatus.class);
        return new ReauthResponse(status.active(), status.loggedIn(), novncUrl());
    }

    @PostMapping("/stop")
    public ReauthResponse stop() {
        AutomationReauthStatus status = automationClient.restClient().post()
                .uri("/reauth/stop")
                .retrieve()
                .body(AutomationReauthStatus.class);
        return new ReauthResponse(status.active(), status.loggedIn(), novncUrl());
    }

    private record AutomationReauthStatus(boolean active, boolean loggedIn) {
    }
}
