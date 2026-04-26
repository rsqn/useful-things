package tech.rsqn.useful.things.concurrency;

import java.time.Instant;

/**
 * A handle representing a specific acquisition of a lock key.
 *
 * <p>This is intentionally shaped for distributed locks (lease + fencing token),
 * even when used with the in-process implementation.</p>
 */
public final class LockHandle {
    private final String key;
    private final long fencingToken;
    private final Instant expiresAt;

    public LockHandle(String key, long fencingToken, Instant expiresAt) {
        this.key = key;
        this.fencingToken = fencingToken;
        this.expiresAt = expiresAt;
    }

    public String getKey() {
        return key;
    }

    /**
     * Optional fencing token. Backends that cannot provide tokens may return {@code 0}.
     */
    public long getFencingToken() {
        return fencingToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

