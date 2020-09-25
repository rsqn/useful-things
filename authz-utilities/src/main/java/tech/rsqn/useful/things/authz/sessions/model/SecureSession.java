package tech.rsqn.useful.things.authz.sessions.model;

import tech.rsqn.useful.things.authz.models.Identity;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SecureSession implements Serializable {
    public static final long DEFAULT_SESSION_TTL = 60L * 1000L * 60L;

    public enum SessionState {VALIDATING, VALIDATED, INVALIDATED, EXPIRED}
    public enum AuthenticationState {NOT_AUTHENTICATED, AUTHENTICATED}

    private String id;
    private Date startedTs;
    private Date expiresTs;
    private Identity identity;
    private Map<String, String> attributes;
    private SessionState sessionState;
    private AuthenticationState authenticationState;
    private String remoteId;

    public SecureSession() {
        startedTs = new Date();
        expiresTs = new Date(System.currentTimeMillis() + DEFAULT_SESSION_TTL);
        attributes = new HashMap<>();
        sessionState = SessionState.VALIDATING;
        authenticationState = AuthenticationState.NOT_AUTHENTICATED;
    }

    public void putAttr(String n, String v) {
        attributes.put(n, v);
    }

    public String getAttr(String n) {
        return attributes.get(n);
    }

    public String getId() {
        return id;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresTs.getTime();
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getStartedTs() {
        return startedTs;
    }

    public void setStartedTs(Date startedTs) {
        this.startedTs = startedTs;
    }

    public Date getExpiresTs() {
        return expiresTs;
    }

    public void setExpiresTs(Date expiresTs) {
        this.expiresTs = expiresTs;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }


    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    public void setSessionState(SessionState sessionState) {
        this.sessionState = sessionState;
    }

    public AuthenticationState getAuthenticationState() {
        return authenticationState;
    }

    public void setAuthenticationState(AuthenticationState authenticationState) {
        this.authenticationState = authenticationState;
    }


    @Override
    public String toString() {
        return "SecureSession{" +
                "id='" + id + '\'' +
                ", startedTs=" + startedTs +
                ", expiresTs=" + expiresTs +
                ", identity=" + identity +
                ", attributes=" + attributes +
                ", sessionState=" + sessionState +
                ", authenticationState=" + authenticationState +
                ", remoteId='" + remoteId + '\'' +
                '}';
    }
}

