package tech.rsqn.useful.things.encryption;

/**
 * Created by joshcaspersz on 27/01/2016.
 */
public interface EncryptionTool {

    byte[] encrypt(byte[] plainText);

    byte[] decrypt(byte[] cryptText);

    String encode(String plainText);

    String decode(String encodedText);

    void setCharSet(String charSet);
}
