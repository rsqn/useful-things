package tech.rsqn.useful.things.storage;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 2/16/13
 * Time: 11:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class FileSystemFileRecordService implements FileRecordService {
    private Logger log = LoggerFactory.getLogger(getClass());

    private static final Map<String, String> cache = new Hashtable<String, String>();


    public FileHandle createNew(String name, String mimeType) {
        FileSystemFileHandle handle = new FileSystemFileHandle();
        handle.setUid(UUID.randomUUID().toString());
        handle.setName(name);
        handle.setMimeType(mimeType);

        String tld = System.getProperty("java.io.tmpdir");

        File tldFile = new File(tld);
        if ( ! tldFile.exists()) {
            tldFile.mkdirs();
        }

        handle.setTld(tldFile);
        return handle;
    }

    @Override
    public boolean exists(String uid) {
        try {
            getByUid(uid);
            return true;
        } catch (RuntimeException e){
            return false;
        }
    }

    @Override
    public boolean exists(String path, String uid) {
        try {
            getByUidAndPath(path, uid);
            return true;
        } catch (RuntimeException e){
            return false;
        }
    }

    public FileHandle getByUid (String uid) {
        FileSystemFileHandle handle = new FileSystemFileHandle();
        handle.setUid(uid);
        String tld = System.getProperty("java.io.tmpdir");
        File tldFile = new File(tld);
        handle.setTld(tldFile);
        handle.loadMeta();
        return handle;
    }

    @Override
    public FileHandle getByUidAndPath(String path, String uid) {
        FileSystemFileHandle handle = new FileSystemFileHandle();
        handle.setUid(uid);
        String tld = System.getProperty("java.io.tmpdir");
        File tldFile = new File(tld);
        tldFile = new File(tldFile,path);

        handle.setTld(tldFile);
        handle.loadMeta();
        return handle;
    }

    @Override
    public void getAll(FileIterator fileIterator) {
        throw new NotImplementedException();
    }

    @Override
    public void getAllByPath(String path, FileIterator fileIterator) {
        throw new NotImplementedException();
    }
}
