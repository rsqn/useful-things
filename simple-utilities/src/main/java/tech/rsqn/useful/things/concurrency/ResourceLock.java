package tech.rsqn.useful.things.concurrency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Application-level keyed lock with a distributed-lock-shaped API (lease + optional fencing token).
 *
 * <p>Current implementation is in-process only. Lease expiry is best-effort: if a caller fails to
 * release, a later caller may take over after expiry once the underlying mutex becomes available.
 * Callers should treat fencing tokens as the authority for ordering side effects.</p>
 *
 * <p>Non-reentrant by default: acquiring a key you already hold in the same thread fails.</p>
 */
public final class ResourceLock {

    private static final class LockState {
        private final ReentrantLock lock = new ReentrantLock(true);

        private volatile long currentToken = 0;
        private volatile long ownerThreadId = 0;
        private volatile Instant expiresAt = Instant.EPOCH;
    }

    private final ConcurrentHashMap<String, LockState> states = new ConcurrentHashMap<>();
    private final AtomicLong globalFence = new AtomicLong(0);

    /**
     * Test hook: whether this lock still tracks internal state for {@code key}.
     *
     * <p>In the common uncontended case, successful {@link #release(LockHandle)} should remove the
     * per-key state to avoid unbounded memory growth.</p>
     */
    boolean isTrackedForTests(String key) {
        return states.containsKey(key);
    }

    private void maybeRemoveReleasedState(String key, LockState state) {
        if (state.lock.isLocked()) {
            return;
        }
        if (state.lock.hasQueuedThreads()) {
            return;
        }
        if (!state.expiresAt.equals(Instant.EPOCH)) {
            return;
        }
        if (state.currentToken != 0) {
            return;
        }
        states.remove(key, state);
    }

    /**
     * Attempts to acquire {@code key} within {@code wait}. Returns a {@link LockHandle} on success
     * or {@code null} on failure.
     *
     * @param lease required lease duration; must be positive
     */
    public LockHandle tryAcquire(String key, Duration wait, Duration lease) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(wait, "wait");
        Objects.requireNonNull(lease, "lease");
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("lease must be > 0");
        }
        if (wait.isNegative()) {
            throw new IllegalArgumentException("wait must be >= 0");
        }

        final long deadlineNs = System.nanoTime() + wait.toNanos();

        while (true) {
            LockState state = states.computeIfAbsent(key, k -> new LockState());

            if (state.currentToken != 0 && state.ownerThreadId == Thread.currentThread().threadId()) {
                return null; // non-reentrant (ReentrantLock would otherwise allow re-acquire)
            }

            long remainingNs = deadlineNs - System.nanoTime();
            if (remainingNs < 0) {
                return null;
            }

            boolean acquired;
            try {
                acquired = state.lock.tryLock(remainingNs, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            if (acquired) {
                Instant now = Instant.now();
                if (state.currentToken != 0 && now.isAfter(state.expiresAt)) {
                    // Lease expired while nobody could acquire; treat as stale and allow takeover.
                    state.currentToken = 0;
                    state.ownerThreadId = 0;
                    state.expiresAt = Instant.EPOCH;
                }

                long token = globalFence.incrementAndGet();
                Instant expiresAt = now.plus(lease);
                state.currentToken = token;
                state.ownerThreadId = Thread.currentThread().threadId();
                state.expiresAt = expiresAt;
                return new LockHandle(key, token, expiresAt);
            }
        }
    }

    /**
     * Releases a lock previously acquired via {@link #tryAcquire(String, Duration, Duration)}.
     *
     * <p>Best-effort and idempotent: if the handle does not match current ownership/token,
     * release is ignored.</p>
     */
    public void release(LockHandle handle) {
        if (handle == null) return;

        LockState state = states.get(handle.getKey());
        if (state == null) return;

        if (state.currentToken != handle.getFencingToken()) return;
        if (!state.lock.isHeldByCurrentThread()) return;

        state.ownerThreadId = 0;
        state.expiresAt = Instant.EPOCH;
        state.currentToken = 0;

        state.lock.unlock();

        maybeRemoveReleasedState(handle.getKey(), state);
    }

    public boolean tryWithLock(String key, Duration wait, Duration lease, Runnable action) {
        Objects.requireNonNull(action, "action");
        LockHandle handle = tryAcquire(key, wait, lease);
        if (handle == null) return false;
        try {
            action.run();
            return true;
        } finally {
            release(handle);
        }
    }

    public <T> T tryWithLock(String key, Duration wait, Duration lease, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        LockHandle handle = tryAcquire(key, wait, lease);
        if (handle == null) return null;
        try {
            return action.get();
        } finally {
            release(handle);
        }
    }
}

