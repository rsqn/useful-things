package tech.rsqn.useful.things.storage;

import java.io.Serializable;

/**
 * Created by mandrewes on 11/04/16.
 */
public class FileHandleReference implements Serializable {
    private static final long serialVersionUID = -4078098155470705200L;
    private String uid;
    private long length = -1;
    private String mimeType;
    private String name;
    private String resourcePath;
    private String extension;

    public static FileHandleReference with(FileHandle handle) {
        FileHandleReference ret = new FileHandleReference();

        ret.setUid(handle.getUid());
        ret.setLength(handle.getLength());
        ret.setMimeType(handle.getMimeType());
        ret.setName(handle.getName());
        ret.setResourcePath(handle.getResourcePath());
        ret.setExtension(handle.getExtension());

        return ret;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
