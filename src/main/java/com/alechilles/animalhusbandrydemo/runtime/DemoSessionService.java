package com.alechilles.animalhusbandrydemo.runtime;

import com.alechilles.animalhusbandrydemo.tutorial.AhDemoTutorialService;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DemoSessionService {
    public static final String INSTANCE_ASSET = "AnimalHusbandry_DemoFarm";
    public static final Duration MAX_SESSION_LENGTH = Duration.ofHours(2);
    public static final Duration WORLD_EMPTY_GRACE = Duration.ofSeconds(10);

    private static final Transform INSTANCE_ENTRY = new Transform(-849.48, 123.45, 130.13, 0.0f, 0.0f, 0.0f);
    private static final long AUTO_START_DELAY_SECONDS = 1L;
    private static final long INSTANCE_SEED_DELAY_SECONDS = 3L;

    private final DemoLoadoutService loadoutService;
    private final DemoWorldSeeder worldSeeder;
    private final HytaleLogger logger;
    private final DemoSessionRegistry registry = new DemoSessionRegistry();
    private final AhDemoTutorialService tutorialService;
    private final Map<UUID, Instant> emptySince = new ConcurrentHashMap<>();
    private ScheduledFuture<?> maintenanceTask;

    public DemoSessionService(@Nonnull DemoLoadoutService loadoutService,
                              @Nonnull DemoWorldSeeder worldSeeder,
                              @Nonnull HytaleLogger logger) {
        this.loadoutService = loadoutService;
        this.worldSeeder = worldSeeder;
        this.logger = logger;
        this.tutorialService = new AhDemoTutorialService(registry, logger);
    }

    public void startMaintenance() {
        if (maintenanceTask != null && !maintenanceTask.isCancelled()) {
            return;
        }
        tutorialService.start();
        maintenanceTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                this::runMaintenance,
                10,
                5,
                TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel(false);
            maintenanceTask = null;
        }
        tutorialService.shutdown();
        for (DemoSession session : registry.sessions()) {
            requestInstanceRemoval(session);
        }
        registry.clear();
        emptySince.clear();
    }

    public void startDemo(@Nonnull Player player,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerEntityRef,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World originWorld,
                          @Nonnull MessageSink sink) {
        UUID playerUuid = player.getUuid();
        if (playerUuid == null) {
            sink.send("Unable to start demo: player UUID unavailable.");
            return;
        }
        if (registry.get(playerUuid) != null) {
            sink.send("You already have an active Animal Husbandry demo. Use /ahdemo reset or /ahdemo leave.");
            return;
        }
        if (!registry.markStarting(playerUuid)) {
            sink.send("Your Animal Husbandry demo is already starting.");
            return;
        }

        Transform returnPoint = resolveReturnPoint(player);
        loadoutService.grant(player, playerEntityRef, store);
        CompletableFuture<World> future = spawnInstance(originWorld, returnPoint, playerUuid, sink);
        if (future == null) {
            registry.clearStarting(playerUuid);
            loadoutService.clear(player);
            return;
        }

        InstancesPlugin.teleportPlayerToLoadingInstance(playerEntityRef, store, future, INSTANCE_ENTRY);
        future.whenComplete((instanceWorld, throwable) -> {
            registry.clearStarting(playerUuid);
            if (throwable != null || instanceWorld == null) {
                log(Level.SEVERE, throwable, "Failed to start Animal Husbandry demo for %s", playerUuid);
                clearLoadout(originWorld, player);
                sink.send("Unable to start the Animal Husbandry demo instance.");
                return;
            }
            DemoSession session = new DemoSession(
                    playerUuid,
                    instanceWorld.getWorldConfig().getUuid(),
                    instanceWorld.getName(),
                    originWorld.getWorldConfig().getUuid(),
                    returnPoint,
                    Instant.now()
            );
            DemoSession previous = registry.put(session);
            if (previous != null) {
                tutorialService.endSession(previous);
                requestInstanceRemoval(previous);
            }
            tutorialService.startSession(session, instanceWorld);
            scheduleSeed(instanceWorld);
            sink.send("Animal Husbandry demo ready. Use /ahdemo leave when you are done.");
        });
    }

    public void leaveDemo(@Nonnull Player player,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerEntityRef,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull MessageSink sink) {
        UUID playerUuid = player.getUuid();
        if (playerUuid == null) {
            sink.send("Unable to leave demo: player UUID unavailable.");
            return;
        }
        DemoSession session = registry.remove(playerUuid);
        if (session != null) {
            tutorialService.endSession(session);
        }
        tutorialService.cleanupHud(player);
        loadoutService.clear(player);
        if (session == null) {
            sink.send("No active Animal Husbandry demo session found. Demo items, if any, were cleared.");
            return;
        }
        CompletableFuture<Void> exit = InstancesPlugin.exitInstance(playerEntityRef, store);
        exit.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log(Level.WARNING, throwable, "Failed to exit Animal Husbandry demo for %s", playerUuid);
            }
            requestInstanceRemoval(session);
        });
        sink.send("Leaving Animal Husbandry demo and removing your private farm.");
    }

    public void resetDemo(@Nonnull Player player,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerEntityRef,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World currentWorld,
                          @Nonnull MessageSink sink) {
        UUID playerUuid = player.getUuid();
        if (playerUuid == null) {
            sink.send("Unable to reset demo: player UUID unavailable.");
            return;
        }
        DemoSession oldSession = registry.remove(playerUuid);
        if (oldSession == null) {
            startDemo(player, store, playerEntityRef, playerRef, currentWorld, sink);
            return;
        }
        if (!registry.markStarting(playerUuid)) {
            registry.put(oldSession);
            sink.send("Your Animal Husbandry demo is already resetting.");
            return;
        }
        tutorialService.endSession(oldSession);
        tutorialService.cleanupHud(player);

        World originWorld = resolveOriginWorld(oldSession, currentWorld);
        Transform returnPoint = oldSession.getReturnPoint().clone();
        loadoutService.grant(player, playerEntityRef, store);
        CompletableFuture<World> future = spawnInstance(originWorld, returnPoint, playerUuid, sink);
        if (future == null) {
            registry.clearStarting(playerUuid);
            loadoutService.clear(player);
            requestInstanceRemoval(oldSession);
            return;
        }

        InstancesPlugin.teleportPlayerToLoadingInstance(playerEntityRef, store, future, INSTANCE_ENTRY);
        future.whenComplete((instanceWorld, throwable) -> {
            registry.clearStarting(playerUuid);
            if (throwable != null || instanceWorld == null) {
                log(Level.SEVERE, throwable, "Failed to reset Animal Husbandry demo for %s", playerUuid);
                registry.put(oldSession);
                if (currentWorld.isAlive()) {
                    tutorialService.startSession(oldSession, currentWorld);
                }
                sink.send("Unable to reset the Animal Husbandry demo instance.");
                return;
            }
            requestInstanceRemoval(oldSession);
            DemoSession session = new DemoSession(
                    playerUuid,
                    instanceWorld.getWorldConfig().getUuid(),
                    instanceWorld.getName(),
                    originWorld.getWorldConfig().getUuid(),
                    returnPoint,
                    Instant.now()
            );
            registry.put(session);
            tutorialService.startSession(session, instanceWorld);
            scheduleSeed(instanceWorld);
            sink.send("Animal Husbandry demo reset. A fresh private farm is ready.");
        });
    }

    public void scheduleAutoStart(@Nonnull PlayerRef playerRef, @Nonnull World originWorld) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null || registry.get(playerUuid) != null || registry.isStarting(playerUuid)) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!originWorld.isAlive()) {
                return;
            }
            originWorld.execute(() -> autoStart(playerUuid, playerRef, originWorld));
        }, AUTO_START_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void autoStart(@Nonnull UUID playerUuid, @Nonnull PlayerRef playerRef, @Nonnull World originWorld) {
        if (registry.get(playerUuid) != null || registry.isStarting(playerUuid)) {
            return;
        }
        Ref<EntityStore> playerEntityRef = originWorld.getEntityRef(playerUuid);
        if (playerEntityRef == null || !playerEntityRef.isValid() || !playerRef.isValid()) {
            return;
        }
        Store<EntityStore> store = originWorld.getEntityStore().getStore();
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        startDemo(player, store, playerEntityRef, playerRef, originWorld, message ->
                logger.at(Level.FINE).log("Auto-start Animal Husbandry demo for %s: %s", playerUuid, message));
    }

    public void openTutorialGuide(@Nonnull Player player,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull Ref<EntityStore> playerEntityRef,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull MessageSink sink) {
        tutorialService.openGuide(player, store, playerEntityRef, playerRef, sink::send);
    }

    public void sendStatus(@Nonnull Player player, @Nonnull MessageSink sink) {
        UUID playerUuid = player.getUuid();
        if (playerUuid == null) {
            sink.send("Animal Husbandry demo status unavailable: player UUID unavailable.");
            return;
        }
        if (registry.isStarting(playerUuid)) {
            sink.send("Animal Husbandry demo is starting.");
            return;
        }
        DemoSession session = registry.get(playerUuid);
        if (session == null) {
            sink.send("No active Animal Husbandry demo session.");
            return;
        }
        long minutes = Duration.between(session.getStartedAt(), Instant.now()).toMinutes();
        sink.send("Animal Husbandry demo active in " + session.getInstanceWorldName() + " for " + minutes + " minute(s). "
                + tutorialService.describeStatus(playerUuid));
    }

    public void cleanupDisconnect(@Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        DemoSession session = playerUuid != null ? registry.remove(playerUuid) : null;
        if (session != null) {
            tutorialService.endSession(session);
        }
        cleanDisconnectedInventory(playerRef);
        if (session != null) {
            requestInstanceRemoval(session);
        }
    }

    private void cleanDisconnectedInventory(@Nonnull PlayerRef playerRef) {
        UUID worldUuid = playerRef.getWorldUuid();
        Ref<EntityStore> entityRef = playerRef.getReference();
        Universe universe = Universe.get();
        World world = universe != null && worldUuid != null ? universe.getWorld(worldUuid) : null;
        if (world == null || entityRef == null || !playerRef.isValid() || !entityRef.isValid()) {
            return;
        }
        world.execute(() -> {
            if (!playerRef.isValid() || !entityRef.isValid()) {
                return;
            }
            try {
                Player player = world.getEntityStore().getStore().getComponent(entityRef, Player.getComponentType());
                loadoutService.clear(player);
            } catch (IllegalStateException ignored) {
                logger.at(Level.FINE).log("Skipped Animal Husbandry demo inventory cleanup for invalid player reference.");
            }
        });
    }

    private void scheduleSeed(@Nonnull World instanceWorld) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!instanceWorld.isAlive()) {
                return;
            }
            instanceWorld.execute(() -> {
                if (instanceWorld.isAlive()) {
                    worldSeeder.seed(instanceWorld);
                }
            });
        }, INSTANCE_SEED_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private CompletableFuture<World> spawnInstance(@Nonnull World originWorld,
                                                   @Nonnull Transform returnPoint,
                                                   @Nonnull UUID playerUuid,
                                                   @Nonnull MessageSink sink) {
        InstancesPlugin instancesPlugin = InstancesPlugin.get();
        if (instancesPlugin == null) {
            sink.send("Unable to start demo: Hytale Instances plugin is not available.");
            return null;
        }
        if (!InstancesPlugin.doesInstanceAssetExist(INSTANCE_ASSET)) {
            sink.send("Unable to start demo: instance asset '" + INSTANCE_ASSET + "' is not loaded.");
            return null;
        }
        String instanceName = instanceName(playerUuid, UUID.randomUUID());
        return instancesPlugin.spawnInstance(INSTANCE_ASSET, instanceName, originWorld, returnPoint);
    }

    @Nonnull
    static String instanceName(@Nonnull UUID playerUuid, @Nonnull UUID nonce) {
        return "ahdemo-" + playerUuid + "-" + nonce.toString().replace("-", "").substring(0, 12);
    }

    private Transform resolveReturnPoint(@Nonnull Player player) {
        TransformComponent transform = player.getTransformComponent();
        if (transform != null) {
            return transform.getTransform().clone();
        }
        return new Transform(0.5, 80.0, 0.5, 0.0f, 180.0f, 0.0f);
    }

    private World resolveOriginWorld(@Nonnull DemoSession session, @Nonnull World fallback) {
        Universe universe = Universe.get();
        World originWorld = universe != null ? universe.getWorld(session.getOriginWorldUuid()) : null;
        return originWorld != null ? originWorld : fallback;
    }

    private void requestInstanceRemoval(@Nonnull DemoSession session) {
        emptySince.remove(session.getInstanceWorldUuid());
        if (session.markRemovalRequested()) {
            InstancesPlugin.safeRemoveInstance(session.getInstanceWorldUuid());
        }
    }

    private void clearLoadout(@Nonnull World world, @Nonnull Player player) {
        world.execute(() -> loadoutService.clear(player));
    }

    private void log(@Nonnull Level level, @Nullable Throwable throwable, @Nonnull String message, Object... args) {
        var api = logger.at(level);
        if (throwable != null) {
            api = api.withCause(throwable);
        }
        api.log(message, args);
    }

    private void runMaintenance() {
        Instant now = Instant.now();
        for (DemoSession session : registry.sessions()) {
            if (Duration.between(session.getStartedAt(), now).compareTo(MAX_SESSION_LENGTH) >= 0) {
                if (registry.remove(session.getPlayerUuid(), session)) {
                    tutorialService.endSession(session);
                    requestInstanceRemoval(session);
                }
                continue;
            }

            Universe universe = Universe.get();
            World world = universe != null ? universe.getWorld(session.getInstanceWorldUuid()) : null;
            if (world == null || !world.isAlive()) {
                if (registry.remove(session.getPlayerUuid(), session)) {
                    tutorialService.endSession(session);
                    emptySince.remove(session.getInstanceWorldUuid());
                }
                continue;
            }
            world.execute(() -> checkWorldEmpty(session, world, Instant.now()));
        }
    }

    private void checkWorldEmpty(@Nonnull DemoSession session, @Nonnull World world, @Nonnull Instant now) {
        if (world.getPlayerCount() > 0) {
            emptySince.remove(session.getInstanceWorldUuid());
            return;
        }
        Instant since = emptySince.computeIfAbsent(session.getInstanceWorldUuid(), ignored -> now);
        if (Duration.between(since, now).compareTo(WORLD_EMPTY_GRACE) >= 0
                && registry.remove(session.getPlayerUuid(), session)) {
            tutorialService.endSession(session);
            requestInstanceRemoval(session);
        }
    }

    public int activeSessionCount() {
        return registry.activeCount();
    }

    @FunctionalInterface
    public interface MessageSink {
        void send(@Nonnull String message);
    }
}
