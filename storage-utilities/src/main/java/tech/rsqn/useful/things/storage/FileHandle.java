package tech.rsqn.useful.things.storage;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class FileHandle {
    private String uid;
    private long length = -1;
    private String mimeType;
    private String name;
    private String resourcePath;
    private String extension;

    public FileHandle() {
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
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

    public void delete() {
        throw new UnsupportedOperationException("This class must be extended");
    }

    public Map<String, String> getMeta() {
        throw new UnsupportedOperationException("This class must be extended");
    }

    public long streamIn( InputStream is ) {
        throw new UnsupportedOperationException("This class must be extended");
    }

    public long streamOut( OutputStream os ) {
        throw new UnsupportedOperationException("This class must be extended");
    }

    @Override
    public String toString() {
        return "FileHandle{" +
                "uid='" + uid + '\'' +
                ", length=" + length +
                ", mimeType='" + mimeType + '\'' +
                ", name='" + name + '\'' +
                ", resourcePath='" + resourcePath + '\'' +
                ", extension='" + extension + '\'' +
                '}';
    }
}
