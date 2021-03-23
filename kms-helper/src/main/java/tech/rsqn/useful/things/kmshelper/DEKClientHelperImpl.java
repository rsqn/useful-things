package tech.rsqn.useful.things.kmshelper;

import tech.rsqn.useful.things.encryption.AESEncryptionTool;

public class DEKClientHelperImpl implements DEKClientHelper {

    private byte[] key;

    private AESEncryptionTool aesEncryptionTool;

    public DEKClientHelperImpl(byte[] key){
        this.key = key;
        this.aesEncryptionTool = new AESEncryptionTool();
        aesEncryptionTool.setKey(key);
    }

    @Override
    public byte[] encrypt(byte[] plainTextData) {
        return aesEncryptionTool.encrypt(plainTextData);
    }

    @Override
    public byte[] decrypt(byte[] cryptTextData) {
        return aesEncryptionTool.decrypt(cryptTextData);
    }

    public byte[] getKey() {
        return key;
    }

    public AESEncryptionTool getAesEncryptionTool() {
        return aesEncryptionTool;
    }
}
