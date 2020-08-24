package tech.rsqn.useful.things.kmshelper;

import java.nio.charset.Charset;

public interface KMSCMKClientHelper {



    byte[] encrypt(byte[] plainTextDEK);

    byte[] decrypt(byte[] cryptTextDEK);

    byte[] encode(String data);

    String decode(byte[] data);

    Charset getCharset();
}
