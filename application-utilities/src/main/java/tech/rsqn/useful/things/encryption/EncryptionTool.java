package tech.rsqn.useful.things.encryption;

public interface EncryptionTool {

    String getAlias();

    void setCharSet(String charSet);
//    void setKey(byte[] keyBytes);

    byte[] encrypt(byte[] plainText);

    byte[] decrypt(byte[] cryptText);

    String encode(String plainText);

    String decode(String encodedText);


}