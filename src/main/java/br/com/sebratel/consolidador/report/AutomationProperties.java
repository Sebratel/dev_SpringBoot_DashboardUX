package br.com.sebratel.consolidador.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao de onde/como chamar o script de automacao Node existente.
 * Ver application.properties (prefixo "automation").
 */
@ConfigurationProperties(prefix = "automation")
public class AutomationProperties {

    /** Executavel do Node (ex: "node", ou caminho absoluto se nao estiver no PATH). */
    private String nodeExecutable = "node";

    /** Caminho para automation/src/index.js, absoluto ou relativo ao working dir do BFF. */
    private String scriptPath;

    /** Diretorio de trabalho do processo Node (a pasta automation/). */
    private String workingDir;

    public String getNodeExecutable() {
        return nodeExecutable;
    }

    public void setNodeExecutable(String nodeExecutable) {
        this.nodeExecutable = nodeExecutable;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }
}
