import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.kmshelper.Base64ClientHelperImpl;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelperImpl;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;

public class KMSCMKClientHelperTest {

    //private String keyArnTest = "arn:aws:kms:ap-southeast-2:258568400917:key/d6851a96-2eb2-4d77-9542-4e6a4f8850b4";

    private KMSCMKClientHelperImpl kmsCmkClientHelper;
    private Base64ClientHelperImpl base64ClientHelper;

    @BeforeClass
    public void setUp() {
        //kmsCmkClientHelper = new KMSCMKClientHelperImpl(keyArnTest);
        kmsCmkClientHelper = Mockito.mock(KMSCMKClientHelperImpl.class);
        base64ClientHelper = Mockito.mock(Base64ClientHelperImpl.class);

        when(base64ClientHelper.getCharset()).thenReturn(StandardCharsets.UTF_8);
        byte[] encValue = "[B@68b58644".getBytes(base64ClientHelper.getCharset());
        byte[] value = "butter".getBytes(base64ClientHelper.getCharset());
        when(kmsCmkClientHelper.encrypt("butter".getBytes(base64ClientHelper.getCharset())))
            .thenReturn(encValue);
        when(kmsCmkClientHelper.decrypt("[B@68b58644".getBytes(base64ClientHelper.getCharset())))
            .thenReturn(value);

        when(base64ClientHelper.encode("butter".getBytes(base64ClientHelper.getCharset())))
            .thenReturn("YnV0dGVy");

        byte[] encodedValue = "butter".getBytes(base64ClientHelper.getCharset());
        when(base64ClientHelper.decode("YnV0dGVy"))
            .thenReturn(encodedValue);
    }

    @Test
    public void encryptDecryptTest() {

        byte[] encryptedButter = kmsCmkClientHelper.encrypt("butter".getBytes());
        byte[] butter = kmsCmkClientHelper.decrypt(encryptedButter);

        Assert.assertEquals(new String(butter, base64ClientHelper.getCharset()), "butter");
    }



    @Test
    public void encodeEncryptDecryptDecodeTest() {

        byte[] encryptedButter =
            kmsCmkClientHelper.encrypt("butter".getBytes(base64ClientHelper.getCharset()));

        when(base64ClientHelper.encode(encryptedButter))
            .thenReturn("W0JANjhiNTg2NDQ=");
        when(base64ClientHelper.decode("W0JANjhiNTg2NDQ="))
            .thenReturn(encryptedButter);


        String encodedEncryptedButter =
            base64ClientHelper.encode(encryptedButter);

        byte[] decodedEncryptedbutter = base64ClientHelper.decode(encodedEncryptedButter);
        byte[] butter = kmsCmkClientHelper.decrypt(decodedEncryptedbutter);

        Assert.assertEquals(butter, "butter".getBytes(base64ClientHelper.getCharset()));
    }
}
