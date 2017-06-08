package tech.rsqn.search.reference;

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
public class TextFileIndexerTest {
    InMemoryIndex index;

    TextFileIndexer indexer;

    @BeforeMethod
    public void setUp() throws Exception {

        index = new InMemoryIndex();
        indexer = new TextFileIndexer();

        File f = new File("src/test/resources/text-file.txt");

        System.out.println(f.getAbsolutePath());
        indexer.setIndex(index);
        indexer.setTextFile(f);
    }

    @Test
    public void shouldPerformFullIndex() throws Exception {
        long count = indexer.performFullIndex();

        System.out.printf("Indexed " + count +  " lines");

        SearchResult result = index.search("Version", 50);

        Assert.assertEquals(result.getMatches().size(), 37);
    }

}

