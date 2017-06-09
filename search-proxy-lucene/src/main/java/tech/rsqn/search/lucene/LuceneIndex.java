package tech.rsqn.search.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.rsqn.search.proxy.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This only has string support today - that is all thats needed today.
 * <p>
 * This implementation is not thread safe
 */
public class LuceneIndex implements Index {
    public static final String ID_FIELD = "id";
    public static final String REFERENCE_FIELD = "reference";
    private static Logger log = LoggerFactory.getLogger(LuceneIndex.class);

    private String indexPath;
    private boolean createOnly;

    private AtomicBoolean withinBatch;
    private Directory indexDir;
    private Analyzer analyzer;
    private IndexWriterConfig iwc;
    private IndexWriter writer;

    /**
     *
     */
    public LuceneIndex() {
        withinBatch = new AtomicBoolean(false);
    }

    public void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }

    public void setCreateOnly(boolean createOnly) {
        this.createOnly = createOnly;
    }

    private void mayRead() {
        if (withinBatch.get()) {
            throw new RuntimeException("Index is currently being updated in batch mode");
        }
    }

    private void mayWrite() {
        if (!withinBatch.get()) {
            throw new RuntimeException("This index requires batch mode to be active for writes-batch entry has not been started");
        }
    }

    @Override
    public synchronized void submitSingleEntry(IndexEntry entry) {
        this.beginBatch();
        this.submitBatchEntry(entry);
        this.endBatch();
    }

    @Override
    public void submitBatchEntry(IndexEntry entry) {

        mayWrite();

        Document doc = new Document();
        Field f;

        //todo - support reference and id fields as data in IndexEntry
        f = new StringField(ID_FIELD, entry.getUid(), Field.Store.YES);
        doc.add(f);
        f = new StringField(REFERENCE_FIELD, entry.getReference(), Field.Store.YES);
        doc.add(f);

        for (String keyField : entry.getAttrs().keySet()) {
//            f = new StringField(keyField, entry.getAttrs().get(keyField), Field.Store.NO);
            f = new StringField(keyField, entry.getAttrs().get(keyField), Field.Store.YES);
            doc.add(f);
        }

        // todo - do this when IndexEntr supports numbers
//        doc.add(new LongPoint("modified", lastModified));
        // todo - and do this for that path - when IndexEntry supports types
//        doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));

        try {
            if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                writer.addDocument(doc);
                log.debug("added " + entry);

            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                writer.updateDocument(new Term("reference", entry.getReference()), doc);
                log.debug("updated " + entry);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void beginBatch() {
        if (withinBatch.get() == true) {
            throw new RuntimeException("already within batch update");
        }

        log.info("beginBatch");
        try {
            indexDir = FSDirectory.open(Paths.get(indexPath));
            analyzer = new StandardAnalyzer();
            iwc = new IndexWriterConfig(analyzer);

            if (createOnly) {
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            } else {
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            }

            iwc.setRAMBufferSizeMB(64);

            writer = new IndexWriter(indexDir, iwc);

            withinBatch.set(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void endBatch() {
        log.info("endBatch");
        if (withinBatch.get() == false) {
            throw new RuntimeException("no batch update in progress");
        }
        try {
            withinBatch.set(false);
            writer.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    @Override
    public SearchResult search(String s, int max) {
        SearchQuery q = new SearchQuery().with("*", s).limit(max);
        return search(q);
    }

    private IndexEntry docToIndexEntry(ScoreDoc hit, Document doc) {
        IndexEntry ret = new IndexEntry();
        ret.setUid(doc.get(ID_FIELD));
        ret.setReference(doc.get(REFERENCE_FIELD));

        for (IndexableField indexableField : doc.getFields()) {
            ret.putAttr(indexableField.name(), indexableField.stringValue());
        }
        return ret;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        mayRead();

        SearchResult ret = new SearchResult();

        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
            IndexSearcher searcher = new IndexSearcher(reader);

            Query luceneQuery = null;

            BooleanQuery.Builder builder = (new BooleanQuery.Builder());
            for (SearchAttribute searchAttribute : query.getAttributes()) {
                Query _q = new FuzzyQuery(new Term(searchAttribute.getName(), searchAttribute.getPattern()));
                builder.add(_q, BooleanClause.Occur.SHOULD);
                luceneQuery = _q;
                break;
            }
//            luceneQuery = builder.build();

//            luceneQuery = new FuzzyQuery(new Term("name", "nut butt"));

            TopDocs results = searcher.search(luceneQuery, query.getLimit()*5);
            ScoreDoc[] hits = results.scoreDocs;
            int numTotalHits = results.totalHits;
            log.debug(numTotalHits + " total matching documents");

            for (ScoreDoc hit : hits) {
                log.debug("doc=" + hit.doc + " score=" + hit.score);
                Document doc = searcher.doc(hit.doc);
                String id = doc.get(ID_FIELD);
                log.debug("doc=" + hit.doc + " score=" + hit.score + " id=" + id);
                SearchResultItem item = new SearchResultItem().with(docToIndexEntry(hit, doc), hit.score);
                ret.addMatch(item);
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
//        ret.normalize();
        return ret;
    }
}
