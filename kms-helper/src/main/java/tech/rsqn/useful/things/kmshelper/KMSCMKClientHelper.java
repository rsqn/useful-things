package tech.rsqn.useful.things.kmshelper;

import java.nio.charset.Charset;

public interface KMSCMKClientHelper {



    byte[] encrypt(byte[] plainTextDEK);

    byte[] decrypt(byte[] cryptTextDEK);

    String encode(byte[] data);

    byte[] decode(String data);

    Charset getCharset();
}
