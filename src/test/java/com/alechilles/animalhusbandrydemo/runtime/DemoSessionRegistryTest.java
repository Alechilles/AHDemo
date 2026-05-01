package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.math.vector.Transform;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void instanceNamesIncludePlayerAndNonceToAvoidResetCollisions() {
        UUID playerUuid = UUID.fromString("4f0181d6-516c-4fd4-b366-f606d9bb864a");
        String first = DemoSessionService.instanceName(playerUuid, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        String second = DemoSessionService.instanceName(playerUuid, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        assertTrue(first.startsWith("ahdemo-" + playerUuid + "-"));
        assertTrue(second.startsWith("ahdemo-" + playerUuid + "-"));
        assertNotEquals(first, second);
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
