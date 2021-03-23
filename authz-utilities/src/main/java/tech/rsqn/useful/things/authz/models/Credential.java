package tech.rsqn.useful.things.authz.models;


import java.io.Serializable;

public class Credential implements Serializable {
    private static final long serialVersionUID = 7217987644011908435L;
    protected String identityUid;

    public String getIdentityUid() {
        return identityUid;
    }

    public void setIdentityUid(String identityUid) {
        this.identityUid = identityUid;
    }
}