package tech.rsqn.useful.things.kmshelper;

public interface KeyRotateHelper {

    byte[] createKey(String alias);

    void writeKey(String alias, byte[] key);

    byte[] readKey(String alias);

}
