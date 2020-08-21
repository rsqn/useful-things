package tech.rsqn.useful.things.kmshelper;

import com.amazonaws.services.kms.model.GrantListEntry;

import java.util.List;

/**
 * Created by joshcaspersz on 27/01/2016.
 */
public interface KMSClientHelper {

    byte[] encrypt(byte[] plainText);

    byte[] decrypt(byte[] cryptText);

}
