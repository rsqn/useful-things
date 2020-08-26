package tech.rsqn.useful.things.kmshelper;

public abstract class KeyCreationHelper {

    byte[] createKey(String alias) {
        return new byte[0];
    }

    void writeKey(String alias, byte[] key){

    }

    byte[] readKey(String alias){
        return new byte[0];
    }

}
