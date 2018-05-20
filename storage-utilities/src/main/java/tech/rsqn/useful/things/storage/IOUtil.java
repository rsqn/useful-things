package tech.rsqn.useful.things.storage;


import com.amazonaws.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

public class IOUtil {

    public static String readResourceToString(String name) {
        try {
            URL url = IOUtil.class.getResource(name);
            if ( url != null) {
                return IOUtil.readToString(url.openStream());
            } else {
                throw new RuntimeException("Resource not found  " + name);
            }
        } catch (IOException e) {
            throw new RuntimeException("Exception reading stream " + e, e);
        }
    }

    public static byte[] readResourceToByteArray(String name) {
        try {
            URL url = IOUtil.class.getResource(name);
            if ( url != null) {
                return IOUtils.toByteArray(url.openStream());
            } else {
                throw new RuntimeException("Resource not found  " + name);
            }
        } catch (IOException e) {
            throw new RuntimeException("Exception reading stream " + e, e);
        }
    }

    public static String readToString(InputStream is) {
        try {
            return IOUtils.toString(is);
        } catch (IOException e) {
            throw new RuntimeException("Exception reading stream " + e, e);
        }
    }

    public static void doDelete(File f) {
        try {
            if (f != null && f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
        }
    }

    public static void doClose(InputStream is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (Exception e) {
        }
    }

    public static void doClose(OutputStream os) {
        try {
            if (os != null) {
                os.close();
            }
        } catch (Exception e) {
        }
    }

    public static void zeroBuffer(byte[] buff) {
        for (int i = 0; i < buff.length; i++) {
            buff[i] = 0;
        }
    }
}
