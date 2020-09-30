package tech.rsqn.useful.things.mathanddata;

import org.testng.Assert;
import org.testng.annotations.Test;

public class IntMatrixMarshallingTest {

    @Test
    public void shouldMarshallAndUnmarshall() throws Exception {

        IntMatrix src = new IntMatrix().with(1,1,9,10,0);

        src.set(1,5,5,2);

        EncodedMatrix encoded = new EncodedMatrix().with(src);

        IntMatrix dst = encoded.extractMatrix();

        Integer i = dst.get(1, 5, 5);

        Assert.assertTrue(i == 2);


        System.out.println(src.print("SRC"));

        System.out.println(dst.print("DST"));


    }
}
