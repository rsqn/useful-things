package tech.rsqn.useful.things.ledger;

/**
 * Interface for ledger subscribers.
 */
public interface LedgerSubscriber {
    /**
     * Called when a new event is written to the ledger.
     *
     * @param event The event written.
     */
    void onEvent(BaseEvent event);
}
