package io.whyjvm.playground.database;

/**
 * Balanco de uma rodada de pressao sobre o pool de conexoes.
 *
 * @param attempted  quantas queries paralelas foram disparadas
 * @param succeeded  quantas conseguiram conexao e terminaram
 * @param failed     quantas nao conseguiram conexao a tempo
 * @param firstError mensagem da primeira falha (tipicamente timeout de conexao)
 */
public record PoolPressureReport(int attempted, int succeeded, int failed, String firstError) {
}
