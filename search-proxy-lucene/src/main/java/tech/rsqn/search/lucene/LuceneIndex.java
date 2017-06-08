package tech.rsqn.search.reference;

import tech.rsqn.search.proxy.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This is not thread safe as this is only a reference implementation to be used prior to lucene.
 * <p>
 * The functionality in here is not meant to provide a real search - just act as a placeholder for unit tests, or while deciding on an implementation
 */
public class LuceneIndex implements Index {

    private List<IndexEntry> data;

    public LuceneIndex() {
        data = new ArrayList<>();
    }

    @Override
    public void submit(IndexEntry entry) {
        data.add(entry);
    }

    @Override
    public SearchResult search(String s, int max) {
        SearchResult ret = new SearchResult();


        s = s.toLowerCase();
        for (IndexEntry indexEntry : data) {
            for (String k : indexEntry.getAttrs().keySet()) {
                String v = indexEntry.getAttrs().get(k);
                if (v.toLowerCase().contains(s)) {
                    ret.addMatch(new SearchResultItem().with(indexEntry, v.length()));
                }
            }
        }

        ret.normalize();

        return ret;
    }

    @Override
    public void optimize() {

    }

    @Override
    public SearchResult search(SearchQuery query) {

        SearchResult ret = new SearchResult();

        for (IndexEntry indexEntry : data) {
            for (SearchAttribute searchAttr : query.getAttributes()) {
                for (String entryAttrKey : indexEntry.getAttrs().keySet()) {
                    if ("*".equals(searchAttr.getName()) || searchAttr.getName().equals(entryAttrKey)) {
                        String entryAttrVal = indexEntry.getAttrs().get(entryAttrKey);
                        if (entryAttrVal.toLowerCase().contains(searchAttr.getPattern())) {
                            double yoloScore = searchAttr.getPattern().length();
                            ret.addMatch(new SearchResultItem().with(indexEntry, yoloScore));
                        }
                    }
                }
            }
        }

        ret.normalize();
        return ret;

    }
}
