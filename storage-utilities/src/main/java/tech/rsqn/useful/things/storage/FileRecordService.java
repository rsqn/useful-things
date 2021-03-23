package tech.rsqn.useful.things.storage;


public interface FileRecordService {

    FileHandle createNew(String name, String mimeType);

    boolean exists(String uid);

    boolean exists(String path, String uid);

    FileHandle getByUid(String uid);

    FileHandle getByUidAndPath(String path, String uid);

    void getAll(FileIterator fileIterator);

    void getAllByPath(String path, FileIterator fileIterator);

    void copy(String fromUid, String toUid);

    void copyTo(String fromUid, FileRecordService toSrv, String toUid);

}
