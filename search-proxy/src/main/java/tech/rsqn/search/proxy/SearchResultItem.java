package tech.rsqn.search.proxy;

/**
 * Created by mandrewes on 8/6/17.
 */
public class SearchResultItem {

    private double score;
    private IndexEntry indexEntry;

    public SearchResultItem with(IndexEntry entry, double score) {
        this.setIndexEntry(entry);
        this.setScore(score);
        return this;
    }

    public SearchResultItem merge(SearchResultItem src) {
        this.score += src.getScore();
        return this;
    }

    public IndexEntry getIndexEntry() {
        return indexEntry;
    }

    public void setIndexEntry(IndexEntry indexEntry) {
        this.indexEntry = indexEntry;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
