import com.amazonaws.services.kms.model.GenerateDataKeyResult;
import org.bouncycastle.util.encoders.Hex;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.kmshelper.Base64ClientHelper;
import tech.rsqn.useful.things.kmshelper.Base64ClientHelperImpl;
import tech.rsqn.useful.things.kmshelper.DEKClientHelper;
import tech.rsqn.useful.things.kmshelper.DEKClientHelperImpl;
import tech.rsqn.useful.things.kmshelper.KMSCMKClientHelperImpl;

import java.nio.ByteBuffer;

import static org.mockito.Mockito.when;

public class DEKClientHelperTest {

    private String dekKeyTest = "7eb3153e4af392e4b793b8219fbba0eda2c8e50607972db35ca338a66b9a9573";

    private DEKClientHelper dekClientHelper;
    private Base64ClientHelper base64ClientHelper;

    @BeforeClass
    public void setUp() {
        dekClientHelper = new DEKClientHelperImpl(Hex.decode(dekKeyTest), "TEST-pd-20200826");

        base64ClientHelper = new Base64ClientHelperImpl();

    }

    @Test
    public void encryptDecryptTest() {

        byte[] encryptedButter = dekClientHelper.encrypt("butter".getBytes());
        byte[] butter = dekClientHelper.decrypt(encryptedButter);

        Assert.assertEquals(new String(butter, base64ClientHelper.getCharset()), "butter");
    }



    @Test
    public void encodeEncryptDecryptDecodeTest() {

        byte[] encryptedButter =
            dekClientHelper.encrypt("butter".getBytes(base64ClientHelper.getCharset()));

        String encodedEncryptedButter =
            base64ClientHelper.encode(encryptedButter);

        byte[] decodedEncryptedbutter = base64ClientHelper.decode(encodedEncryptedButter);
        byte[] butter = dekClientHelper.decrypt(decodedEncryptedbutter);

        Assert.assertEquals(butter, "butter".getBytes(base64ClientHelper.getCharset()));
    }


}
