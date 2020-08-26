package tech.rsqn.useful.things.kmshelper;

import tech.rsqn.useful.things.encryption.AESEncryptionTool;

public class DEKClientHelperImpl implements DEKClientHelper {

    private String key;

    private AESEncryptionTool aesEncryptionTool;

    public DEKClientHelperImpl(String key){
        this.key = key;
        this.aesEncryptionTool = new AESEncryptionTool();
        aesEncryptionTool.setKey(key.getBytes());
    }

    @Override
    public byte[] encrypt(byte[] plainTextData) {
        return new byte[0];
    }

    @Override
    public byte[] decrypt(byte[] cryptTextData) {
        return new byte[0];
    }
}
