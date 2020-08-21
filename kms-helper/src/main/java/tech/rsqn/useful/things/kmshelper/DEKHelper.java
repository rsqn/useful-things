package tech.rsqn.useful.things.kmshelper;

public interface DEKHelper {

    byte[] encrypt(byte[] plainTextData);

    byte[] decrypt(byte[] cryptTextData);

}
