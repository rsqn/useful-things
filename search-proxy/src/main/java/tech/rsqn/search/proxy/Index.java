package tech.rsqn.search.proxy;

/**
 * Created by mandrewes on 5/6/17.
 *
 */
public interface Index {

    /**
     * Submit a single new item the index - outside batchUpdates
     * @param entry
     */
    void submitSingleEntry(IndexEntry entry);

    /**
     * Submit an item to the index, within begin and end update
     * @param entry
     */
    void submitBatchEntry(IndexEntry entry);

    /**
     * Lets the index know that submissions are coming.
     * An implementation may or may not become unavailable until endUpdate is called - depending on the implementation
     */
    void beginBatch();

    /**
     * Lets the index know it may close any writers ( if applicable ), and may perform optimisations and bring the index back online
     */
    void endBatch();

    SearchResult search(String s, int max);

    SearchResult search(SearchQuery query);

    IndexMetrics fetchMetrics();

    void clearIndex();

}
