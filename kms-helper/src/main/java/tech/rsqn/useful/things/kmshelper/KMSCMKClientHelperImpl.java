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
    public byte[] encrypt(byte[] plainText) {

        ByteBuffer plaintext = ByteBuffer.wrap(plainText);

        EncryptRequest req = new EncryptRequest().withKeyId(cmkKeyIdArn).withPlaintext(plaintext);
        ByteBuffer ciphertext = kmsClient().encrypt(req).getCiphertextBlob();

        return ciphertext.array();
    }

    @Override
    public byte[] decrypt(byte[] cryptText) {
        ByteBuffer ciphertextBlob = ByteBuffer.wrap(cryptText);

        DecryptRequest req = new DecryptRequest().withKeyId(cmkKeyIdArn).withCiphertextBlob(ciphertextBlob);
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
