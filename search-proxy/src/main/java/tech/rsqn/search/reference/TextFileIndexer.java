package tech.rsqn.search.reference;

import tech.rsqn.search.proxy.Index;
import tech.rsqn.search.proxy.IndexEntry;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * Created by mandrewes on 5/6/17.
 */
public class TextFileIndexer {

    private Index index;
    private File textFile;

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    public File getTextFile() {
        return textFile;
    }

    public void setTextFile(File textFile) {
        this.textFile = textFile;
    }

    private List<String> readFile() {
        try {
            List<String> lines = org.apache.commons.io.IOUtils.readLines(new FileReader(textFile));
            return lines;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long performFullIndex() {
        List<String> lines = readFile();

        for (int i = 0; i < lines.size(); i++) {
            IndexEntry entry = new IndexEntry();
            entry.setReference("" + 0);
            entry.addAttr("line", lines.get(i));
            index.submitSingleEntry(entry);
        }

        return lines.size();
    }

    public long performIncrementalIndex(long offset) {
        List<String> lines = readFile();
        long ctr = 0;

        for (long i = offset; i < lines.size(); i++) {
            IndexEntry entry = new IndexEntry();
            entry.setReference("" + 0);
            entry.addAttr("line", lines.get((int)offset));
            index.submitSingleEntry(entry);
            ctr++;
        }

        return ctr;
    }
}
