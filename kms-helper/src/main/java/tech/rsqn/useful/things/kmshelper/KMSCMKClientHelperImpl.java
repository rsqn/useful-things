package tech.rsqn.useful.things.kmshelper;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.amazonaws.services.kms.model.EncryptRequest;
import com.amazonaws.services.kms.model.EncryptResult;
import com.amazonaws.services.kms.model.KeyListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class KMSCMKClientHelperImpl implements KMSCMKClientHelper {

    private static Logger logger = LoggerFactory.getLogger(KMSCMKClientHelperImpl.class);

    private AWSKMS kmsClient;

    private String cmkKeyIdArn;


    @Override
    public byte[] encrypt(byte[] plainText) {

        ByteBuffer wrappedPlainText = ByteBuffer.wrap(plainText);

        EncryptRequest request = new EncryptRequest().withKeyId(cmkKeyIdArn).withPlaintext(wrappedPlainText);
        EncryptResult result = kmsClient().encrypt(request);

        return result.getCiphertextBlob().array();
    }

    @Override
    public byte[] decrypt(byte[] cryptText) {
        ByteBuffer wrappedCryptText = ByteBuffer.wrap(cryptText);

        DecryptRequest request = new DecryptRequest().withCiphertextBlob(wrappedCryptText);
        DecryptResult result = kmsClient().decrypt(request);
        return result.getPlaintext().array();
    }


    public KMSCMKClientHelperImpl(String keyIdArn) {
        this.cmkKeyIdArn = keyIdArn;
    }


    private AWSKMS kmsClient() {
        if (kmsClient == null) {
            kmsClient =
                AWSKMSClientBuilder.standard().withCredentials(DefaultAWSCredentialsProviderChain.getInstance()).build();
            logger.info("kmsClient initialised:");
            for(KeyListEntry key: kmsClient.listKeys().getKeys()) {
                logger.debug("kmsClient key: {}", key.toString());
            }

        }
        return kmsClient;
    }



}
