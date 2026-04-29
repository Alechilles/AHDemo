package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.math.vector.Transform;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DemoSessionRegistryTest {
    @Test
    void startCreatesOneActiveSessionAndRejectsDuplicateStart() {
        DemoSessionRegistry registry = new DemoSessionRegistry();
        UUID playerUuid = UUID.randomUUID();

        assertTrue(registry.markStarting(playerUuid));
        assertFalse(registry.markStarting(playerUuid));

        DemoSession session = session(playerUuid);
        assertNull(registry.put(session));

        assertEquals(1, registry.activeCount());
        assertSame(session, registry.get(playerUuid));
        assertFalse(registry.markStarting(playerUuid));
    }

    @Test
    void cleanupIsIdempotentAndRemovalCanBeRequestedOnlyOnce() {
        DemoSessionRegistry registry = new DemoSessionRegistry();
        UUID playerUuid = UUID.randomUUID();
        DemoSession session = session(playerUuid);
        registry.put(session);

        assertSame(session, registry.remove(playerUuid));
        assertNull(registry.remove(playerUuid));
        assertEquals(0, registry.activeCount());

        assertTrue(session.markRemovalRequested());
        assertFalse(session.markRemovalRequested());
    }

    private DemoSession session(UUID playerUuid) {
        return new DemoSession(
                playerUuid,
                UUID.randomUUID(),
                "instance-ahdemo",
                UUID.randomUUID(),
                new Transform(0.5, 80.0, 0.5, 0.0f, 180.0f, 0.0f),
                Instant.now()
        );
    }
}
