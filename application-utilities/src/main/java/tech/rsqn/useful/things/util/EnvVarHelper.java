package tech.rsqn.useful.things.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvVarHelper {
    private final static Pattern ENV_VAR_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)(?::([^\\}]*))?\\}");

    /**
     * Handle "hello ${var}", "${var:default}" formats for environ variable expansion.
     */
    public static String substituteEnvVars(final String text) {
        return substituteEnvVars(text, System.getenv());
    }

    /**
     * Handle "hello ${var}", "${var:default}", find var in replaceMap replace value.
     */
    public static String substituteEnvVars(final String text, final Map<String, ?> replaceMap) {
        if (StringUtils.isBlank(text) || replaceMap == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        final Matcher matcher = ENV_VAR_PATTERN.matcher(text);
        int index = 0;
        while (matcher.find()) {
            sb.append(text, index, matcher.start());
            final String var = matcher.group(1);
            final Object obj = replaceMap.get(var);
            String value;
            if (obj != null) {
                value = String.valueOf(obj);
            } else {
                // if no env variable, see if a default value was specified
                value = matcher.group(2);
                if (value == null) {
                    value = "";
                }
            }
            sb.append(value);
            index = matcher.end();
        }
        sb.append(text, index, text.length());
        return sb.toString();
    }
}
