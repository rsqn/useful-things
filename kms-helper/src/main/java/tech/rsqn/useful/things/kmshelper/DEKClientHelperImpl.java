package tech.rsqn.useful.things.kmshelper;

import tech.rsqn.useful.things.encryption.AESEncryptionTool;

import java.util.Arrays;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DEKClientHelperImpl that = (DEKClientHelperImpl) o;
        return Arrays.equals(key, that.key) && aesEncryptionTool.equals(that.aesEncryptionTool);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(aesEncryptionTool);
        result = 31 * result + Arrays.hashCode(key);
        return result;
    }

    @Override
    public String toString() {
        return "DEKClientHelperImpl{" +
            "aesEncryptionTool=" + aesEncryptionTool +
            '}';
    }
}
