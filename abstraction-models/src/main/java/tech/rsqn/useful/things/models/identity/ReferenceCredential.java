package tech.rsqn.useful.things.models.identity;

import java.io.Serializable;


/**
 * References that another authentication mechanism was used
 */
public class ReferenceCredential extends AbstractCredential implements Serializable {
    private static final long serialVersionUID = 3179351345178033509L;
    private String principal;
    private String referenceScope;
    private String reference;

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getReferenceScope() {
        return referenceScope;
    }

    public void setReferenceScope(String referenceScope) {
        this.referenceScope = referenceScope;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    @Override
    public String toString() {
        return "ReferenceCredential{" +
                "principal='" + principal + '\'' +
                ", referenceScope='" + referenceScope + '\'' +
                ", reference='" + reference + '\'' +
                '}';
    }
}
