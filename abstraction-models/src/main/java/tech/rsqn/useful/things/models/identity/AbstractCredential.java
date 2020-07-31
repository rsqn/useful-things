package tech.rsqn.useful.things.models.identity;

import java.io.Serializable;

public class AbstractCredential implements Serializable, Credential {

    private static final long serialVersionUID = -2171913314062954411L;
    protected String identityId;

    @Override
    public String getIdentityUid() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }
}
