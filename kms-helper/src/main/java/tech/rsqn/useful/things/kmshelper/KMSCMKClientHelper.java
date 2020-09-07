package tech.rsqn.useful.things.kmshelper;

import com.amazonaws.services.kms.model.AliasListEntry;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;

import java.util.List;

public interface KMSCMKClientHelper {

    byte[] encrypt(byte[] plainTextDEK);

    byte[] decrypt(byte[] cryptTextDEK);

    GenerateDataKeyResult generateDataKey();

    byte[] generateRandom(int noBytes);

    List<AliasListEntry> listAliases();

}
