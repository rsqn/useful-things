package tech.rsqn.useful.things.encryption;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import javax.crypto.Cipher;
import java.io.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

public class RSAEncryptionTool implements EncryptionTool, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(RSAEncryptionTool.class);
    private String charSet = "utf-8";
    private String privateKeyFile;
    private String privateKeyPassword;
    private String publicKeyFile;
    private String algorithm = "RSA/NONE/OAEPPadding";
    private String provider = "BC";
    private AmazonS3 s3Client;
    private KeyFactory factory;
    private String keyDir;
    private File _keyDir;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String alias;

    private static final String CLASS_PATH_PREFIX = "classpath://";
    private static final String S3_PATH_PREFIX = "s3://";



    public String getAlias() {
        return alias;
    }

    @Required
    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Required
    public void setKeyDir(String keyDir) {
        this.keyDir = keyDir;
    }

    public void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Required
    public void setPublicKeyFile(String publicKeyFile) {
        this.publicKeyFile = publicKeyFile;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyPassword(String privateKeyPassword) {
        this.privateKeyPassword = privateKeyPassword;
    }

    @Required
    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }


    public RSAEncryptionTool() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        factory = KeyFactory.getInstance("RSA", "BC");
        _keyDir = new File(keyDir);

        log.info("RSAEncryptionTool - Private Key Path " + privateKeyFile);
        log.info("RSAEncryptionTool - Private Key Password Present ? " + StringUtils.isNotEmpty(privateKeyPassword) );
        log.info("RSAEncryptionTool - Public Key Path " + publicKeyFile);
        log.info("RSAEncryptionTool - keyDir " + _keyDir.getAbsolutePath());

        if (!_keyDir.exists()) {
            _keyDir.mkdirs();
        }

        if ( ! StringUtils.isEmpty(privateKeyFile)) {
            privateKey = loadPrivateKey(privateKeyFile,privateKeyPassword);
        }
        publicKey = loadPublicKey(publicKeyFile);
    }

    @Override
    public byte[] encrypt(byte[] plainText) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm, provider);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(plainText);
        } catch (Exception e) {
            throw new RuntimeException("Decryption exception " + e, e);
        }
    }

    @Override
    public byte[] decrypt(byte[] cryptText) {
        if (StringUtils.isEmpty(privateKeyFile)) {
            throw new RuntimeException("Private key not available for decryption");
        }

        try {
            Cipher cipher = Cipher.getInstance(algorithm, provider);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(cryptText);
        } catch (Exception e) {
            throw new RuntimeException("Encryption exception " + e, e);
        }
    }

    @Override
    public String encode(String plainText) {
        try {
            byte[] encData = encrypt(plainText.getBytes(charSet));
            return new String(Base64.encodeBase64(encData), charSet);
        } catch (Exception e) {
            throw new RuntimeException("Encryption exception " + e, e);
        }
    }

    @Override
    public String decode(String encodedText) {
        try {
            byte[] fromHex = Base64.decodeBase64(encodedText);
            byte[] decData = decrypt(fromHex);
            return new String(decData, charSet);
        } catch (Exception e) {
            throw new DecryptException("Decryption exception " + e, e);
        }
    }

    @Override
    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }


    // review - remove the need for a keys dir - keep keys in memory
    private String fetchRemoteIfNecessary(String s) {
        if (s.startsWith("s3")) {
            String[] bucketAndKey = parseBucketKeyAndFileName(s);
            String bucket = bucketAndKey[0];
            String key = bucketAndKey[1];
            String fileName = bucketAndKey[2];

            log.info("KeyTool - fetching " + s);
            S3Object obj = s3Client.getObject(bucket, key);
            log.info("KeyTool - fetched " + s);

            quickState.put(s, "fetched");

            File f = new File(_keyDir, fileName);
            FileOutputStream os = null;
            log.info("KeyTool - output file " + f.getAbsolutePath());

            try {

                S3ObjectInputStream is = obj.getObjectContent();

                log.info("KeyTool - fetching " + s);

                os = new FileOutputStream(f);
                IOUtils.copy(is, os);

                log.info("local path now " + f.getAbsolutePath());
                return f.getAbsolutePath();

            } catch (Exception ex) {
                log.info("KeyTool - failed to write to " + f.getAbsolutePath());
                throw new RuntimeException(ex);
            } finally {
                if (os != null) {
                    IOUtils.closeQuietly(os);
                }
            }
        }

        return s;
    }

    public PrivateKey loadPrivateKey(String fileName, String keyPass) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(provider);
        Reader keyReader = null;
        PrivateKey privateKey = null;

        try {
            if (fileName.startsWith(CLASS_PATH_PREFIX)) {
                // extract the classpath
                String keyResource = fileName.substring(CLASS_PATH_PREFIX.length());
                keyReader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(keyResource));
            } else if ( fileName.startsWith(S3_PATH_PREFIX) ) {
                String fn = fetchRemoteIfNecessary(fileName);
                keyReader = new FileReader(fn);
            } else {
                keyReader = new FileReader(fileName);
            }

            PEMParser pemParser = new PEMParser(keyReader);
            Object key = pemParser.readObject();

            if (key instanceof PEMEncryptedKeyPair) {
                if ( keyPass != null ) {
                    PEMDecryptorProvider decryptionProvider = new JcePEMDecryptorProviderBuilder().build(keyPass.toCharArray());
                    privateKey = converter.getKeyPair(((PEMEncryptedKeyPair) key).decryptKeyPair(decryptionProvider)).getPrivate();
                } else {
                    throw new RuntimeException("Encrypted private key found but no pass provided");
                }
            } else {
                privateKey = converter.getKeyPair((PEMKeyPair) key).getPrivate();
            }
        } finally {
            if (keyReader != null) {
                keyReader.close();
            }
        }

        return privateKey;
    }

    public PublicKey loadPublicKey(String fileName) {
        try {
            fileName = fetchRemoteIfNecessary(fileName);

            PemFile pemFile = new PemFile().with(fileName);
            byte[] content = pemFile.getPemObject().getContent();
            X509EncodedKeySpec spec = new X509EncodedKeySpec(content);
            return factory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> quickState = new HashMap<>();


    // rushing this morning - MA
    private String[] parseBucketKeyAndFileName(String s) {
        String[] ret = new String[3];

        s = s.substring("s3://".length());
        int index = s.indexOf("/");

        String bucket = s.substring(0, index);
        String key = s.substring(index + 1);
        String fn = s.substring(s.lastIndexOf("/") + 1);

        ret[0] = bucket;
        ret[1] = key;
        ret[2] = fn;

        return ret;
    }

}
