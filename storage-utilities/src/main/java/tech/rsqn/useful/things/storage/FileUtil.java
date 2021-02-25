package tech.rsqn.useful.things.storage;


import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;

/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 23/02/12
 */
public class FileUtil {


    public static void copy(File input, File output) throws URISyntaxException, IOException {
        copy(new URI("file://" + input.getAbsolutePath()), output);
    }

    public static void copy(URI input, File output)
            throws IOException {
        try {
            InputStream in = null;
            try {
                File f = new File(input);
                if (f.exists())
                    in = new FileInputStream(f);
            } catch (Exception notAFile) {
            }

            File out = output;

            if (in == null) {
                in = input.toURL().openStream();
            }

            copy(in, new FileOutputStream(out));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new IOException("Cannot copy to " + output + " " + e);
        }
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        // if both are file streams, use channel IO
        if ((output instanceof FileOutputStream) && (input instanceof FileInputStream)) {
            try {
                FileChannel target = ((FileOutputStream) output).getChannel();
                FileChannel source = ((FileInputStream) input).getChannel();

                source.transferTo(0, Integer.MAX_VALUE, target);

                source.close();
                target.close();

                return;
            } catch (Exception e) {
            }
        }

        byte[] buf = new byte[8192];
        while (true) {
            int length = input.read(buf);
            if (length < 0)
                break;
            output.write(buf, 0, length);
        }

        try {
            input.close();
        } catch (IOException ignore) {
        }
        try {
            output.close();
        } catch (IOException ignore) {
        }
    }

    public static String readFileToString(File f) {
        try {
            return FileUtils.fileRead(f.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("IOException " + e, e);
        }
    }

    public static void writeStringToFile(File f, String data) {
        try {
            FileUtils.fileWrite(f.getAbsolutePath(),data);
        } catch (Exception e) {
            throw new RuntimeException("IOException " + e, e);
        }
    }
}
