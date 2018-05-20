package tech.rsqn.useful.things.encryption;


import org.bouncycastle.util.encoders.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.util.Random;


public class AESEncryptionTool implements EncryptionTool {

    private String charSet = "utf-8";
    private byte[] key;
    private int blockSize = 16;
    private String alorithm = "AES/CBC/PKCS5Padding";
    private String provider = "BC";

    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }

    public AESEncryptionTool() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public void setKey(byte[] keyBytes) {
        key = keyBytes;
    }

    private byte[] getRandomBytes(int n) {
        byte[] ret = new byte[n];
        new Random().nextBytes(ret);
        return ret;
    }

    public byte[] encrypt(byte[] plainText) {
        try {
            Cipher enCipher = Cipher.getInstance(alorithm, provider);
            byte[] iv = getRandomBytes(blockSize);

            enCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

            byte[] encrypted = enCipher.doFinal(plainText);

            byte[] ret = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, ret, 0, iv.length);
            System.arraycopy(encrypted, 0, ret, iv.length, encrypted.length);
            return ret;
        } catch (Exception e) {
            throw new RuntimeException("Encryption exception " + e, e);
        }
    }


    public byte[] decrypt(byte[] cryptText) {
        try {
            Cipher deCipher = Cipher.getInstance(alorithm, provider);
            byte[] iv = new byte[blockSize];
            byte[] decData = new byte[cryptText.length - blockSize];

            System.arraycopy(cryptText, 0, iv, 0, iv.length);
            System.arraycopy(cryptText, iv.length, decData, 0, decData.length);

            deCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return deCipher.doFinal(decData);
        } catch (Exception e) {
            throw new RuntimeException("Encryption exception " + e, e);
        }
    }

    public String encode(String plainText) {
        try {

            byte[] encData = encrypt(plainText.getBytes(charSet));
            String encString = Base64.toBase64String(encData);
            return encString;
        } catch (Exception e) {
            throw new RuntimeException("Encryption exception " + e, e);
        }
    }

    public String decode(String encodedText) {
        try {
            byte[] de = Base64.decode(encodedText);
            byte[] decData = decrypt(de);
            return new String(decData, charSet);
        } catch (Exception e) {
            throw new RuntimeException("Decryption exception " + e, e);
        }
    }
}
