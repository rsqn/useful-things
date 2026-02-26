package tech.rsqn.useful.things.configuration;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractConfigurationSource implements ConfigurationSource {

    private final Logger LOG = LoggerFactory.getLogger(AbstractConfigurationSource.class);
    

    // Handle the enc/dec outside of this class
    private String encryptedPropertyPrefix = "enc://";


    /**
     * Override this to provide list of names.
     * @return The list of names.
     */
    public abstract List<String> getNames();

    @Override
    public Integer getIntegerValue(final String name) {
        String s = getStringValue(name);
        if (Strings.isNullOrEmpty(s)) {
            LOG.warn("Warning getIntegerValue {} has empty value", name);
            return null;
        }
        return Integer.valueOf(s);
    }

    @Override
    public int getIntValue(String name) {
        return getIntegerValue(name);
    }

    @Override
    public boolean getBoolValue(String name) {
        String s = getStringValue(name);
        if (Strings.isNullOrEmpty(s)) {
            LOG.warn("Warning parseBool on {} has empty value", name);
        }
        return Boolean.valueOf(s);
    }


    @Override
    public List<Integer> getIntArray(String name, String delimiter) {
        String v = getStringValue(name);
        if (v == null) {
            v = "";
        }
        String[] parts = v.split(delimiter);
        List<Integer> ret = new ArrayList<>();
        for (String part : parts) {
            if (!Strings.isNullOrEmpty(part)) {
                ret.add(Integer.parseInt(part));
            }
        }
        return ret;
    }

    @Override
    public List<String> getStringArray(String name, String delimiter) {
        String v = getStringValue(name);
        if (v == null) {
            return new LinkedList<>();
        }
        String[] parts = v.split(delimiter);
        List<String> ret = new ArrayList<>();
        Collections.addAll(ret, parts);
        return ret;

    }

    @Override
    public int getIntValue(String name, int dfl) {
        if (!Strings.isNullOrEmpty(getStringValue(name))) {
            return Integer.parseInt(getStringValue(name));
        }
        return dfl;
    }

    @Override
    public Integer getIntegerValue(final String name, final Integer dfl) {
        if (!Strings.isNullOrEmpty(getStringValue(name))) {
            return getIntegerValue(name);
        }
        return dfl;
    }

    @Override
    public Long getLongValue(final String name) {
        String s = getStringValue(name);
        if (Strings.isNullOrEmpty(s)) {
            LOG.warn("Warning getLongValue {} has empty value", name);
            return null;
        }
        return Long.valueOf(s);
    }

    @Override
    public Long getLongValue(final String name, final Long dfl) {
        String s = getStringValue(name);
        if (Strings.isNullOrEmpty(s)) {
            return dfl;
        }
        return Long.valueOf(s);
    }

    @Override
    public BigDecimal getBigDecimalValue(final String name) {
        String s = getStringValue(name);
        if (Strings.isNullOrEmpty(s)) {
            LOG.warn("Warning getBigDecimalValue {} has empty value", name);
            return null;
        }
        return new BigDecimal(s);
    }

    @Override
    public BigDecimal getBigDecimalValue(final String name, final BigDecimal dfl) {
        String s = getStringValue(name);
        if (Strings.isNullOrEmpty(s)) {
            return dfl;
        }
        return new BigDecimal(s);
    }

    @Override
    public double getDoubleValue(String name, double dfl) {
        if (!Strings.isNullOrEmpty(getStringValue(name))) {
            return Double.valueOf(getStringValue(name));
        }
        return dfl;
    }

    @Override
    public boolean getBoolValue(String name, boolean dfl) {
        String v = getStringValue(name);
        if (!Strings.isNullOrEmpty(v)) {
            return getBoolValue(v);
        }
        return dfl;
    }

    @Override
    public boolean containsProperty(String name) {
        return getStringValue(name) != null;
    }

    @Override
    public boolean hasValue(String name) {
        String v = getStringValue(name);
        return !Strings.isNullOrEmpty(v);
    }

    @Override
    public String requireString(String name) {
        String v = getStringValue(name);
        if (v == null) {
            throw new IllegalStateException("Missing required configuration: " + name);
        }
        return v;
    }

    @Override
    public int requireInt(String name) {
        String v = getStringValue(name);
        if (v == null) {
            throw new IllegalStateException("Missing required configuration: " + name);
        }
        return Integer.parseInt(v);
    }

    @Override
    public long requireLong(String name) {
        String v = getStringValue(name);
        if (v == null) {
            throw new IllegalStateException("Missing required configuration: " + name);
        }
        return Long.parseLong(v);
    }

    @Override
    public double requireDouble(String name) {
        String v = getStringValue(name);
        if (v == null) {
            throw new IllegalStateException("Missing required configuration: " + name);
        }
        return Double.parseDouble(v);
    }

    @Override
    public boolean requireBoolean(String name) {
        String v = getStringValue(name);
        if (v == null) {
            throw new IllegalStateException("Missing required configuration: " + name);
        }
        return Boolean.parseBoolean(v);
    }

    @Override
    public BigDecimal requireBigDecimal(String name) {
        String v = getStringValue(name);
        if (v == null) {
            throw new IllegalStateException("Missing required configuration: " + name);
        }
        return new BigDecimal(v);
    }

    // Handle the enc/dec outside of this class
    protected boolean propertyIsEncrypted(Object value) {
        return value.toString().startsWith(encryptedPropertyPrefix);
    }

}
