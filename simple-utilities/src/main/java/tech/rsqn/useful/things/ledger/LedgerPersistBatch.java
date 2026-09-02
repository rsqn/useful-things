package tech.rsqn.useful.things.ledger;

/**
 * Shared persistence batch sizing for write-behind ledgers.
 * <p>
 * Single-record {@link Ledger#write} calls are coalesced on the writer thread
 * into {@link PersistenceDriver#writeBatch} appends up to this size. Plain JSONL
 * row order is preserved.
 */
final class LedgerPersistBatch {

    /** System property override for {@link #DEFAULT_BATCH_SIZE}. */
    static final String BATCH_SIZE_PROP = "tech.rsqn.useful.things.ledger.persist-batch-size";

    /** Default coalesce size (plan 188 C2 knee for write-behind paths). */
    static final int DEFAULT_BATCH_SIZE = 128;

    private LedgerPersistBatch() {
    }

    static int resolveBatchSize() {
        int value = Integer.getInteger(BATCH_SIZE_PROP, DEFAULT_BATCH_SIZE);
        if (value <= 0) {
            throw new IllegalArgumentException(BATCH_SIZE_PROP + " must be positive, got " + value);
        }
        return value;
    }
}
