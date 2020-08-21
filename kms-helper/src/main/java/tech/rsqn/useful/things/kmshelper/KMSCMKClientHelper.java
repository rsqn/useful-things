package tech.rsqn.useful.things.kmshelper;

public interface KMSCMKClientHelper {

    byte[] encrypt(byte[] plainTextDEK);

    byte[] decrypt(byte[] cryptTextDEK);

}
