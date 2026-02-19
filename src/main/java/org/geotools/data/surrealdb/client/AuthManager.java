package org.geotools.data.surrealdb.client;

import com.surrealdb.signin.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Domain service managing JWT token lifecycle for SurrealDB connections.
 * Thread-safe via ReentrantReadWriteLock.
 *
 * <p>This is a pure state manager — it tracks token validity but does NOT
 * directly interact with the SurrealDB SDK. The actual signin calls are
 * performed by {@link SurrealDBSdkClient}, which updates this manager.</p>
 *
 * <p>Since the SurrealDB SDK Token class does not expose JWT expiry,
 * this manager estimates expiry based on {@code tokenObtainedAt + DEFAULT_TOKEN_TTL_SECONDS}.
 * A refresh buffer ensures proactive re-authentication before actual expiry.</p>
 */
public class AuthManager {

    private static final Logger LOG = LoggerFactory.getLogger(AuthManager.class);

    static final long DEFAULT_TOKEN_TTL_SECONDS = 3600;
    static final long REFRESH_BUFFER_SECONDS = 60;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Token currentToken;
    private Instant tokenObtainedAt;

    /**
     * Records a successful authentication by storing the token and timestamp.
     *
     * @param token the JWT token from a successful signin
     */
    public void updateToken(Token token) {
        lock.writeLock().lock();
        try {
            this.currentToken = token;
            this.tokenObtainedAt = Instant.now();
            LOG.debug("Token updated successfully");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @return true if the token is absent, null, or within the refresh buffer of expiry
     */
    public boolean needsRefresh() {
        lock.readLock().lock();
        try {
            return !hasValidTokenInternal();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return the current JWT token string, or null if not authenticated
     */
    public String getAccessToken() {
        lock.readLock().lock();
        try {
            return currentToken != null ? currentToken.getToken() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return true if a valid (non-expired) token exists
     */
    public boolean hasValidToken() {
        lock.readLock().lock();
        try {
            return hasValidTokenInternal();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Invalidates the current token, forcing re-authentication on next use.
     */
    public void invalidate() {
        lock.writeLock().lock();
        try {
            LOG.debug("Invalidating current auth token");
            this.currentToken = null;
            this.tokenObtainedAt = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean hasValidTokenInternal() {
        if (currentToken == null || tokenObtainedAt == null) {
            return false;
        }
        Instant expiresAt = tokenObtainedAt.plusSeconds(DEFAULT_TOKEN_TTL_SECONDS);
        Instant refreshAt = expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS);
        return Instant.now().isBefore(refreshAt);
    }

    // Visible for testing
    Instant getTokenObtainedAt() {
        lock.readLock().lock();
        try {
            return tokenObtainedAt;
        } finally {
            lock.readLock().unlock();
        }
    }
}
