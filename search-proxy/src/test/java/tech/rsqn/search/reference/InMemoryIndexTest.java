package tech.rsqn.search.reference;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tech.rsqn.search.proxy.IndexEntry;
import tech.rsqn.search.proxy.SearchQuery;
import tech.rsqn.search.proxy.SearchResult;

/**
 * Created by mandrewes on 8/6/17.
 */
public class InMemoryIndexTest {
    InMemoryIndex index;

    @BeforeMethod
    public void setUp() throws Exception {

        index = new InMemoryIndex();

    }

    @Test
    public void shouldFindSimpleMatchByName() throws Exception {
        IndexEntry entry = new IndexEntry();
        entry.setReference("1");
        entry.putAttr("name", "bob the dogs");
        index.submit(entry);

        entry = new IndexEntry();
        entry.setReference("2");
        entry.putAttr("name", "dog the bog");
        index.submit(entry);

        entry = new IndexEntry();
        entry.setReference("3");
        entry.putAttr("name", "nut butter");
        index.submit(entry);

        SearchResult result = index.search("dog", 50);

        Assert.assertEquals(result.getMatches().size(), 2);
    }

    @Test
    public void shouldFindSimpleMatchByNameAndIdent() throws Exception {
        IndexEntry entry = new IndexEntry();
        entry.setReference("1");
        entry.putAttr("name", "bob the dogs");
        entry.putAttr("ident", "1234");
        index.submit(entry);

        entry = new IndexEntry();
        entry.setReference("2");
        entry.putAttr("name", "dog the bog");
        entry.putAttr("ident", "6789");
        index.submit(entry);

        entry = new IndexEntry();
        entry.setReference("3");
        entry.putAttr("name", "nut butter");
        entry.putAttr("ident", "1011");
        index.submit(entry);


        SearchQuery query = new SearchQuery()
                .limit(10)
                .with("*","dog")
                .and("name","dog")
                .with("ident","6789");

        SearchResult result = index.search(query);

        Assert.assertEquals(result.getMatches().size(), 2);

        Assert.assertEquals(result.getMatches().get(0).getIndexEntry().getReference(), "2");

    }
}

