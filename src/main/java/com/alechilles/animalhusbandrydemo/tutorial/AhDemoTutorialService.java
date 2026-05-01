package com.alechilles.animalhusbandrydemo.tutorial;

import com.alechilles.alecstamework.Tamework;
import com.alechilles.alecstamework.api.NpcProfileChangedEvent;
import com.alechilles.alecstamework.config.TameworkMetadataKeys;
import com.alechilles.alecstamework.npc.components.TameworkBreedingComponent;
import com.alechilles.alecstamework.npc.components.TameworkCommandLinksComponent;
import com.alechilles.alecstamework.npc.components.TameworkHappinessComponent;
import com.alechilles.alecstamework.npc.components.TameworkLifeStageComponent;
import com.alechilles.alecstamework.npc.components.TameworkNeedsComponent;
import com.alechilles.alecstamework.npc.components.TameworkOwnerComponent;
import com.alechilles.alecstamework.npc.components.TameworkTamedComponent;
import com.alechilles.animalhusbandrydemo.runtime.DemoLoadoutService;
import com.alechilles.animalhusbandrydemo.runtime.DemoSession;
import com.alechilles.animalhusbandrydemo.runtime.DemoSessionRegistry;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.math.vector.Vector3d;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AhDemoTutorialService {
    private static final long POLL_INTERVAL_SECONDS = 1L;
    private static final double FARM_MIN_X = -870.0;
    private static final double FARM_MAX_X = -830.0;
    private static final double FARM_MIN_Z = 104.0;
    private static final double FARM_MAX_Z = 128.5;
    private static final TutorialViewModel EMPTY_VIEW_MODEL = new TutorialState(Instant.EPOCH).viewModel();

    private final DemoSessionRegistry registry;
    private final HytaleLogger logger;
    private final Map<UUID, RuntimeState> runtimeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, AhDemoTutorialHud> hudByPlayer = new ConcurrentHashMap<>();
    private ScheduledFuture<?> pollTask;
    private AutoCloseable profileEventSubscription;

    public AhDemoTutorialService(@Nonnull DemoSessionRegistry registry, @Nonnull HytaleLogger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    public void start() {
        if (pollTask == null || pollTask.isCancelled()) {
            pollTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                    this::pollActiveSessions,
                    1,
                    POLL_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );
        }
        subscribeProfileEvents();
    }

    public void shutdown() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        if (profileEventSubscription != null) {
            try {
                profileEventSubscription.close();
            } catch (Exception exception) {
                logger.at(Level.FINE).withCause(exception).log("Failed to close AH demo tutorial event subscription.");
            }
            profileEventSubscription = null;
        }
        runtimeByPlayer.clear();
        hudByPlayer.clear();
    }

    public void startSession(@Nonnull DemoSession session, @Nonnull World world) {
        runtimeByPlayer.put(session.getPlayerUuid(), new RuntimeState(Instant.now()));
        world.execute(() -> installOrRefreshHud(session, world));
    }

    public void endSession(@Nonnull DemoSession session) {
        runtimeByPlayer.remove(session.getPlayerUuid());
        AhDemoTutorialHud hud = hudByPlayer.remove(session.getPlayerUuid());
        Universe universe = Universe.get();
        World world = universe != null ? universe.getWorld(session.getInstanceWorldUuid()) : null;
        if (hud == null || world == null || !world.isAlive()) {
            return;
        }
        world.execute(() -> {
            Player player = resolvePlayer(world, session.getPlayerUuid());
            clearHud(player);
        });
    }

    public void cleanupHud(@Nullable Player player) {
        clearHud(player);
    }

    public void openGuide(@Nonnull Player player,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerEntityRef,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull DemoSessionServiceSink sink) {
        UUID playerUuid = player.getUuid();
        if (playerUuid == null || registry.get(playerUuid) == null) {
            sink.send("Start an Animal Husbandry demo before opening the tutorial guide.");
            return;
        }
        RuntimeState runtime = runtimeByPlayer.computeIfAbsent(playerUuid, ignored -> new RuntimeState(Instant.now()));
        synchronized (runtime) {
            runtime.state.setHudHidden(false);
        }
        refreshHud(player, playerRef, runtime);
        if (player.getPageManager() == null) {
            sink.send("Unable to open the tutorial guide right now.");
            return;
        }
        player.getPageManager().openCustomPage(playerEntityRef, store, new AhDemoTutorialPage(playerRef, this, playerUuid));
    }

    @Nonnull
    public TutorialViewModel viewModel(@Nonnull UUID playerUuid) {
        RuntimeState runtime = runtimeByPlayer.get(playerUuid);
        if (runtime == null) {
            return EMPTY_VIEW_MODEL;
        }
        synchronized (runtime) {
            return runtime.state.viewModel();
        }
    }

    public void handleGuideAction(@Nonnull UUID playerUuid, @Nullable String action) {
        RuntimeState runtime = runtimeByPlayer.get(playerUuid);
        if (runtime == null || action == null) {
            return;
        }
        synchronized (runtime) {
            Instant now = Instant.now();
            int previousModuleIndex = runtime.state.getModuleIndex();
            switch (action) {
                case "Previous" -> runtime.state.previous(now);
                case "Next" -> runtime.state.next(now);
                case "Restart" -> {
                    runtime.builder.reset();
                    runtime.state.restartModule(now);
                }
                case "ToggleHud" -> runtime.state.setHudHidden(!runtime.state.isHudHidden());
                default -> {
                    return;
                }
            }
            if (runtime.state.getModuleIndex() != previousModuleIndex) {
                runtime.builder.reset();
            }
        }
        refreshSessionHud(playerUuid);
    }

    @Nonnull
    public String describeStatus(@Nonnull UUID playerUuid) {
        RuntimeState runtime = runtimeByPlayer.get(playerUuid);
        if (runtime == null) {
            return "Tutorial inactive.";
        }
        synchronized (runtime) {
            TutorialViewModel model = runtime.state.viewModel();
            return "Tutorial: " + model.title() + " (" + model.progress() + ").";
        }
    }

    private void subscribeProfileEvents() {
        if (profileEventSubscription != null) {
            return;
        }
        Tamework tamework = Tamework.getInstance();
        if (tamework == null || tamework.getApi() == null || tamework.getApi().events() == null) {
            return;
        }
        profileEventSubscription = tamework.getApi().events().subscribe(NpcProfileChangedEvent.class, this::onProfileChanged);
    }

    private void onProfileChanged(@Nonnull NpcProfileChangedEvent event) {
        UUID ownerUuid = event.after() != null ? event.after().ownerUuid() : null;
        if (ownerUuid == null) {
            return;
        }
        RuntimeState runtime = runtimeByPlayer.get(ownerUuid);
        if (runtime != null) {
            synchronized (runtime) {
                if (!runtime.isPlayerVisible()) {
                    return;
                }
                runtime.state.update(TutorialSnapshot.empty(), Instant.now());
            }
            refreshSessionHud(ownerUuid);
        }
    }

    private void pollActiveSessions() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (DemoSession session : registry.sessions()) {
            World world = universe.getWorld(session.getInstanceWorldUuid());
            if (world == null || !world.isAlive()) {
                continue;
            }
            world.execute(() -> pollSession(session, world));
        }
    }

    private void pollSession(@Nonnull DemoSession session, @Nonnull World world) {
        RuntimeState runtime = runtimeByPlayer.computeIfAbsent(session.getPlayerUuid(), ignored -> new RuntimeState(Instant.now()));
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> playerEntityRef = world.getEntityRef(session.getPlayerUuid());
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        Instant now = Instant.now();
        TutorialSnapshot snapshot = collectSnapshot(store, session.getPlayerUuid(), player, playerEntityRef, runtime);
        boolean changed;
        synchronized (runtime) {
            runtime.markPlayerVisible(now);
            int previousModuleIndex = runtime.state.getModuleIndex();
            changed = runtime.state.update(snapshot, now);
            if (runtime.state.getModuleIndex() != previousModuleIndex) {
                runtime.builder.reset();
            }
        }
        if (changed || runtime.isDirty()) {
            refreshHud(player, player.getPlayerRef(), runtime);
        }
    }

    private boolean isDirty(@Nonnull RuntimeState runtime) {
        synchronized (runtime) {
            return runtime.state.isDirty();
        }
    }

    private void installOrRefreshHud(@Nonnull DemoSession session, @Nonnull World world) {
        RuntimeState runtime = runtimeByPlayer.get(session.getPlayerUuid());
        Player player = resolvePlayer(world, session.getPlayerUuid());
        if (runtime != null && player != null) {
            synchronized (runtime) {
                runtime.markPlayerVisible(Instant.now());
            }
            refreshHud(player, player.getPlayerRef(), runtime);
        }
    }

    private void refreshSessionHud(@Nonnull UUID playerUuid) {
        DemoSession session = registry.get(playerUuid);
        RuntimeState runtime = runtimeByPlayer.get(playerUuid);
        Universe universe = Universe.get();
        World world = session != null && universe != null ? universe.getWorld(session.getInstanceWorldUuid()) : null;
        if (session == null || runtime == null || world == null || !world.isAlive()) {
            return;
        }
        world.execute(() -> {
            Player player = resolvePlayer(world, playerUuid);
            if (player != null) {
                refreshHud(player, player.getPlayerRef(), runtime);
            }
        });
    }

    private void refreshHud(@Nonnull Player player, @Nullable PlayerRef playerRef, @Nonnull RuntimeState runtime) {
        if (playerRef == null || player.getHudManager() == null) {
            return;
        }
        TutorialViewModel viewModel;
        synchronized (runtime) {
            viewModel = runtime.state.viewModel();
            runtime.state.markClean();
        }
        AhDemoTutorialHud hud = hudByPlayer.get(player.getUuid());
        if (hud == null) {
            hud = new AhDemoTutorialHud(playerRef, viewModel);
            hudByPlayer.put(player.getUuid(), hud);
            player.getHudManager().setCustomHud(playerRef, hud);
            hud.show();
            return;
        }
        hud.refresh(viewModel);
    }

    private void clearHud(@Nullable Player player) {
        if (player == null || player.getPlayerRef() == null || player.getHudManager() == null) {
            return;
        }
        HudManager hudManager = player.getHudManager();
        hudManager.setCustomHud(player.getPlayerRef(), null);
    }

    @Nullable
    private Player resolvePlayer(@Nonnull World world, @Nonnull UUID playerUuid) {
        Ref<EntityStore> ref = world.getEntityRef(playerUuid);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return world.getEntityStore().getStore().getComponent(ref, Player.getComponentType());
    }

    @Nonnull
    private TutorialSnapshot collectSnapshot(@Nonnull Store<EntityStore> store,
                                             @Nonnull UUID playerUuid,
                                             @Nullable Player player,
                                             @Nonnull Ref<EntityStore> playerEntityRef,
                                             @Nonnull RuntimeState runtime) {
        ArrayList<TutorialSnapshotBuilder.NpcObservation> observations = new ArrayList<>();
        boolean commandTried = hasSelectedDemoCommand(player);
        boolean enteredFarm = isInsideFarm(store, playerEntityRef);
        ComponentType<EntityStore, TameworkOwnerComponent> ownerType = TameworkOwnerComponent.getComponentType();
        ComponentType<EntityStore, TameworkTamedComponent> tamedType = TameworkTamedComponent.getComponentType();
        if (ownerType == null || tamedType == null) {
            return new TutorialSnapshot(enteredFarm, 0, 0, false, commandTried, false, false, false);
        }
        ComponentType<EntityStore, TameworkCommandLinksComponent> commandLinksType = TameworkCommandLinksComponent.getComponentType();
        ComponentType<EntityStore, TameworkNeedsComponent> needsType = TameworkNeedsComponent.getComponentType();
        ComponentType<EntityStore, TameworkHappinessComponent> happinessType = TameworkHappinessComponent.getComponentType();
        ComponentType<EntityStore, TameworkBreedingComponent> breedingType = TameworkBreedingComponent.getComponentType();
        ComponentType<EntityStore, TameworkLifeStageComponent> lifeStageType = TameworkLifeStageComponent.getComponentType();
        store.forEachChunk((BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>>) (chunk, commandBuffer) -> collectChunkObservations(
                chunk,
                playerUuid,
                observations,
                ownerType,
                tamedType,
                commandLinksType,
                needsType,
                happinessType,
                breedingType,
                lifeStageType
        ));
        synchronized (runtime) {
            return runtime.builder.build(observations, commandTried, enteredFarm);
        }
    }

    private boolean isInsideFarm(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerEntityRef) {
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        Vector3d position = transform != null ? transform.getPosition() : null;
        return isInsideFarm(position);
    }

    static boolean isInsideFarm(@Nullable Vector3d position) {
        return position != null
                && position.x >= FARM_MIN_X
                && position.x <= FARM_MAX_X
                && position.z >= FARM_MIN_Z
                && position.z <= FARM_MAX_Z;
    }

    private boolean hasSelectedDemoCommand(@Nullable Player player) {
        Inventory inventory = player != null ? player.getInventory() : null;
        if (inventory == null) {
            return false;
        }
        return hasSelectedDemoCommand(inventory.getHotbar())
                || hasSelectedDemoCommand(inventory.getStorage())
                || hasSelectedDemoCommand(inventory.getBackpack())
                || hasSelectedDemoCommand(inventory.getUtility())
                || hasSelectedDemoCommand(inventory.getTools());
    }

    private boolean hasSelectedDemoCommand(@Nullable ItemContainer container) {
        if (container == null) {
            return false;
        }
        final boolean[] found = { false };
        container.forEach((slot, stack) -> {
            if (!found[0] && isSelectedDemoCommand(stack)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private boolean isSelectedDemoCommand(@Nullable ItemStack stack) {
        if (!DemoLoadoutService.shouldRemove(stack)) {
            return false;
        }
        String selected = stack.getFromMetadataOrNull(TameworkMetadataKeys.COMMAND_SELECTED_ID, Codec.STRING);
        return selected != null && !selected.isBlank();
    }

    private void collectChunkObservations(
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull UUID playerUuid,
            @Nonnull ArrayList<TutorialSnapshotBuilder.NpcObservation> observations,
            @Nonnull ComponentType<EntityStore, TameworkOwnerComponent> ownerType,
            @Nonnull ComponentType<EntityStore, TameworkTamedComponent> tamedType,
            @Nullable ComponentType<EntityStore, TameworkCommandLinksComponent> commandLinksType,
            @Nullable ComponentType<EntityStore, TameworkNeedsComponent> needsType,
            @Nullable ComponentType<EntityStore, TameworkHappinessComponent> happinessType,
            @Nullable ComponentType<EntityStore, TameworkBreedingComponent> breedingType,
            @Nullable ComponentType<EntityStore, TameworkLifeStageComponent> lifeStageType) {
        for (int i = 0; i < chunk.size(); i++) {
            NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
            if (npc == null || npc.getUuid() == null) {
                continue;
            }
            TameworkOwnerComponent owner = chunk.getComponent(i, ownerType);
            TameworkTamedComponent tamed = chunk.getComponent(i, tamedType);
            boolean ownedAndTamed = owner != null
                    && playerUuid.equals(owner.getOwnerId())
                    && tamed != null
                    && tamed.isTamed();
            if (!ownedAndTamed) {
                continue;
            }
            TameworkCommandLinksComponent commandLinks = commandLinksType != null
                    ? chunk.getComponent(i, commandLinksType)
                    : null;
            TameworkNeedsComponent needs = needsType != null ? chunk.getComponent(i, needsType) : null;
            TameworkHappinessComponent happiness = happinessType != null ? chunk.getComponent(i, happinessType) : null;
            TameworkBreedingComponent breeding = breedingType != null ? chunk.getComponent(i, breedingType) : null;
            TameworkLifeStageComponent lifeStage = lifeStageType != null ? chunk.getComponent(i, lifeStageType) : null;

            observations.add(new TutorialSnapshotBuilder.NpcObservation(
                    npc.getUuid(),
                    resolveRoleId(npc),
                    true,
                    commandLinks != null && playerUuid.equals(commandLinks.getOwnerId()) && commandLinks.getToolIds().length > 0,
                    needs != null ? needs.getHunger() : null,
                    needs != null ? needs.getThirst() : null,
                    feedImpulseSignatures(happiness),
                    breeding != null && (breeding.getLastPartnerUuid() != null
                            || breeding.getCooldownDurationMs() > 0L
                            || breeding.getCooldownStartedAtMs() > 0L
                            || breeding.getCooldownUntilMs() > 0L),
                    isOffspringOrGrowth(lifeStage)
            ));
        }
    }

    @Nonnull
    private Set<String> feedImpulseSignatures(@Nullable TameworkHappinessComponent happiness) {
        HashSet<String> signatures = new HashSet<>();
        if (happiness == null) {
            return signatures;
        }
        for (TameworkHappinessComponent.ActiveImpulse impulse : happiness.getActiveImpulses()) {
            if (impulse == null || impulse.getKey() == null || !impulse.getKey().startsWith("feed:")) {
                continue;
            }
            signatures.add(impulse.getKey() + "|" + impulse.getExpiresAtMs() + "|" + impulse.getItemId());
        }
        return signatures;
    }

    @Nullable
    private String resolveRoleId(@Nonnull NPCEntity npc) {
        if (npc.getRoleName() != null && !npc.getRoleName().isBlank()) {
            return npc.getRoleName();
        }
        if (npc.getRole() != null && npc.getRole().getRoleName() != null) {
            return npc.getRole().getRoleName();
        }
        NPCPlugin npcPlugin = NPCPlugin.get();
        return npcPlugin != null && npc.getRoleIndex() >= 0 ? npcPlugin.getName(npc.getRoleIndex()) : null;
    }

    private boolean isOffspringOrGrowth(@Nullable TameworkLifeStageComponent lifeStage) {
        if (lifeStage == null || lifeStage.getStage() == null) {
            return false;
        }
        String stage = lifeStage.getStage();
        return "Baby".equalsIgnoreCase(stage)
                || "Adolescent".equalsIgnoreCase(stage)
                || lifeStage.getBornAtMs() > 0L;
    }

    private static final class RuntimeState {
        private final TutorialState state;
        private final TutorialSnapshotBuilder builder = new TutorialSnapshotBuilder();
        private boolean playerVisible;

        private RuntimeState(@Nonnull Instant now) {
            this.state = new TutorialState(now);
        }

        private synchronized boolean isDirty() {
            return state.isDirty();
        }

        private boolean isPlayerVisible() {
            return playerVisible;
        }

        private void markPlayerVisible(@Nonnull Instant now) {
            if (playerVisible) {
                return;
            }
            playerVisible = true;
            if (state.currentModule() == TutorialModule.INTRO
                    && !state.completedTasks().contains(TutorialTaskId.INTRO_READY)) {
                state.resetCurrentModuleTimer(now);
            }
        }
    }

    @FunctionalInterface
    public interface DemoSessionServiceSink {
        void send(@Nonnull String message);
    }
}
