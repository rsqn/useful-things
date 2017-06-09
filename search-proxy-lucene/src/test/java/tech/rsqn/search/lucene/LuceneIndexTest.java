package tech.rsqn.search.lucene;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tech.rsqn.search.proxy.IndexEntry;
import tech.rsqn.search.proxy.SearchQuery;
import tech.rsqn.search.proxy.SearchResult;

import java.io.File;

/**
 * Created by mandrewes on 8/6/17.
 */
public class LuceneIndexTest {
    LuceneIndex index;

    @BeforeMethod
    public void setUp() throws Exception {
        index = new LuceneIndex();
        File f = new File("/tmp/index-test");
        f.mkdirs();
        index.setIndexPath(f.getAbsolutePath());
        index.setCreateOnly(true);
    }

    @Test
    public void shouldFindSimpleMatchByNameAndIdent() throws Exception {

        index.beginBatch();

        IndexEntry entry = new IndexEntry();
        entry.setReference("1");
        entry.putAttr("name", "bob the dogs");
        entry.putAttr("ident", "1234");
        index.submitBatchEntry(entry);

        entry = new IndexEntry();
        entry.setReference("2");
        entry.putAttr("name", "dog the bog");
        entry.putAttr("ident", "6789");
        index.submitBatchEntry(entry);

        entry = new IndexEntry();
        entry.setReference("3");
        entry.putAttr("name", "nut butter");
        entry.putAttr("ident", "1011");
        index.submitBatchEntry(entry);

        index.endBatch();

        SearchQuery query = new SearchQuery()
                .limit(10)
//                .with("*","dog")
                .and("name","nut butter")
                .and("name","dog the bog")
                .and("ident","1234")
                .and("ident","6789");

        SearchResult result = index.search(query);

        Assert.assertEquals(result.getMatches().size(), 2);

//        Assert.assertEquals(result.getMatches().get(0).getIndexEntry().getReference(), "2");

    }
}

