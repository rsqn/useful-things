package tech.rsqn.useful.things.storage;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
<<<<<<< HEAD

=======
>>>>>>> master

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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
    private String root;

    public FileSystemFileRecordService(String fsRoot) {
        root = fsRoot;
        if (!root.endsWith("/")) {
            root = String.format("%s/", root);
        }
    }

    public FileSystemFileRecordService() {
        root = System.getProperty("java.io.tmpdir");
    }

    @Override
    public String toString() {
        return String.format("FileSystemFileRecordService({})", root);
    }

    @Override
    public FileHandle createNew(String name, String mimeType) {
        FileSystemFileHandle handle = new FileSystemFileHandle();
        handle.setUid(UUID.randomUUID().toString());
        handle.setName(name);
        handle.setMimeType(mimeType);

        File tldFile = new File(getFullPath("", name));
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

    @Override
    public FileHandle getByUid (String uid) {
        return getByUidAndPath("", uid);
    }

    @Override
    public FileHandle getByUidAndPath(String path, String uid) {
        FileSystemFileHandle handle = new FileSystemFileHandle();
        handle.setUid(uid);
        File tldFile = new File(getFullPath(path, uid));
        handle.setTld(tldFile);
        // generate and load metadata for files that don't exist
        // real file are to be returned as is
        if (!tldFile.exists())
            handle.loadMeta();
        return handle;
    }

    private String getFullPath(String path, String uid) {
        if (path != null) {
            return String.format("%s/%s/%s", root, path, uid);
        } else {
            return String.format("%s/%s", root, uid);
        }
    }

    @Override
    public void getAll(FileIterator fileIterator) {
        getAllByPath("", fileIterator);
    }

    @Override
    public void getAllByPath(String path, FileIterator fileIterator) {
        try {
            List<FileHandle> handles = new ArrayList<FileHandle>();
            Files.walk(Paths.get(getFullPath(path, "")))
                    .filter(Files::isRegularFile)
                    .forEach(i -> handles.add(getByUid(i.toString().replace(root, ""))));

            for (FileHandle h : handles) {
                if (!fileIterator.onfileHandle(h)) {
                    return;
                }
            }
        }
        catch(Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void copy(String fromUid, String toUid)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyTo(String fromUid, FileRecordService toSrv, String toUid)
    {
        throw new UnsupportedOperationException();
    }
}
