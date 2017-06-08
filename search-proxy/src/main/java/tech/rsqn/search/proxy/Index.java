package tech.rsqn.search.proxy;

/**
 * Created by mandrewes on 5/6/17.
 */
public interface Index {

    void submit(IndexEntry entry);

    void optimize();

    SearchResult search(String s, int max);

    SearchResult search(SearchQuery query);

}
