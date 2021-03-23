package tech.rsqn.useful.things.models.identity;

import java.io.Serializable;

public class MutualCredential implements Serializable {
    private static final long serialVersionUID = 8046371074478037942L;

    private String uid;
    private String identityId;
    private String domain;
    private String issuer;
    private String apiKey;
    private String pubKey;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    @Override
    public String toString() {
        return "MutualCredential{" +
                "uid='" + uid + '\'' +
                ", identityId='" + identityId + '\'' +
                ", domain='" + domain + '\'' +
                ", issuer='" + issuer + '\'' +
                ", apiKey='" + apiKey + '\'' +
                ", pubKey='" + pubKey + '\'' +
                '}';
    }
}
