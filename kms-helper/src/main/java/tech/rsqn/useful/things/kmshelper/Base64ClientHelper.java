package tech.rsqn.useful.things.kmshelper;

import java.nio.charset.Charset;

public interface Base64ClientHelper {

    String encode(byte[] data);

    byte[] decode(String data);

    Charset getCharset();
}
