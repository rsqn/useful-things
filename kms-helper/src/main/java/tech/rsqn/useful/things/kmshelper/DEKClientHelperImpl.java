package tech.rsqn.useful.things.kmshelper;

import tech.rsqn.useful.things.encryption.AESEncryptionTool;

public class DEKClientHelperImpl implements DEKClientHelper {

    private byte[] key;

<<<<<<< HEAD
    private String keyDEKArnOrAlias;

    private AESEncryptionTool aesEncryptionTool;

    public DEKClientHelperImpl(byte[] key, String keyDEKArnOrAlias){
        this.key = key;
        this.keyDEKArnOrAlias = keyDEKArnOrAlias;
=======
    private AESEncryptionTool aesEncryptionTool;

    public DEKClientHelperImpl(byte[] key){
        this.key = key;
>>>>>>> master
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

<<<<<<< HEAD
    public String getKeyDEKArnOrAlias() {
        return keyDEKArnOrAlias;
    }

=======
>>>>>>> master
    public AESEncryptionTool getAesEncryptionTool() {
        return aesEncryptionTool;
    }
}
