package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.math.vector.Transform;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;

public final class DemoSession {
    private final UUID playerUuid;
    private final UUID instanceWorldUuid;
    private final String instanceWorldName;
    private final UUID originWorldUuid;
    private final Transform returnPoint;
    private final Instant startedAt;
    private final AtomicBoolean removalRequested = new AtomicBoolean(false);

    public DemoSession(@Nonnull UUID playerUuid,
                       @Nonnull UUID instanceWorldUuid,
                       @Nonnull String instanceWorldName,
                       @Nonnull UUID originWorldUuid,
                       @Nonnull Transform returnPoint,
                       @Nonnull Instant startedAt) {
        this.playerUuid = playerUuid;
        this.instanceWorldUuid = instanceWorldUuid;
        this.instanceWorldName = instanceWorldName;
        this.originWorldUuid = originWorldUuid;
        this.returnPoint = returnPoint;
        this.startedAt = startedAt;
    }

    @Nonnull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nonnull
    public UUID getInstanceWorldUuid() {
        return instanceWorldUuid;
    }

    @Nonnull
    public String getInstanceWorldName() {
        return instanceWorldName;
    }

    @Nonnull
    public UUID getOriginWorldUuid() {
        return originWorldUuid;
    }

    @Nonnull
    public Transform getReturnPoint() {
        return returnPoint;
    }

    @Nonnull
    public Instant getStartedAt() {
        return startedAt;
    }

    public boolean markRemovalRequested() {
        return removalRequested.compareAndSet(false, true);
    }

    public boolean isRemovalRequested() {
        return removalRequested.get();
    }
}
