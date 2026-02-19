package org.geotools.data.surrealdb.client;

import com.surrealdb.signin.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthManagerTest {

    private AuthManager authManager;

    @BeforeEach
    void setUp() {
        authManager = new AuthManager();
    }

    @Test
    void updateTokenStoresToken() {
        Token token = new Token("jwt-token-value");

        authManager.updateToken(token);

        assertEquals("jwt-token-value", authManager.getAccessToken());
    }

    @Test
    void getAccessTokenNullBeforeAuth() {
        assertNull(authManager.getAccessToken());
    }

    @Test
    void getAccessTokenSetAfterUpdate() {
        authManager.updateToken(new Token("my-jwt"));

        assertEquals("my-jwt", authManager.getAccessToken());
    }

    @Test
    void invalidateClearsToken() {
        authManager.updateToken(new Token("jwt"));
        assertNotNull(authManager.getAccessToken());

        authManager.invalidate();

        assertNull(authManager.getAccessToken());
    }

    @Test
    void hasValidTokenFalseBeforeAuth() {
        assertFalse(authManager.hasValidToken());
    }

    @Test
    void hasValidTokenTrueAfterUpdate() {
        authManager.updateToken(new Token("jwt"));

        assertTrue(authManager.hasValidToken());
    }

    @Test
    void hasValidTokenFalseAfterInvalidate() {
        authManager.updateToken(new Token("jwt"));
        assertTrue(authManager.hasValidToken());

        authManager.invalidate();

        assertFalse(authManager.hasValidToken());
    }

    @Test
    void needsRefreshTrueWhenNoToken() {
        assertTrue(authManager.needsRefresh());
    }

    @Test
    void needsRefreshFalseWithValidToken() {
        authManager.updateToken(new Token("jwt"));

        assertFalse(authManager.needsRefresh());
    }

    @Test
    void tokenObtainedAtSetOnUpdate() {
        assertNull(authManager.getTokenObtainedAt());

        authManager.updateToken(new Token("jwt"));

        assertNotNull(authManager.getTokenObtainedAt());
    }

    @Test
    void tokenObtainedAtClearedOnInvalidate() {
        authManager.updateToken(new Token("jwt"));
        assertNotNull(authManager.getTokenObtainedAt());

        authManager.invalidate();

        assertNull(authManager.getTokenObtainedAt());
    }

    @Test
    void multipleUpdateTokensReplacePrevious() {
        authManager.updateToken(new Token("jwt-1"));
        assertEquals("jwt-1", authManager.getAccessToken());

        authManager.updateToken(new Token("jwt-2"));
        assertEquals("jwt-2", authManager.getAccessToken());
    }
}
