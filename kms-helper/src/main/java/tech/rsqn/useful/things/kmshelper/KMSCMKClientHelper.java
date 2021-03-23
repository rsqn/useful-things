package tech.rsqn.useful.things.kmshelper;

import com.amazonaws.services.kms.model.AliasListEntry;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;

import java.util.List;

public interface KMSCMKClientHelper {

    byte[] encrypt(String kmsCMKArnAliasOrId, byte[] plainTextDEK);

    byte[] decrypt(byte[] cryptTextDEK);

    GenerateDataKeyResult generateDataKey(String kmsCMKArnAliasOrId);

    byte[] generateRandom(int noBytes);

    List<AliasListEntry> listAliases();

}
