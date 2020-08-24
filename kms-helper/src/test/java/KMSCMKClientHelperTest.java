import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelper;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelperImpl;

import static org.mockito.Mockito.when;
import org.mockito.Mock;

public class KMSCMKClientHelperTest {

    private String keyArnTest = "arn:aws:kms:ap-southeast-2:258568400917:key/d6851a96-2eb2-4d77-9542-4e6a4f8850b4";

    @Mock
    private KMSCMKClientHelper kmsCmkClientHelper;

    @BeforeClass
    public void setUp() {
        kmsCmkClientHelper = new KMSCMKClientHelperImpl(keyArnTest);

        when(kmsCmkClientHelper.encrypt("butter".getBytes(kmsCmkClientHelper.getCharset()))).thenReturn("[B@68b58644".getBytes(kmsCmkClientHelper.getCharset()));
        when(kmsCmkClientHelper.decrypt("[B@68b58644".getBytes(kmsCmkClientHelper.getCharset()))).thenReturn("butter".getBytes(kmsCmkClientHelper.getCharset()));
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
        String encodedEncryptedButter =
            kmsCmkClientHelper.encode(encryptedButter);
        byte[] decodedEncryptedbutter = kmsCmkClientHelper.decode(encodedEncryptedButter);
        byte[] butter = kmsCmkClientHelper.decrypt(decodedEncryptedbutter);

        Assert.assertEquals(butter, "butter".getBytes(kmsCmkClientHelper.getCharset()));
    }
}
