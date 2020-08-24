package tech.rsqn.useful.things.kmshelper;

import tech.rsqn.useful.things.encryption.AESEncryptionTool;

public class DEKHelperImpl implements DEKHelper {

    private String key;

    private AESEncryptionTool aesEncryptionTool;

    public DEKHelperImpl(String key){
        this.key = key;
        this.aesEncryptionTool = new AESEncryptionTool();
        aesEncryptionTool.setKey(key.getBytes());
    }

    @Override
    public byte[] encrypt(byte[] plainTextData) {
        return aesEncryptionTool.encrypt(plainTextData);
    }

    @Override
    public byte[] decrypt(byte[] cryptTextData) {
        return aesEncryptionTool.decrypt(cryptTextData);
    }
}
