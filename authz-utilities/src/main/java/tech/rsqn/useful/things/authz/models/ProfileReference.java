package tech.rsqn.useful.things.authz.models;

import java.io.Serializable;

public class ProfileReference implements Serializable  {

    private static final long serialVersionUID = 7847500742662219561L;
    protected String identityUid;
    protected String profileUid;

    public String getIdentityUid() {
        return identityUid;
    }

    public void setIdentityUid(String identityUid) {
        this.identityUid = identityUid;
    }

    public String getProfileUid() {
        return profileUid;
    }

    public void setProfileUid(String profileUid) {
        this.profileUid = profileUid;
    }
}
