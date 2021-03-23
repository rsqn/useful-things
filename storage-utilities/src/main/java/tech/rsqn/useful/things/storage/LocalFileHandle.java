package tech.rsqn.useful.things.storage;

import org.apache.commons.io.IOUtil;

import java.io.*;
import java.nio.file.Files;
import java.util.UUID;

public class LocalFileHandle extends FileHandle implements Serializable {
    private static final long serialVersionUID = 8612614067570182761L;
    private transient File file;

    public static LocalFileHandle with (File f) {
        LocalFileHandle ret = new LocalFileHandle();

        ret.setName(f.getName());
        ret.setLength(f.length());
        ret.setUid(UUID.randomUUID().toString());

        String ext = f.getName();
        int i = ext.lastIndexOf(".");
        if (i > 0) {
            ext = ext.substring(i + 1).toLowerCase();
        } else {
            ext = "";
        }

        try {
            ret.setMimeType(Files.probeContentType(f.toPath()));
        } catch (IOException e) {
            ret.setMimeType("application/octet-stream");
        }
        ret.file = f;
        return ret;
    }


    @Override
    public void delete() {
        file.delete();
    }

    @Override
    public long streamIn(InputStream is) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            FileUtil.copy(is, fos);
        } catch (Exception e) {
            throw new RuntimeException("Unable to write data " + e, e);
        }
        return getLength();
    }

    @Override
    public long streamOut(OutputStream os) {
        try (FileInputStream fis = new FileInputStream(file)) {
            FileUtil.copy(fis, os);
            return getLength();
        } catch (Exception e) {
            throw new RuntimeException("Unable to read or stream out data " + e, e);
        }
    }

    public int write(byte[] buff, long ptr, int offset, int len) {
        throw new UnsupportedOperationException("no soup for you");
    }

    public int read(byte[] buff, long ptr, int offset, int len) {
        throw new UnsupportedOperationException("no soup for you");
    }

    public void close() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public String calculateCheckSum() {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(is);
            return md5;
        } catch (Exception e) {

        } finally {
<<<<<<< HEAD
            try {
                IOUtils.close(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
=======
            IOUtil.shutdownStream(is);
>>>>>>> master
        }
        return "failure" + System.currentTimeMillis();
    }

    @Override
    public InputStream asInputStream() {
        try {
            return new FileInputStream(file);
        }
        catch(Exception ex) {
            throw new RuntimeException(String.format("Local file is imaginary - %s", ex.toString()));
        }
    }
}
