package tech.rsqn.useful.things.kmshelper;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.EncryptRequest;

import java.nio.ByteBuffer;

public class KMSCMKClientHelperImpl implements KMSCMKClientHelper {

    private AWSKMS kmsClient;

    private String cmkKeyIdArn;

    @Override
    public byte[] encrypt(byte[] plainTextDEK) {

        ByteBuffer plaintext = ByteBuffer.wrap(plainTextDEK);

        EncryptRequest req = new EncryptRequest().withKeyId(cmkKeyIdArn).withPlaintext(plaintext);
        ByteBuffer ciphertext = kmsClient().encrypt(req).getCiphertextBlob();

        return ciphertext.array();
    }

    @Override
    public byte[] decrypt(byte[] cryptTextDEK) {
        ByteBuffer ciphertextBlob = ByteBuffer.wrap(cryptTextDEK);

        DecryptRequest req = new DecryptRequest().withCiphertextBlob(ciphertextBlob);
        ByteBuffer plainText = kmsClient().decrypt(req).getPlaintext();
        return plainText.array();
    }

    public KMSCMKClientHelperImpl(String keyIdArn) {
        this.cmkKeyIdArn = keyIdArn;
    }

    private AWSKMS kmsClient() {
        if (kmsClient == null) {
            kmsClient = AWSKMSClientBuilder.standard().build();
        }
        return kmsClient;
    }

}
