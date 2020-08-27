package tech.rsqn.useful.things.encryption;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;

public class AESEncryptionToolTest {

    String creditCardJSON = "{         \"pan\": \"5111111111111111\",\n" +
        "        \"expirationDate\": \"20210101\",\n" +
        "        \"name\": \"Colin Nevin\",\n" +
        "        \"title\": \"Mr\",\n" +
        "        \"firstName\": \"Colin\",\n" +
        "        \"middleName\": \"\",\n" +
        "        \"lastName\": \"Nevin\"}";

    @Test
    public void shouldEncryptAndDecrypt() throws Exception {

        AESEncryptionTool encryptionTool = new AESEncryptionTool();
        encryptionTool.setKey(generate());

        String input = "hello world";
        String encData = encryptionTool.encode(input);

        String output = encryptionTool.decode(encData);
        Assert.assertEquals(input, output);
    }

    @Test
    public void shouldEncryptDecryptDecryptedCreditCardJson() throws Exception {

        AESEncryptionTool encryptionTool = new AESEncryptionTool();
        encryptionTool.setKey(generate());

        String message = creditCardJSON;

        String encData = encryptionTool.encode(message);

        String output = encryptionTool.decode(encData);
        Assert.assertEquals(message, output);
    }

    public static byte[] generate() {

        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey secretKey = keyGen.generateKey();
            return secretKey.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("aes encryption error", e);
        }
    }
}
