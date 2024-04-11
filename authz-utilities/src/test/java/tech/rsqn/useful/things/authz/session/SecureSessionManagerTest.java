package tech.rsqn.useful.things.authz.session;


import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tech.rsqn.cacheservice.CacheService;
import tech.rsqn.cacheservice.hashmapcache.ReferenceHashMapCacheService;
import tech.rsqn.useful.things.authz.models.Credential;
import tech.rsqn.useful.things.authz.models.Identity;
import tech.rsqn.useful.things.authz.models.Token;
import tech.rsqn.useful.things.authz.sessions.SecureSessionManager;
import tech.rsqn.useful.things.authz.sessions.SessionException;
import tech.rsqn.useful.things.authz.sessions.model.SecureSession;

import java.util.UUID;

public class SecureSessionManagerTest {

    CacheService sessionCache;

    CacheService tokenCache;
    CacheService sessionIdRemoteCache;

    SecureSessionManager mgr;

    long sessionTtl = 1000;
    long tokenTtl = 1000;

    @BeforeMethod
    public void setUp() throws Exception {
        sessionCache = new ReferenceHashMapCacheService();
        tokenCache = new ReferenceHashMapCacheService();
        sessionIdRemoteCache = new ReferenceHashMapCacheService();

        mgr = new SecureSessionManager();
        mgr.setSessionTtl(sessionTtl);
        mgr.setValidationTokenTtl(tokenTtl);
        mgr.setSessionCache(sessionCache);
        mgr.setTokenCache(tokenCache);
        mgr.setSessionRemoteIdCache(sessionIdRemoteCache);
        mgr.init();

    }

    @Test
    public void shouldCreateUnAuthenticatedSessionOnEstablish() {

        SecureSession ssn = mgr.establishNewSession("1");

        Assert.assertNotNull(ssn);

        Assert.assertEquals(ssn.getAuthenticationState(), SecureSession.AuthenticationState.NOT_AUTHENTICATED);
        Assert.assertEquals(ssn.getSessionState(), SecureSession.SessionState.VALIDATING);

    }

    @Test
    public void shouldSetCorrectTokenExpiry() {
        SecureSession ssn = mgr.establishNewSession("1");
        Assert.assertNotNull(ssn);
        Token gt = mgr.generateValidationToken(ssn);
        Assert.assertNotNull(gt);

        Assert.assertTrue(gt.isValid(), "token is valid");


        Assert.assertTrue(gt.getValidTo().getTime() <= System.currentTimeMillis() + tokenTtl);
        Assert.assertTrue(gt.getValidTo().getTime() <= System.currentTimeMillis() + (tokenTtl * 2));
    }

    @Test
    public void shouldSaveSessionToSessionCacheOnEstablish() {
        SecureSession ssn = mgr.establishNewSession("1");
        Assert.assertNotNull(ssn);
        Assert.assertEquals(sessionCache.count(), 1);
    }

    @Test
    public void shouldSetCorrectSessionExpiry() {
        SecureSession ssn = mgr.establishNewSession("1");
        Assert.assertNotNull(ssn);
        Assert.assertTrue(ssn.getExpiresTs().getTime() <= System.currentTimeMillis() + sessionTtl);
        Assert.assertTrue(ssn.getExpiresTs().getTime() <= System.currentTimeMillis() + (sessionTtl * 2));
    }


    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*VE001.*")
    public void shouldRefuseValidationWithIncorrectToken() {
        SecureSession ssn = mgr.establishNewSession("1");
        mgr.validateSession(ssn, "1234");
    }


    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*VE004.*")
    public void shouldRefuseValidationIfSessionIsInvalidated() {
        SecureSession ssn = mgr.establishNewSession("1");
        mgr.invalidate(ssn);
        mgr.validateSession(ssn, mgr.generateValidationToken(ssn).getCode());
    }


    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*GV002.*")
    public void shouldRefuseValidationIfSessionIsNotFound() {
        SecureSession ssn = mgr.establishNewSession("1");
        ssn.setId("5");
        mgr.validateSession(ssn,  mgr.generateValidationToken(ssn).getCode());
    }


    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*VE005.*")
    public void shouldRefuseValidationWithExpiredToken() {
        mgr.setValidationTokenTtl(100);
        SecureSession ssn = mgr.establishNewSession("1");

        Token token =  mgr.generateValidationToken(ssn);

        try {
            Thread.sleep(110);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        SecureSession ssn2 = mgr.validateSession(ssn, token.getCode());
        Assert.assertNotNull(ssn2);

        ssn2 = mgr.findSessionById(ssn2.getId());
        Assert.assertNotNull(ssn2);
        Assert.assertEquals(ssn2.getSessionState(), SecureSession.SessionState.INVALIDATED);
    }


    @Test
    public void shouldValidateSessionWithCorrectToken() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        SecureSession ssn2 = mgr.validateSession(ssn, token.getCode());
        Assert.assertNotNull(ssn2);
        Assert.assertEquals(ssn2.getSessionState(), SecureSession.SessionState.VALIDATED);
    }


    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*I002.*")
    public void shouldNotLinkIdentityToNonValidatedSession() {
        SecureSession ssn = mgr.establishNewSession("1");
        Identity ident = new Identity();
        mgr.identifyUserInSession(ssn, ident);
    }

