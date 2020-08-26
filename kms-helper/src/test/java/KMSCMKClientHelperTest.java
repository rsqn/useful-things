import com.amazonaws.services.kms.model.GenerateDataKeyResult;
import org.bouncycastle.util.encoders.Hex;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.kmshelper.Base64ClientHelperImpl;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelperImpl;

import java.nio.ByteBuffer;

import static org.mockito.Mockito.when;

public class KMSCMKClientHelperTest {

//    private String cmkArnTest = "arn:aws:kms:ap-southeast-2:258568400917:key/d6851a96-2eb2-4d77-9542-4e6a4f8850b4";
    private String dekKeyTest = "7eb3153e4af392e4b793b8219fbba0eda2c8e50607972db35ca338a66b9a9573";

    private KMSCMKClientHelperImpl kmsCmkClientHelper;
    private Base64ClientHelperImpl base64ClientHelper;

    @BeforeClass
    public void setUp() {
//        kmsCmkClientHelper = new KMSCMKClientHelperImpl();
//        kmsCmkClientHelper.setCMKKeyArn(cmkArnTest);
        kmsCmkClientHelper = Mockito.mock(KMSCMKClientHelperImpl.class);
        base64ClientHelper = new Base64ClientHelperImpl();

        byte[] encValue = "[B@68b58644".getBytes(base64ClientHelper.getCharset());
        byte[] value = "butter".getBytes(base64ClientHelper.getCharset());
        when(kmsCmkClientHelper.encrypt("butter".getBytes(base64ClientHelper.getCharset())))
            .thenReturn(encValue);
        when(kmsCmkClientHelper.decrypt("[B@68b58644".getBytes(base64ClientHelper.getCharset())))
            .thenReturn(value);

        GenerateDataKeyResult gdkr = new GenerateDataKeyResult();
        gdkr.setPlaintext(ByteBuffer.wrap(Hex.decode(dekKeyTest)));
        when(kmsCmkClientHelper.generateDataKey()).thenReturn(gdkr);

        when(kmsCmkClientHelper.generateRandom(2)).thenReturn(Hex.decode("99"));
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

        String encodedEncryptedButter =
            base64ClientHelper.encode(encryptedButter);

        byte[] decodedEncryptedbutter = base64ClientHelper.decode(encodedEncryptedButter);
        byte[] butter = kmsCmkClientHelper.decrypt(decodedEncryptedbutter);

        Assert.assertEquals(butter, "butter".getBytes(base64ClientHelper.getCharset()));
    }

    @Test
    public void generateDataKeyTest() {
        GenerateDataKeyResult result = kmsCmkClientHelper.generateDataKey();

        Assert.assertEquals(result.getPlaintext().array(), Hex.decode(dekKeyTest));
    }

    @Test
    public void generateRandomTest() {
        byte[] result = kmsCmkClientHelper.generateRandom(2);

        Assert.assertEquals(result, Hex.decode("99"));
    }
}
