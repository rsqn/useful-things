package tech.rsqn.useful.things.kmshelper;

public interface DEKClientHelper {

    byte[] encrypt(byte[] plainTextData);

    byte[] decrypt(byte[] cryptTextData);

<<<<<<< HEAD
    String getKeyDEKArnOrAlias();

=======
>>>>>>> master
    byte[] getKey();

}