    @Test
    public void shouldLinkIdentityToValidatedSession() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        SecureSession ssn2 = mgr.validateSession(ssn, token.getCode());

        Identity ident = new Identity();
        ssn2 = mgr.identifyUserInSession(ssn2, ident);

        Assert.assertNotNull(ssn2.getIdentity());

    }

    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*I003.*")
    public void shouldNotLinkIdentityToAlreadyIdentifiedSession() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        SecureSession ssn2 = mgr.validateSession(ssn, token.getCode());

        Identity ident = new Identity();
        ssn2 = mgr.identifyUserInSession(ssn2, ident);

        Assert.assertNotNull(ssn2.getIdentity());

        Identity ident2 = new Identity();
        ident2.setUid("5");
        mgr.identifyUserInSession(ssn2, ident2);
    }


    @Test
    public void shouldLinkIdentityToAlreadyIdentifiedSessionIfIdentityIsEqual() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        SecureSession ssn2 = mgr.validateSession(ssn, token.getCode());

        Identity ident = new Identity();
        ident.setUid("5");
        ssn2 = mgr.identifyUserInSession(ssn2, ident);

        Assert.assertNotNull(ssn2.getIdentity());

        Identity ident2 = new Identity();
        ident2.setUid("5");
        mgr.identifyUserInSession(ssn2, ident2);
    }


    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*G002.*")
    public void shouldNotGenerateAuthenticationTokenForSessionInNonValidatedState() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        Token tok = mgr.generateAuthenticationToken(ssn);

    }


    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*G001.*")
    public void shouldNotGenerateAuthenticationTokenWhenSessionNotFound() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);

        ssn.setId("5");
        mgr.generateAuthenticationToken(ssn);

    }

    @Test(expectedExceptions = SessionException.class, expectedExceptionsMessageRegExp = ".*A004.*")
    public void shouldNotAuthenticateSessionWithInvalidTokenType() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        mgr.validateSession(ssn, token.getCode());
        Token tok = mgr.generateAuthenticationToken(ssn);

        tok.setScope("FRED"); // this will work as the cache is a hashmap in this test

        Identity ident = new Identity();
        ident.setUid(UUID.randomUUID().toString());
        mgr.authenticateSession(ssn, tok.getCode(), ident, new Credential());

    }


    @Test
    public void shouldGenerateAuthenticationTokenForValidatedSession() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        mgr.validateSession(ssn, token.getCode());
        Token tok = mgr.generateAuthenticationToken(ssn);

        Assert.assertNotNull(tok);

    }


    @Test
    public void shouldAuthenticateSessionWithToken() {
        SecureSession ssn = mgr.establishNewSession("1");
        Token token = mgr.generateValidationToken(ssn);
        mgr.validateSession(ssn, token.getCode());
        Token tok = mgr.generateAuthenticationToken(ssn);

        Identity ident = new Identity();
        ident.setUid(UUID.randomUUID().toString());
        SecureSession check = mgr.authenticateSession(ssn, tok.getCode(), ident, new Credential());

        Assert.assertNotNull(check);
        Assert.assertEquals(check.getSessionState(), SecureSession.SessionState.VALIDATED);
        Assert.assertEquals(check.getAuthenticationState(), SecureSession.AuthenticationState.AUTHENTICATED);
        Assert.assertNotNull(ssn.getIdentity());
        Assert.assertEquals(ssn.getIdentity().getUid(),ident.getUid());
    }


    @Test
    public void shouldPopulateRemoteIdMap() {
        SecureSession ssn = mgr.establishNewSession("7");

        String ssnId = mgr.resolveSessionIdForRemote("7");

        Assert.assertEquals(ssnId,ssn.getId());

        mgr.remove(ssnId);
    }


}
