package com.alechilles.animalhusbandrydemo.runtime;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DemoSessionRegistry {
    private final ConcurrentMap<UUID, DemoSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> starting = new ConcurrentHashMap<>();

    public boolean markStarting(@Nonnull UUID playerUuid) {
        if (sessions.containsKey(playerUuid)) {
            return false;
        }
        return starting.putIfAbsent(playerUuid, Boolean.TRUE) == null;
    }

    public void clearStarting(@Nonnull UUID playerUuid) {
        starting.remove(playerUuid);
    }

    public boolean isStarting(@Nonnull UUID playerUuid) {
        return starting.containsKey(playerUuid);
    }

    @Nullable
    public DemoSession get(@Nonnull UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    @Nullable
    public DemoSession put(@Nonnull DemoSession session) {
        clearStarting(session.getPlayerUuid());
        return sessions.put(session.getPlayerUuid(), session);
    }

    @Nullable
    public DemoSession remove(@Nonnull UUID playerUuid) {
        clearStarting(playerUuid);
        return sessions.remove(playerUuid);
    }

    public boolean remove(@Nonnull UUID playerUuid, @Nonnull DemoSession session) {
        clearStarting(playerUuid);
        return sessions.remove(playerUuid, session);
    }

    public int activeCount() {
        return sessions.size();
    }

    @Nonnull
    public Collection<DemoSession> sessions() {
        return sessions.values();
    }

    public void clear() {
        sessions.clear();
        starting.clear();
    }
}
