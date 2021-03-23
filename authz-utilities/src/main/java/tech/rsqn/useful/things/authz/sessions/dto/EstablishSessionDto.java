package tech.rsqn.useful.things.authz.sessions.dto;

public class EstablishSessionDto {

    private String sessionId;
    private String validationToken;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getValidationToken() {
        return validationToken;
    }

    public void setValidationToken(String validationToken) {
        this.validationToken = validationToken;
    }
}
