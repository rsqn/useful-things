package tech.rsqn.useful.things.authz.models;

import tech.rsqn.useful.things.util.RandomUtil;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Token implements Serializable {
    private static final long serialVersionUID = 3713783499189656756L;
    private Identity identity;
    private String code;
    private String grantedBy;
    private String tokenScope;
    private String resourceScope;
    private String resource;
    private Date validFrom;
    private Date validTo;
    private Map<String, String> attrs = new HashMap<>();

    public Token() {
        this.validFrom = new Date();
    }

    public Token withRandomCode(int len) {
        this.code = RandomUtil.getRandomString(32);
        return this;
    }

    public Token(final String code) {
        this.validFrom = new Date();
        this.code = code;
    }

    public boolean isValid() {
        long ms = System.currentTimeMillis();
        return this.validFrom.getTime() <= ms && this.validTo.getTime() >= ms;
    }


    public Token withResource(String scope, String resource) {
        this.resourceScope = scope;
        this.resource = resource;
        return this;
    }


    public String getTokenScope() {
        return tokenScope;
    }

    public void setTokenScope(String tokenScope) {
        this.tokenScope = tokenScope;
    }

    public Token attr(String key, String value) {
        this.attrs.put(key, value);
        return this;
    }

    public Token andValidMillis(final int millis) {
        this.validTo = new Date(System.currentTimeMillis() + millis);
        return this;
    }

    public Token andValidMinutes(final int minutes) {
        this.validTo = new Date(System.currentTimeMillis() + (1000L * 60L * minutes));
        return this;
    }

    public Token andValidHours(final int hours) {
        this.validTo = new Date(System.currentTimeMillis() + (1000L * 60L * 60L * hours));
        return this;
    }

    public Token andValidDays(final int days) {
        this.validTo = new Date(System.currentTimeMillis() + (1000L * 60L * 60L * 24L * days));
        return this;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public String getResourceScope() {
        return resourceScope;
    }

    public void setResourceScope(String resourceScope) {
        this.resourceScope = resourceScope;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public Date getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Date validFrom) {
        this.validFrom = validFrom;
    }

    public Date getValidTo() {
        return validTo;
    }

    public void setValidTo(Date validTo) {
        this.validTo = validTo;
    }

    public Map<String, String> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, String> attrs) {
        this.attrs = attrs;
    }
}
