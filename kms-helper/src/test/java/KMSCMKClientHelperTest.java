


import org.testng.Assert;
import org.testng.annotations.*;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelper;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelperImpl;

public class KMSCMKClientHelperTest {

    String keyArnTest = "arn:aws:kms:ap-southeast-2:258568400917:key/d6851a96-2eb2-4d77-9542-4e6a4f8850b4";

    @BeforeClass
    public void setUp() {

    }

    @Test
    public void encryptDecryptTest() {
        KMSCMKClientHelper kmsCmkClientHelper = new KMSCMKClientHelperImpl(keyArnTest);

        byte[] encryptedButter = kmsCmkClientHelper.encrypt("butter".getBytes());
        byte[] butter = kmsCmkClientHelper.decrypt(encryptedButter);

        Assert.assertEquals(butter.toString(), "butter");
    }
}
