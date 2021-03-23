package tech.rsqn.useful.things.models.identity;

import java.io.Serializable;


/**
 * Used for username and password
 */
public class BasicCredential extends AbstractCredential implements Serializable {

    private static final long serialVersionUID = -3342065836239824452L;
    private String principal;
    private String secretAlg;
    private String secret;

    public String getSecretAlg() {
        return secretAlg;
    }

    public void setSecretAlg(String secretAlg) {
        this.secretAlg = secretAlg;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Override
    public String toString() {
        return "BasicCredential{" +
                "principal='" + principal + '\'' +
                ", secretAlg='" + secretAlg + '\'' +
                ", identityId='" + identityId + '\'' +
                '}';
    }
}
