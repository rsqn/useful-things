package tech.rsqn.search.lucene;

import com.beust.jcommander.internal.Lists;
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

        index.setWildCardFields(Lists.newArrayList("name","desc","ident"));
    }

    private void populateIndex() {
        index.beginBatch();

        IndexEntry entry = new IndexEntry();
        entry.setReference("1");
        entry.putAttr("name", "bob the dogs");
        entry.putAttr("desc", "bob is a very big dog that eats food");
        entry.putAttr("ident", "1234");
        index.submitBatchEntry(entry);

        entry = new IndexEntry();
        entry.setReference("2");
        entry.putAttr("name", "dog the bog");
        entry.putAttr("desc", "this is a place where dogs velocity is reduced via viscosity");
        entry.putAttr("ident", "6789");
        index.submitBatchEntry(entry);

        entry = new IndexEntry();
        entry.setReference("3");
        entry.putAttr("name", "nut butter");
        entry.putAttr("desc", "a delicious substance, sometimes liked by dogs");
        entry.putAttr("ident", "1011");
        index.submitBatchEntry(entry);

        index.endBatch();
    }

    @Test
    public void shouldFindByIdentField() throws Exception {
       populateIndex();
        SearchQuery query = new SearchQuery()
                .limit(10)
                .and("ident","6789");
        SearchResult result = index.search(query);
        Assert.assertEquals(result.getMatches().size(), 1);
        Assert.assertEquals(result.getMatches().get(0).getIndexEntry().getReference(), "2");
    }


    @Test
    public void shouldFindByNameAndIdent() throws Exception {
        populateIndex();
        SearchQuery query = new SearchQuery()
                .limit(10)
                .and("name","nut butter")
                .and("ident","6789");

        SearchResult result = index.search(query);
        Assert.assertEquals(result.getMatches().size(), 2);
        Assert.assertEquals(result.getMatches().get(0).getIndexEntry().getReference(), "3");

    }

    @Test
    public void shouldFindUsingPartialDescriptionField() throws Exception {
        populateIndex();
        SearchQuery query = new SearchQuery()
                .limit(10)
                .and("desc","velocity via viscosity");

        SearchResult result = index.search(query);
        Assert.assertEquals(result.getMatches().size(), 1);
        Assert.assertEquals(result.getMatches().get(0).getIndexEntry().getReference(), "2");
    }

    @Test
    public void shouldFindUsingSinglePartOfDescriptionField() throws Exception {
        populateIndex();
        SearchQuery query = new SearchQuery()
                .limit(10)
                .and("desc","velocity");

        SearchResult result = index.search(query);
        Assert.assertEquals(result.getMatches().size(), 1);
        Assert.assertEquals(result.getMatches().get(0).getIndexEntry().getReference(), "2");
    }


    @Test
    public void shouldFindUsingPartialNameField() throws Exception {
        populateIndex();
        SearchQuery query = new SearchQuery()
                .limit(10)
                .and("name","nut b");

        SearchResult result = index.search(query);
        Assert.assertEquals(result.getMatches().size(), 1);
        Assert.assertEquals(result.getMatches().get(0).getIndexEntry().getReference(), "3");

    }

    @Test
    public void shouldFindInAnyConfigured() throws Exception {
        populateIndex();
        SearchQuery query = new SearchQuery()
                .limit(10)
                .with("*","dog");

        SearchResult result = index.search(query);
        Assert.assertEquals(result.getMatches().size(), 3);

    }
}

