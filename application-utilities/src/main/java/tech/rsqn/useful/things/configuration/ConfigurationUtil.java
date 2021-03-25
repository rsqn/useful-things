package tech.rsqn.useful.things.configuration;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class ConfigurationUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationUtil.class);
    private static final char DELIMITER_DEFAULT = '.';

    private char delimiter;
    private ConfigurationSource source;

    private ConfigurationUtil(final ConfigurationSource source) {
        this.source = source;
        this.delimiter = DELIMITER_DEFAULT;
    }

    public static ConfigurationUtil using(final ConfigurationSource source) {
        return new ConfigurationUtil(source);
    }

    public char getDelimiter() {
        return this.delimiter;
    }

    public void setDelimiter(final char delimiter) {
        this.delimiter = delimiter;
    }

    // Handle Boolean Values

    public boolean getBoolValue(final String key) {
        return source.getBoolValue(key);
    }

    public boolean getBoolValue(final String key, final String client) {
        String apiClientOverrideKey = key + this.delimiter + client;

        if (StringUtils.isEmpty(client) || source.getStringValue(apiClientOverrideKey) == null) {
            // client not defined or client key not defined - go up a level (base)
            return getBoolValue(key);
        }

        return source.getBoolValue(apiClientOverrideKey);
    }

    public boolean getBoolValue(final String key, final String client, final String merchant) {
        String merchantKey = key + this.delimiter + client + this.delimiter + merchant;

        if (StringUtils.isEmpty(merchant) || source.getStringValue(merchantKey) == null) {
            // merchant not defined or merchant key not defined - go up a level (client)
            return getBoolValue(key, client);
        }

        return source.getBoolValue(merchantKey);
    }

    public boolean getBoolValue(final String key, final String client, final String merchant, final String currency) {
        String currencyKey = key + this.delimiter + client + this.delimiter + merchant + this.delimiter + currency;

        if (StringUtils.isEmpty(currency) || source.getStringValue(currencyKey) == null) {
            // currency not defined or currency key not defined - go up a level (merchant)
            return getBoolValue(key, client, merchant);
        }

        return source.getBoolValue(currencyKey);
    }

    // Handle Integer Values

    public Integer getIntegerValue(final String key) {
        return source.getIntegerValue(key);
    }

    public Integer getIntegerValue(final String key, final String client) {

        if (StringUtils.isEmpty(client)) {
            return getIntegerValue(key);
        }

        final Integer value = source.getIntegerValue(key + this.delimiter + client);
        return value == null ? getIntegerValue(key) : value;
    }

    public Integer getIntegerValue(final String key, final String client, final String merchant) {
        if (StringUtils.isEmpty(merchant)) {
            return getIntegerValue(key, client);
        }

        final Integer value = source.getIntegerValue(key + this.delimiter + client + this.delimiter + merchant);
        return value == null ? getIntegerValue(key, client) : value;
    }

    public Integer getIntegerValue(final String key, final String client, final String merchant, final String currency) {
        if (StringUtils.isEmpty(currency)) {
            return getIntegerValue(key, client, merchant);
        }

        final Integer value = source.getIntegerValue(key + this.delimiter + client + this.delimiter + merchant + this.delimiter + currency);
        return value == null ? getIntegerValue(key, client, merchant) : value;
    }

    // Handle String Values

    public String getStringValue(final String key) {
        return source.getStringValue(key);
    }

    public String getStringValue(final String key, final String client) {

        if (StringUtils.isEmpty(client)) {
            return getStringValue(key);
        }

        final String value = source.getStringValue(key + this.delimiter + client);
        return value == null ? getStringValue(key) : value;
    }

    public String getStringValue(final String key, final String client, final String merchant) {
        if (StringUtils.isEmpty(merchant)) {
            return getStringValue(key, client);
        }

        final String value = source.getStringValue(key + this.delimiter + client + this.delimiter + merchant);
        return value == null ? getStringValue(key, client) : value;
    }

    public String getStringValue(final String key, final String client, final String merchant, final String currency) {
        if (StringUtils.isEmpty(currency)) {
            return getStringValue(key, client, merchant);
        }

        final String value = source.getStringValue(key + this.delimiter + client + this.delimiter + merchant + this.delimiter + currency);
        return value == null ? getStringValue(key, client, merchant) : value;
    }

    public String getStringValueDynamic(final String key, final String client, final List<String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return getStringValue(key,client);
        }

        for (final String par : parameters) {
            final String res = source.getStringValue(key + this.delimiter + client + this.delimiter + par);
            if (res != null ) {
                logger.debug("property resolved for: {" + key + this.delimiter + client + this.delimiter + par + "}");
                return res;
            }
        }
        return getStringValue(key,client);
    }

    // List Strings

    public List<String> getStringArrayValue(final String key, final String arrayDelimiter) {
        return source.getStringArray(key, arrayDelimiter);
    }

    public List<String> getStringArrayValue(final String key, final String arrayDelimiter, final String client) {

        final String thisKey = key + this.delimiter + client;

        if (StringUtils.isEmpty(client) || source.getStringValue(thisKey) == null) {
            // client not defined or client key not defined - go up a level
            return getStringArrayValue(key, arrayDelimiter);
        }

        return source.getStringArray(thisKey, arrayDelimiter);
    }

    public List<String> getStringArrayValue(final String key, final String arrayDelimiter, final String client, final String merchant) {
        final String thisKey = key + this.delimiter + client + this.delimiter + merchant;
        if (StringUtils.isEmpty(merchant) || source.getStringValue(thisKey) == null) {
            // Merchant not defined or merchant key not defined - go up a level
            return getStringArrayValue(key, arrayDelimiter, client);
        }

        return source.getStringArray(thisKey, arrayDelimiter);
    }

    public List<String> getStringArrayValue(final String key, final String arrayDelimiter, final String client, final String merchant, final String currency) {
        final String thisKey = key + this.delimiter + client + this.delimiter + merchant + this.delimiter + currency;
        if (StringUtils.isEmpty(currency) || source.getStringValue(thisKey) == null) {
            // currency not defined or currency key not defined - go up a level
            return getStringArrayValue(key, arrayDelimiter, client, merchant);
        }

        return source.getStringArray(thisKey, arrayDelimiter);
    }

    public List<String> getRoutingCombinations(final List<String> input) {
        final List<String> results = new ArrayList<>();
        List<String> input_copy = new ArrayList<>(input);

        for (int i = 0; i < input.size(); i++) {
            final String pivot = input.get(i);
            input_copy.remove(pivot);
            //pass pivot and the remaining list elements
            results.addAll(generateRoutingCombinations(pivot,input_copy));
            //restore list back to the original
            input_copy = new ArrayList<>(input);
        }
        return results;
    }

    private List<String> generateRoutingCombinations(final String pivot, final List<String> list) {
        final List<String> result = new ArrayList<>();
        final List<String> validPermutations = generateValidPermutations(list);

        // add pivot as first element
        result.add(pivot);
        // append valid permutations results with pivot
        for (final String validPermutation : validPermutations) {
            // append pivot with default delimiter and result
            result.add(pivot + String.valueOf(delimiter) + validPermutation);
        }
        Collections.reverse(result);
        return result;
    }

    private List<String> generateValidPermutations(final List<String> input){
        final String[] buff = new String[input.size()];
        final LinkedHashSet<String> resultList = new LinkedHashSet<>();

        final int input_size = input.size();
        for (int i = 1; i <= input_size; i++) {
            permutationGenerator(input, 0, i, buff, resultList);
        }
        return new ArrayList<>(resultList);
    }

    private void permutationGenerator(final List<String> input, final int i, final int k, final String[] buff, final LinkedHashSet<String> resultList) {
        if (i < k) {
            for (int j = input.size()-1; j >= 0 ; j--) {
                buff[i] = input.get(j);
                permutationGenerator(input, i + 1, k, buff, resultList);
            }
        } else {
            final LinkedHashSet<String> resList = new LinkedHashSet<>();
            for (final String str : buff) {
                if (str != null)
                    resList.add(str);
            }
            resultList.add(String.join(String.valueOf(delimiter), resList));
        }
    }
}