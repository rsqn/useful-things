package tech.rsqn.useful.things.ledger;

/**
 * Interface for ledger subscribers.
 */
public interface LedgerSubscriber {
    /**
     * Called when a new record is written to the ledger.
     *
     * @param record The record written.
     */
    void onRecord(Record record);
}
