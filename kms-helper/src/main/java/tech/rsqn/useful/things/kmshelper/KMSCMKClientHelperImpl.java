package tech.rsqn.useful.things.kmshelper;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.AliasListEntry;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.amazonaws.services.kms.model.EncryptRequest;
import com.amazonaws.services.kms.model.EncryptResult;
import com.amazonaws.services.kms.model.GenerateDataKeyRequest;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;
import com.amazonaws.services.kms.model.GenerateRandomRequest;
import com.amazonaws.services.kms.model.KeyListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

public class KMSCMKClientHelperImpl implements KMSCMKClientHelper {

    public static final String ALIAS = "alias/";
    public static final String ARN = "arn:";
    public static final String AES_256 = "AES_256";
    private static Logger logger = LoggerFactory.getLogger(KMSCMKClientHelperImpl.class);

    private AWSKMS kmsClient;

    @Override
    public byte[] encrypt(String kmsCMKArnAliasOrId, byte[] plainText) {

        ByteBuffer wrappedPlainText = ByteBuffer.wrap(plainText);

        EncryptRequest request = new EncryptRequest().withKeyId(retrieveKeyArnFromArnOrAlias(kmsCMKArnAliasOrId)).withPlaintext(wrappedPlainText);
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


    @Override
    public GenerateDataKeyResult generateDataKey(String kmsCMKArnAliasOrId) {
        GenerateDataKeyRequest request = new GenerateDataKeyRequest()
            .withKeyId(retrieveKeyArnFromArnOrAlias(kmsCMKArnAliasOrId))//Specifies the type of data key to return.
            .withKeySpec(AES_256);
        return kmsClient().generateDataKey(request);
    }

    @Override
    public List<AliasListEntry> listAliases() {
        return kmsClient().listAliases().getAliases();
    }

    public String retrieveKeyArnFromArnOrAlias(String keyArnOrAlias) {
        String keyId = "";
        if (keyArnOrAlias.startsWith(ARN)) {
            keyId = keyArnOrAlias;
            logger.info("Using key ARN:"+keyId);
        } else if (!keyArnOrAlias.startsWith(ALIAS)) {
            for (AliasListEntry aliasEntry : listAliases()) {
                if (aliasEntry.getAliasName().equals(keyArnOrAlias)) {
                    keyId = aliasEntry.getTargetKeyId();
                    break;
                }
            }
            if (keyId.isEmpty()) {
                logger.warn("keyId is empty for keyAlias:" + keyArnOrAlias);
            } else {
                logger.info("Using keyid:"+keyId+" for key alias:"+keyArnOrAlias);
            }
        }
        keyId = keyArnOrAlias;
        logger.info("Using keyid:"+keyId);
        return keyId;
    }

    public byte[] generateRandom(int noBytes) {
        GenerateRandomRequest request = new GenerateRandomRequest().withNumberOfBytes(noBytes);
        return kmsClient().generateRandom(request).getPlaintext().array();
    }


    public KMSCMKClientHelperImpl() {
    }


    private AWSKMS kmsClient() {
        if (kmsClient == null) {
            kmsClient =
                AWSKMSClientBuilder.standard().withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                    .build();
            logger.info("kmsClient initialised:");
            for (KeyListEntry key : kmsClient.listKeys().getKeys()) {
                logger.debug("kmsClient key: {}", key.toString());
            }

        }
        return kmsClient;
    }

}
