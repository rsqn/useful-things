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
public class InMemoryIndex implements Index {

    private List<IndexEntry> data;

    public InMemoryIndex() {
        data = new ArrayList<>();
    }

    @Override
    public void clearIndex() {
        data.clear();
    }

    @Override
    public void submitSingleEntry(IndexEntry entry) {
        data.add(entry);
    }

    @Override
    public void submitBatchEntry(IndexEntry entry) {
        submitSingleEntry(entry);
    }

    @Override
    public IndexMetrics fetchMetrics() {
        IndexMetrics ret = new IndexMetrics();

        ret.put("size", data.size());
        return ret;
    }

    @Override
    public void beginBatch() {

    }

    @Override
    public void endBatch() {

    }

    @Override
    public SearchResult search(String s, int max) {
        SearchResult ret = new SearchResult();


        s = s.toLowerCase();
        for (IndexEntry indexEntry : data) {
            for (String k : indexEntry.getAttrs().keySet()) {
                IndexAttribute attr = indexEntry.getAttrs().get(k);

                if (Attribute.Type.String == attr.getAttrType()) {
                    String v = attr.getAttrValueAs(String.class);

                    if (v.toLowerCase().contains(s)) {
                        ret.addMatch(new SearchResultItem().with(indexEntry, v.length()));
                    }
                }
            }
        }

        ret.normalize();

        return ret;
    }

    @Override
    public SearchResult search(SearchQuery query) {

        SearchResult ret = new SearchResult();

        for (IndexEntry indexEntry : data) {
            for (SearchAttribute searchAttr : query.getAttributes()) {
                for (String entryAttrKey : indexEntry.getAttrs().keySet()) {
                    if ("*".equals(searchAttr.getName()) || searchAttr.getName().equals(entryAttrKey)) {
                        IndexAttribute entryAttrVal = indexEntry.getAttrs().get(entryAttrKey);
                        if (Attribute.Type.String == entryAttrVal.getAttrType() || Attribute.Type.Text == entryAttrVal.getAttrType()) {
                            String s = entryAttrVal.getAttrValueAs(String.class);

                            if (s.toLowerCase().contains(searchAttr.getPattern())) {
                                double yoloScore = s.length();
                                ret.addMatch(new SearchResultItem().with(indexEntry, yoloScore));
                            }
                        }
                    }
                }
            }
        }

        ret.normalize();
        return ret;

    }
}
