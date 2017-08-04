package tech.rsqn.useful.things.encryption;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyRollingRSAEncryptionTool implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(KeyRollingRSAEncryptionTool.class);
    private Map<String, EncryptionTool> tools = new HashMap<>();
    private String currentAlias;
    private List<EncryptionTool> nonCurrent;

    public KeyRollingRSAEncryptionTool() {
        tools = new HashMap<>();
        nonCurrent = new ArrayList<>();
    }

    public Map<String, EncryptionTool> getTools() {
        return tools;
    }

    public void setTools(Map<String, EncryptionTool> tools) {
        this.tools = tools;
    }

    public String getCurrentAlias() {
        return currentAlias;
    }

    public void setCurrentAlias(String currentAlias) {
        this.currentAlias = currentAlias;
    }

    public EncryptionTool getCurrent() {
        return tools.get(currentAlias);
    }

    public EncryptionTool getForAlias(String alias) {
        return tools.get(alias);
    }

    public void addTool(String alias, RSAEncryptionTool t) {
        tools.put(alias, t);
    }

    public byte[] encrypt(byte[] plainText) {
        return getCurrent().encrypt(plainText);
    }

    public byte[] decrypt(byte[] cryptText) {
        try {
            return getCurrent().decrypt(cryptText);
        } catch (DecryptException bbe) {
            throw new KeyRollRequiredException(bbe.getMessage());
        }
    }

    public String encode(String plainText) {
        return getCurrent().encode(plainText);
    }

    public String decode(String encodedText) {
        try {
            return getCurrent().decode(encodedText);
        } catch (DecryptException bbe) {
            throw new KeyRollRequiredException(bbe.getMessage());
        }
    }

    public String decodeAutoSelect(String encodedText) {
        for (String alias : tools.keySet()) {
            try {
                return getForAlias(alias).decode(encodedText);
            } catch (Exception ex) {
                log.info("Unable to decode with alias " + alias + " " + ex.getMessage());
            }
        }
        throw new UnableToDecryptException();
    }

    public byte[] decryptAutoSelect(byte[] cryptText) {
        for (String alias : tools.keySet()) {
            try {
                return getForAlias(alias).decrypt(cryptText);
            } catch (Exception ex) {
                log.info("Unable to decode with alias " + alias + " " + ex.getMessage());
            }
        }
        throw new UnableToDecryptException();
    }

    public byte[] encrypt(String alias, byte[] plainText) {
        return getForAlias(alias).encrypt(plainText);
    }

    public byte[] decrypt(String alias, byte[] cryptText) {
        return getForAlias(alias).decrypt(cryptText);
    }

    public String encode(String alias, String plainText) {
        return getForAlias(alias).encode(plainText);
    }

    public String decode(String alias, String encodedText) {
        return getForAlias(alias).decode(encodedText);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (tools.get(currentAlias) == null) {
            throw new RuntimeException("No key with alias " + currentAlias + " found");
        }

        for (String s : tools.keySet()) {
            if (!s.equals(currentAlias)) {
                nonCurrent.add(tools.get(s));
            }
        }
    }
}
