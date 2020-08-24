import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelperImpl;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;

public class KMSCMKClientHelperTest {

    //private String keyArnTest = "arn:aws:kms:ap-southeast-2:258568400917:key/d6851a96-2eb2-4d77-9542-4e6a4f8850b4";

    private KMSCMKClientHelperImpl kmsCmkClientHelper;

    @BeforeClass
    public void setUp() {
        //kmsCmkClientHelper = new KMSCMKClientHelperImpl(keyArnTest);
        kmsCmkClientHelper = Mockito.mock(KMSCMKClientHelperImpl.class);

        when(kmsCmkClientHelper.getCharset()).thenReturn(StandardCharsets.UTF_8);
        byte[] encValue = "[B@68b58644".getBytes(kmsCmkClientHelper.getCharset());
        byte[] value = "butter".getBytes(kmsCmkClientHelper.getCharset());
        when(kmsCmkClientHelper.encrypt("butter".getBytes(kmsCmkClientHelper.getCharset())))
            .thenReturn(encValue);
        when(kmsCmkClientHelper.decrypt("[B@68b58644".getBytes(kmsCmkClientHelper.getCharset())))
            .thenReturn(value);

        when(kmsCmkClientHelper.encode("butter".getBytes(kmsCmkClientHelper.getCharset())))
            .thenReturn("YnV0dGVy");

        byte[] encodedValue = "butter".getBytes(kmsCmkClientHelper.getCharset());
        when(kmsCmkClientHelper.decode("YnV0dGVy"))
            .thenReturn(encodedValue);
    }

    @Test
    public void encryptDecryptTest() {

        byte[] encryptedButter = kmsCmkClientHelper.encrypt("butter".getBytes());
        byte[] butter = kmsCmkClientHelper.decrypt(encryptedButter);

        Assert.assertEquals(new String(butter, kmsCmkClientHelper.getCharset()), "butter");
    }

    @Test
    public void encodeTest() {

        String encodedButter = kmsCmkClientHelper.encode("butter".getBytes(kmsCmkClientHelper.getCharset()));

        Assert.assertEquals(encodedButter, "YnV0dGVy");
    }

    @Test
    public void decodeTest() {

        byte[] butter = kmsCmkClientHelper.decode("YnV0dGVy");

        Assert.assertEquals(butter, "butter".getBytes(kmsCmkClientHelper.getCharset()));
    }

    @Test
    public void encodeDecodeTest() {

        String encodedButter = kmsCmkClientHelper.encode("butter".getBytes(kmsCmkClientHelper.getCharset()));
        byte[] butter = kmsCmkClientHelper.decode(encodedButter);

        Assert.assertEquals(butter, "butter".getBytes(kmsCmkClientHelper.getCharset()));
    }

    @Test
    public void encodeEncryptDecryptDecodeTest() {

        byte[] encryptedButter =
            kmsCmkClientHelper.encrypt("butter".getBytes(kmsCmkClientHelper.getCharset()));

        when(kmsCmkClientHelper.encode(encryptedButter))
            .thenReturn("W0JANjhiNTg2NDQ=");
        when(kmsCmkClientHelper.decode("W0JANjhiNTg2NDQ="))
            .thenReturn(encryptedButter);


        String encodedEncryptedButter =
            kmsCmkClientHelper.encode(encryptedButter);

        byte[] decodedEncryptedbutter = kmsCmkClientHelper.decode(encodedEncryptedButter);
        byte[] butter = kmsCmkClientHelper.decrypt(decodedEncryptedbutter);

        Assert.assertEquals(butter, "butter".getBytes(kmsCmkClientHelper.getCharset()));
    }
}
