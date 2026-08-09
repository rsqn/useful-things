package tech.rsqn.useful.things.ledger;

/**
 * Controls how ledger notification consumer threads wait for work.
 *
 * <ul>
 *   <li><b>SPIN</b> — {@code parkNanos(1µs)} busy-wait. Maximum throughput for backtest/replay
 *       where events arrive back-to-back with no real-time gaps.</li>
 *   <li><b>BLOCK</b> — {@code LinkedBlockingQueue.take()}. Zero idle CPU for live/paper
 *       where events arrive every few seconds.</li>
 * </ul>
 *
 * <p>Must be set via {@link AbstractLedger#setConsumerMode(ConsumerMode)} <b>before</b> the first
 * {@code subscribe()} call. Default is {@code SPIN} (preserving existing behaviour).
 */
public enum ConsumerMode {
    /**
     * Busy-spin with 1µs parkNanos. Near-zero latency, high CPU usage when idle.
     * Ideal for backtest throughput.
     */
    SPIN,

    /**
     * Blocking wait via LinkedBlockingQueue.take(). Zero CPU when idle, instant wake on offer().
     * Ideal for live/paper trading where events are infrequent.
     */
    BLOCK
}
