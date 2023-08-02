import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.kmshelper.Base64ClientHelperImpl;

public class Base64ClientHelperTest {

    private Base64ClientHelperImpl base64ClientHelper;

    @BeforeClass
    public void setUp() {
        base64ClientHelper = new Base64ClientHelperImpl();
    }

    @Test
    public void encodeTest() {

        String encodedButter = base64ClientHelper.encode("butter".getBytes(base64ClientHelper.getCharset()));

        Assert.assertEquals(encodedButter, "YnV0dGVy");
    }

    @Test
    public void decodeTest() {

        byte[] butter = base64ClientHelper.decode("YnV0dGVy");

        Assert.assertEquals(butter, "butter".getBytes(base64ClientHelper.getCharset()));
    }

    @Test
    public void encodeDecodeTest() {

        String encodedButter = base64ClientHelper.encode("butter".getBytes(base64ClientHelper.getCharset()));
        byte[] butter = base64ClientHelper.decode(encodedButter);

        Assert.assertEquals(butter, "butter".getBytes(base64ClientHelper.getCharset()));
    }

}
