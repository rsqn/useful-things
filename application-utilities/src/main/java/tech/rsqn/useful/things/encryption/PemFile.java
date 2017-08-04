package tech.rsqn.useful.things.encryption;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;

public class PemFile {

    private PemObject pemObject;

    public PemFile() throws Exception {

    }

    public PemFile with(String filename) throws Exception {
        ClassPathResource cpr = new ClassPathResource(filename);
        InputStreamReader reader = null;

        if (cpr.exists()) {
            reader = new InputStreamReader(cpr.getInputStream());
        } else {
            File f = new File(filename);
            if (f.exists()) {
                reader = new FileReader(f);
            }
        }
        if (reader == null) {
            throw new Exception("File " + filename + " not found in classpath or as file");
        }

        PemReader pemReader = new PemReader(reader);
        try {
            this.pemObject = pemReader.readPemObject();
        } finally {
            pemReader.close();
        }
        return this;
    }

    public PemObject getPemObject() {
        return pemObject;
    }
}