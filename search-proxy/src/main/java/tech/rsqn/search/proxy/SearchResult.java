package tech.rsqn.search.proxy;

import java.util.*;

/**
 * Created by mandrewes on 8/6/17.
 */
public class SearchResult {

    private List<SearchResultItem> matches;
    private String lastKey;
    private double sumScore = 0;
    private double matchCount = 0;

    public SearchResult() {
        matches = new ArrayList<>();
    }

    public void normalize() {
        // combine
        Map<String,SearchResultItem> combineMap = new HashMap<>();
        String key;

        for (SearchResultItem match : matches) {
            key = match.getIndexEntry().getUid();
            SearchResultItem existing = combineMap.get(key);
            if ( existing == null ) {
                combineMap.put(key,match);
            } else {
                existing.merge(match);
            }
        }

        matches.clear();
        matches.addAll(combineMap.values());

        // normalize
        for (SearchResultItem match : matches) {
            sumScore += match.getScore();
            matchCount++;
        }
        if (matchCount == 0) {
            return;
        }
        double divisor = 1.0d/ (sumScore / matchCount);  // this math is not correct - but its fit for purpose
        double d;

        for (SearchResultItem match : matches) {
            d = match.getScore();
            match.setScore(d * divisor);
        }

        // order
        matches.sort(new Comparator<SearchResultItem>() {
            @Override
            public int compare(SearchResultItem o1, SearchResultItem o2) {
                return new Double(o2.getScore()).compareTo(o1.getScore());
            }
        });
    }

    public void addMatch(SearchResultItem item) {
        matches.add(item);
    }

    public List<SearchResultItem> getMatches() {
        return matches;
    }

    public void setMatches(List<SearchResultItem> matches) {
        this.matches = matches;
    }

    public String getLastKey() {
        return lastKey;
    }

    public void setLastKey(String lastKey) {
        this.lastKey = lastKey;
    }
}
