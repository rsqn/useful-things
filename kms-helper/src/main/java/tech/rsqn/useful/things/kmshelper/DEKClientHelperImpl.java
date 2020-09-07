package tech.rsqn.useful.things.kmshelper;

import tech.rsqn.useful.things.encryption.AESEncryptionTool;

public class DEKClientHelperImpl implements DEKClientHelper {

    private byte[] key;

    private String keyDEKArnOrAlias;

    private AESEncryptionTool aesEncryptionTool;

    public DEKClientHelperImpl(byte[] key, String keyDEKArnOrAlias){
        this.key = key;
        this.keyDEKArnOrAlias = keyDEKArnOrAlias;
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

    public String getKeyDEKArnOrAlias() {
        return keyDEKArnOrAlias;
    }

    public AESEncryptionTool getAesEncryptionTool() {
        return aesEncryptionTool;
    }
}