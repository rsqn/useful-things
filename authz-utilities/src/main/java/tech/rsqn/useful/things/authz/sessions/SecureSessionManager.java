package tech.rsqn.useful.things.authz.sessions;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.rsqn.cacheservice.CacheService;
import tech.rsqn.useful.things.authz.models.Credential;
import tech.rsqn.useful.things.authz.models.Identity;
import tech.rsqn.useful.things.authz.models.Token;
import tech.rsqn.useful.things.authz.sessions.model.SecureSession;
import tech.rsqn.useful.things.util.RandomUtil;
import tech.rsqn.useful.things.util.Requirement;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class SecureSessionManager {
    protected static final Logger LOG = LoggerFactory.getLogger(SecureSessionManager.class);
    private CacheService sessionCache;
    private CacheService tokenCache;
    private CacheService sessionRemoteIdCache;

    private long sessionTtl;
    private long validationTokenTtl;
    private long authenticationTokenTtl;
    private long tokenExpiryGracePeriod;
    private long sessionExpriyGracePeriod;

    public static final String TOKEN_TYPE_SESSION_VALIDATION = "SESSION_VALIDATION";
    public static final String TOKEN_TYPE_AUTHENTICATION_WINDOW = "AUTHENTICATION_WINDOW";

    public SecureSessionManager() {
        validationTokenTtl = 1000L * 30L;
        authenticationTokenTtl = 1000L * 60L;
        sessionTtl = 1000L * 60L * 60L;
        tokenExpiryGracePeriod = 1000L * 60L * 2L;
        sessionExpriyGracePeriod = 1000L * 60L * 10L;
    }

    public void setSessionCache(CacheService sessionCache) {
        this.sessionCache = sessionCache;
    }

    public void setTokenCache(CacheService tokenCache) {
        this.tokenCache = tokenCache;
    }

    public void setSessionTtl(long sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public void setValidationTokenTtl(long validationTokenTtl) {
        this.validationTokenTtl = validationTokenTtl;
    }

    public void setSessionRemoteIdCache(CacheService sessionRemoteIdCache) {
        this.sessionRemoteIdCache = sessionRemoteIdCache;
    }

    public void setAuthenticationTokenTtl(long authenticationTokenTtl) {
        this.authenticationTokenTtl = authenticationTokenTtl;
    }

    public void setTokenExpiryGracePeriod(long tokenExpiryGracePeriod) {
        this.tokenExpiryGracePeriod = tokenExpiryGracePeriod;
    }

    public void setSessionExpriyGracePeriod(long sessionExpriyGracePeriod) {
        this.sessionExpriyGracePeriod = sessionExpriyGracePeriod;
    }

    public String resolveSessionIdForRemote(String remoteId) {
        return sessionRemoteIdCache.get(remoteId);
    }

    public void init() throws Exception {
        Requirement.notNull(tokenCache, "tokenCache");
        Requirement.notNull(sessionCache, "sessionCache");
    }

    private SecureSession generateSession() {
        // will find one
        for (int i = 0; i < 5000; i++) {
            SecureSession session = new SecureSession();
            session.setId(RandomUtil.getUid());
            if (!sessionCache.containsKey(session.getId())) {
                session.setStartedTs(new Date());
                _commitSession(session);
                return session;
            }
        }
        throw new RuntimeException("Unable to generate a session");
    }

    private Token generateBlankToken(long ttl) {
        // will find one
        for (int i = 0; i < 5000; i++) {
            Token token = new Token();
            token.setValidTo(new Date(System.currentTimeMillis() + ttl));
            if (!tokenCache.containsKey(token.getCode())) {
                tokenCache.putWithTTL(token.getCode(), token, ttl + tokenExpiryGracePeriod);
                return token;
            }
        }

        throw new RuntimeException("Unable to generate a token");
    }

    private Map<String, List<Callable>> cleanUpHandlers = new Hashtable<>();
    private BlockingQueue<Callable> q = new ArrayBlockingQueue(5000);


    //todo : threadpool this
    private void processCleanupQueue() {
        if (q.size() > 0) {
            LOG.info("Processing cleanup queue ");
            Callable cb = null;
            try {
                while ((cb = q.poll(100, TimeUnit.MILLISECONDS)) != null) {
                    try {
                        cb.call();
                    } catch ( Exception ex ) {

                    }
                }
            } catch (Exception ie) {
                LOG.warn(ie.getMessage(), ie);
            }

        }
    }

    public void registerCleanupHandler(String sessionId, Callable cb, long delayMs) {
        synchronized (cleanUpHandlers) {
            List<Callable> handlers = cleanUpHandlers.get(sessionId);
            if (handlers == null) {
                handlers = new ArrayList<>();
            }
            handlers.add(new Callable() {
                @Override
                public Object call() throws Exception {
                    try {
                        Thread.sleep(delayMs);
                    } catch (Exception ex) {
                        LOG.warn("Error calling clean up handler for " + sessionId, ex);
                    }
                    try {
                        LOG.info("Calling cleanup handler for session " + sessionId);
                        cb.call();
                    } catch (Exception ex) {
                        LOG.warn("Error calling clean up handler for " + sessionId, ex);
                    }
                    return null;
                }
            });
            cleanUpHandlers.put(sessionId, handlers);
        }
    }

    private void onCleanUp(String sessionId) {
        synchronized (cleanUpHandlers) {
            List<Callable> _handlers = cleanUpHandlers.get(sessionId);
            if (_handlers == null) {
                LOG.info("no cleanup handlers registered for " + sessionId);
                return;
            }
            for (Callable handler : _handlers) {
                q.add(handler);
            }
            processCleanupQueue();
        }


    }

    public void invalidate(SecureSession _ssn) {
        LOG.info("Invalidating session " + _ssn);
        SecureSession ssn = sessionCache.get(_ssn.getId());

        if (ssn != null) {
            ssn.setSessionState(SecureSession.SessionState.INVALIDATED);
            sessionCache.putWithTTL(ssn.getId(), ssn, sessionExpriyGracePeriod);
        }
    }

    public void remove(SecureSession _ssn) {
        LOG.info("Removing session " + _ssn + " remote " + _ssn.getRemoteId());
        sessionCache.remove(_ssn.getId());
        sessionRemoteIdCache.remove(_ssn.getRemoteId());
        onCleanUp(_ssn.getId());
    }


    private Map<String, Long> scheduledRemovals = new Hashtable<>();

    private void houseKeep() {
        synchronized (scheduledRemovals) {
            List<String> toRemove = new ArrayList<>();
            for (String k : scheduledRemovals.keySet()) {
                Long v = scheduledRemovals.get(k);
                if (v <= System.currentTimeMillis()) {
                    toRemove.add(k);
                }
            }
            for (String k : toRemove) {
                remove(k);
                scheduledRemovals.remove(k);
            }
        }
    }

    public void scheduleRemoval(String ssnId) {
        LOG.info("Scheduling session removal by id " + ssnId);
        synchronized (scheduledRemovals) {
            scheduledRemovals.put(ssnId, System.currentTimeMillis() + 1000L * 60L * 10L);
        }
    }


    public void remove(String ssnId) {
        LOG.info("Removing session by id " + ssnId);
        SecureSession ssn = sessionCache.get(ssnId);
        if (ssn != null) {
            remove(ssn);
        } else {
            LOG.info("Session not found " + ssnId);
            onCleanUp(ssnId);
        }
    }

    public SecureSession establishNewSession(String remoteId) {
        SecureSession session = generateSession();
        session.setIdentity(null);
        session.setRemoteId(remoteId);
        session.setAuthenticationState(SecureSession.AuthenticationState.NOT_AUTHENTICATED);
        session.setSessionState(SecureSession.SessionState.VALIDATING);

        _commitSession(session);
        return session;
    }


    public Token generateValidationToken(SecureSession _s) {
        SecureSession ssn = sessionCache.get(_s.getId());

        if (ssn == null) {
            throw new SessionException("Session validation information incorrect GV002");
        }

        Token token = generateBlankToken(validationTokenTtl);
        token.setResource(ssn.getId());
        token.setScope(TOKEN_TYPE_SESSION_VALIDATION);
        token.setValidTo(new Date(System.currentTimeMillis() + validationTokenTtl));
        tokenCache.putWithTTL(token.getCode(), token, validationTokenTtl + tokenExpiryGracePeriod);
        return token;
    }

    public void _commitSession(SecureSession session) {
        session.setExpiresTs(new Date(System.currentTimeMillis() + sessionTtl));
        sessionCache.putWithTTL(session.getId(), session, sessionTtl + sessionExpriyGracePeriod);
        sessionRemoteIdCache.putWithTTL(session.getRemoteId(), session.getId(), sessionTtl * 2);

        if (scheduledRemovals.containsKey(session.getId())) {
            synchronized (scheduledRemovals) {
                scheduledRemovals.remove(session.getId());
            }
        }
    }

    public void touchSession(SecureSession _s) {
        SecureSession ssn = sessionCache.get(_s.getId());

        if (ssn == null) {
            throw new SessionException("Session validation information incorrect VE002");
        }
        ssn.setAttributes(_s.getAttributes());

        _commitSession(ssn);
    }

    public SecureSession validateSession(SecureSession _s, String _t) {
        Token tok = tokenCache.get(_t);
        SecureSession ssn = sessionCache.get(_s.getId());

        if (tok == null) {
            throw new SessionException("Session validation information incorrect VE001");
        }

        if (ssn == null) {
            throw new SessionException("Session validation information incorrect VE002");
        }

        if (!SecureSession.SessionState.VALIDATING.equals(ssn.getSessionState())) {
            throw new SessionException("Session is in invalid state to perform validation VE004");
        }

        if (TOKEN_TYPE_SESSION_VALIDATION.equals(tok.getScope())) {
            if (ssn.getId().equals(tok.getResource())) {
                if (tok.isValid()) {
                    ssn.setSessionState(SecureSession.SessionState.VALIDATED);
                    tokenCache.remove(tok.getCode());
                    _commitSession(ssn);
                } else {
                    invalidate(ssn);
                    throw new SessionException("Session validation information incorrect VE005");
                }
            } else {
                invalidate(ssn);
                throw new SessionException("Session validation information incorrect VE006");
            }
        } else {
            invalidate(ssn);
            throw new SessionException("Session validation information incorrect VE007");
        }
        return ssn;
    }


    public SecureSession identifyUserInSession(SecureSession _s, Identity identity) {
        SecureSession ssn = sessionCache.get(_s.getId());

        if (ssn == null) {
            throw new SessionException("session state invalid for identification I001");
        }

        if (!SecureSession.SessionState.VALIDATED.equals(ssn.getSessionState())) {
            throw new SessionException("session state invalid for identification  I002");
        }

        if (ssn.getIdentity() != null) {
            if (!identity.getUid().equals(ssn.getIdentity().getUid())) {
                throw new SessionException("session already has an identified user I003");
            }
        }

        ssn.setIdentity(identity);
        _commitSession(ssn);

        return ssn;
    }

    public Token generateAuthenticationToken(SecureSession _s) {
        SecureSession ssn = sessionCache.get(_s.getId());
        if (ssn == null) {
            throw new SessionException("session state invalid for authentication G001");
        }

        if (!SecureSession.SessionState.VALIDATED.equals(ssn.getSessionState())) {
            throw new SessionException("session state invalid for identification  G002");
        }

        Token token = generateBlankToken(authenticationTokenTtl);
        token.setResource(ssn.getId());
        token.setScope(TOKEN_TYPE_AUTHENTICATION_WINDOW);
        token.setValidTo(new Date(System.currentTimeMillis() + authenticationTokenTtl));
        tokenCache.putWithTTL(token.getCode(), token, authenticationTokenTtl + tokenExpiryGracePeriod);
        return token;
    }

    public SecureSession authenticateSession(SecureSession _s, String _t, Identity identity, Credential... credentials) {
        SecureSession ssn = sessionCache.get(_s.getId());
        Token tok = tokenCache.get(_t);


        if (ssn == null) {
            throw new SessionException("session state invalid for authentication A001");
        }

        if (TOKEN_TYPE_AUTHENTICATION_WINDOW.equals(tok.getScope())) {
            if (ssn.getId().equals(tok.getResource())) {
                if (tok.isValid()) {
                    ssn.setAuthenticationState(SecureSession.AuthenticationState.AUTHENTICATED);
                    ssn.setIdentity(identity);
                    LOG.info("Session " + ssn + " authenticated with credentials " + credentials);
                    tokenCache.remove(tok.getCode());
                    _commitSession(ssn);
                } else {
                    invalidate(ssn);
                    throw new SessionException("Session validation information incorrect A002");
                }
            } else {
                invalidate(ssn);
                throw new SessionException("Session validation information incorrect A003");
            }
        } else {
            invalidate(ssn);
            throw new SessionException("Session validation information incorrect A004");
        }

        if (!SecureSession.SessionState.VALIDATED.equals(ssn.getSessionState())) {
            throw new SessionException("session state invalid for authentication  A005");
        }


        if (SecureSession.AuthenticationState.AUTHENTICATED.equals(ssn.getSessionState())) {
            throw new SessionException("session state invalid for authentication  A006");
        }

        if (ssn.getIdentity() != null) {
            if (!identity.getUid().equals(ssn.getIdentity().getUid())) {
                throw new SessionException("session already authentication A007");
            }
        }
        return ssn;
    }


    public SecureSession findSessionById(String id) {
        SecureSession ssn = sessionCache.get(id);
        if (ssn == null) {
            throw new SessionException("Session not found");
        }

        if (ssn.isExpired()) {
            throw new SessionExpiredException("Session has expired");
        }
        return ssn;
    }

    public SecureSession checkIfSecureSessionExists(String id) {
        SecureSession ssn = sessionCache.get(id);
        //   SecureSession ssn = sessionRemoteIdCache.get(id);

        if (ssn == null) {
            return null;
        }
        if (ssn.isExpired()) {
            return null;
        }
        return ssn;
    }

}
