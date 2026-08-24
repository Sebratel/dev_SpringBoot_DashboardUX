package br.com.sebratel.consolidador.report;

/**
 * Abstrai "como" um job de relatorio e efetivamente executado. Hoje a unica
 * implementacao chama o script Node/Playwright existente em automation/ como
 * subprocesso (NodeProcessReportJobRunner), mas o Controller/Service nao
 * dependem desse detalhe - uma futura reescrita em Java pura implementaria
 * a mesma interface sem tocar no resto do BFF.
 */
public interface ReportJobRunner {

    /**
     * Executa o job de forma SINCRONA nesta thread (quem chama e responsavel
     * por rodar isso numa thread de background). Implementacoes devem
     * chamar job.updateProgress(...) durante a execucao e terminar com
     * job.markDone(...) ou job.markFailed(...).
     */
    void run(ReportJob job);
}
