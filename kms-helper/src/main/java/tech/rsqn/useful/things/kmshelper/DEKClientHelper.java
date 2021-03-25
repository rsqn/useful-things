package tech.rsqn.useful.things.kmshelper;

public interface DEKClientHelper {

    byte[] encrypt(byte[] plainTextData);

    byte[] decrypt(byte[] cryptTextData);

    byte[] getKey();

}
