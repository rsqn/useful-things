package tech.rsqn.useful.things.encryption;

import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.util.encoders.Base64;

import javax.crypto.Cipher;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;


public class RSAEncryptionTool implements EncryptionTool {

    private String charSet = "utf-8";
    private String keyPassword;
    private String keyFile;
    private String algorithm = "RSA/NONE/OAEPPadding";
    private String provider = "BC";

    private static final String CLASS_PATH_PREFIX = "classpath:";

    public String getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public RSAEncryptionTool() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    @Override
    public byte[] encrypt(byte[] plainText) {
        try {
            PublicKey publicKey = extractPublicKeyFromPrivateKeyFile();
            Cipher cipher = Cipher.getInstance(algorithm, provider);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(plainText);
        } catch(Exception e) {
            throw new RuntimeException("Decryption exception " + e, e);
        }
    }

    @Override
    public byte[] decrypt(byte[] cryptText) {
        try {
            PrivateKey privateKey = readPrivateKeyFile();
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
            return new String(Base64.toBase64String(encData));
        } catch (Exception e) {
            throw new RuntimeException("Encryption exception " + e, e);
        }
    }

    @Override
    public String decode(String encodedText) {
        try {
            byte[] encData = Base64.decode(encodedText);
            byte[] decData = decrypt(encData);
            return new String(decData, charSet);
        } catch (Exception e) {
            throw new RuntimeException("Decryption exception " + e, e);
        }
    }

    @Override
    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }


    private PublicKey extractPublicKeyFromPrivateKeyFile() throws Exception {
        PrivateKey privateKey = readPrivateKeyFile();
        RSAPrivateCrtKey rsaPrivateCrtKey = (RSAPrivateCrtKey) privateKey;
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(rsaPrivateCrtKey.getModulus(), rsaPrivateCrtKey.getPublicExponent());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(publicKeySpec);
    }

    private PrivateKey readPrivateKeyFile() throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(provider);
        Reader keyReader = null;
        PrivateKey privateKey = null;

        try {
            if (keyFile.startsWith(CLASS_PATH_PREFIX)) {
                String keyResource = keyFile.substring(CLASS_PATH_PREFIX.length());
                keyReader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(keyResource));
            } else {
                keyReader = new FileReader(keyFile);
            }

            PEMParser pemParser = new PEMParser(keyReader);
            Object key = pemParser.readObject();
            if (key instanceof PEMEncryptedKeyPair) {
                PEMDecryptorProvider decryptionProvider = new JcePEMDecryptorProviderBuilder().build(keyPassword.toCharArray());
                privateKey = converter.getKeyPair(((PEMEncryptedKeyPair) key).decryptKeyPair(decryptionProvider)).getPrivate();
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

}
