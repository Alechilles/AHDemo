package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

public final class DemoLoadoutService {
    public static final String METADATA_KEY = "AlecsAnimalHusbandryDemo.Grant";
    public static final String METADATA_VALUE = "v1";
    private static final int SNAPSHOT_VERSION = 1;

    private static final List<LoadoutItem> LOADOUT = List.of(
            new LoadoutItem("AHDemo_Guide_Item", 1),
            new LoadoutItem("AnimalHusbandry_Command_Item", 1),
            new LoadoutItem("AnimalHusbandry_Command_Item_Shakuhachi", 1),
            new LoadoutItem("Tw_Feed_Trough", 4),
            new LoadoutItem("Tw_Feed_Herbivore", 32),
            new LoadoutItem("Tw_Feed_Carnivore", 16),
            new LoadoutItem("Plant_Crop_Lettuce_Item", 32),
            new LoadoutItem("Plant_Crop_Corn_Item", 32),
            new LoadoutItem("Food_Chicken_Raw", 16),
            new LoadoutItem("Container_Bucket", 2),
            new LoadoutItem("Deco_Bucket", 2),
            new LoadoutItem("Weapon_Shortbow_Tranquilizer", 1),
            new LoadoutItem("Weapon_Arrow_Tranquilizer", 64),
            new LoadoutItem("Potion_Tranquilizer", 6)
    );

    private final Path stashDirectory;
    private final HytaleLogger logger;
    private final Map<UUID, DemoInventorySnapshot> stashes = new ConcurrentHashMap<>();

    public DemoLoadoutService(@Nonnull Path stashDirectory, @Nonnull HytaleLogger logger) {
        this.stashDirectory = stashDirectory;
        this.logger = logger;
    }

    public void grant(@Nonnull Player player,
                      @Nonnull Ref<EntityStore> playerRef,
                      @Nonnull ComponentAccessor<EntityStore> accessor) {
        clearAll(player);
        for (LoadoutItem item : LOADOUT) {
            ItemStackTransaction transaction = player.giveItem(createDemoStack(item.itemId(), item.quantity()), playerRef, accessor);
            if (transaction != null && transaction.getRemainder() != null && !transaction.getRemainder().isEmpty()) {
                player.giveItem(transaction.getRemainder(), playerRef, accessor);
            }
        }
    }

    public boolean enterDemo(@Nonnull UUID playerUuid,
                             @Nonnull Player player,
                             @Nonnull Ref<EntityStore> playerRef,
                             @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (!ensureStashed(playerUuid, player)) {
            return false;
        }
        grant(player, playerRef, accessor);
        return true;
    }

    public void resetDemoInventory(@Nonnull UUID playerUuid,
                                   @Nonnull Player player,
                                   @Nonnull Ref<EntityStore> playerRef,
                                   @Nonnull ComponentAccessor<EntityStore> accessor) {
        grant(player, playerRef, accessor);
    }

    public void restoreOriginalInventory(@Nonnull UUID playerUuid,
                                         @Nullable Player player,
                                         @Nullable Ref<EntityStore> playerRef,
                                         @Nullable ComponentAccessor<EntityStore> accessor) {
        restoreOriginalInventory(playerUuid, player, playerRef, accessor, false);
    }

    public void restoreOriginalInventory(@Nonnull UUID playerUuid,
                                         @Nullable Player player,
                                         @Nullable Ref<EntityStore> playerRef,
                                         @Nullable ComponentAccessor<EntityStore> accessor,
                                         boolean discardInventoryIfMissing) {
        DemoInventorySnapshot snapshot = removeStash(playerUuid);
        if (player == null || player.getInventory() == null) {
            if (snapshot != null && logger != null) {
                logger.at(Level.WARNING).log(
                        "Removed Animal Husbandry demo inventory stash for %s without restoring because player inventory was unavailable.",
                        playerUuid
                );
            }
            return;
        }
        if (snapshot == null) {
            if (discardInventoryIfMissing) {
                clearAll(player);
            } else {
                clear(player);
            }
            return;
        }
        clearAll(player);
        snapshot.restore(player.getInventory(), playerRef, accessor);
    }

    public boolean hasStash(@Nonnull UUID playerUuid) {
        return stashes.containsKey(playerUuid);
    }

    public int clear(@Nullable Player player) {
        if (player == null || player.getInventory() == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int removed = 0;
        removed += clearContainer(inventory.getHotbar());
        removed += clearContainer(inventory.getStorage());
        removed += clearContainer(inventory.getBackpack());
        removed += clearContainer(inventory.getUtility());
        removed += clearContainer(inventory.getTools());
        removed += clearContainer(inventory.getArmor());
        return removed;
    }

    private boolean ensureStashed(@Nonnull UUID playerUuid, @Nonnull Player player) {
        if (hasStash(playerUuid)) {
            return true;
        }
        discardStaleStashFile(playerUuid);
        if (player.getInventory() == null) {
            return false;
        }
        DemoInventorySnapshot snapshot = DemoInventorySnapshot.capture(player.getInventory());
        stashes.put(playerUuid, snapshot);
        try {
            Files.createDirectories(stashDirectory);
            Files.writeString(stashPath(playerUuid), snapshot.toDocument(playerUuid).toJson(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            stashes.remove(playerUuid);
            logger.at(Level.SEVERE).withCause(e).log("Failed to stash Animal Husbandry demo inventory for %s", playerUuid);
            return false;
        }
    }

    private void discardStaleStashFile(@Nonnull UUID playerUuid) {
        Path path = stashPath(playerUuid);
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.delete(path);
            logger.at(Level.WARNING).log(
                    "Discarded stale Animal Husbandry demo inventory stash before creating a fresh stash for %s",
                    playerUuid
            );
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log(
                    "Failed to discard stale Animal Husbandry demo inventory stash for %s",
                    playerUuid
            );
        }
    }

    @Nullable
    private DemoInventorySnapshot removeStash(@Nonnull UUID playerUuid) {
        DemoInventorySnapshot snapshot = stashes.remove(playerUuid);
        Path path = stashPath(playerUuid);
        if (snapshot == null && Files.exists(path)) {
            try {
                snapshot = DemoInventorySnapshot.fromDocument(BsonDocument.parse(Files.readString(path, StandardCharsets.UTF_8)));
            } catch (RuntimeException | IOException e) {
                logger.at(Level.SEVERE).withCause(e).log("Failed to restore Animal Husbandry demo inventory stash for %s", playerUuid);
                return null;
            }
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to delete restored Animal Husbandry demo inventory stash for %s", playerUuid);
        }
        return snapshot;
    }

    @Nonnull
    private Path stashPath(@Nonnull UUID playerUuid) {
        return stashDirectory.resolve(playerUuid + ".json");
    }

    private int clearAll(@Nullable Player player) {
        if (player == null || player.getInventory() == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        inventory.clear();
        int removed = 0;
        removed += clearContainer(inventory.getHotbar(), false);
        removed += clearContainer(inventory.getStorage(), false);
        removed += clearContainer(inventory.getBackpack(), false);
        removed += clearContainer(inventory.getUtility(), false);
        removed += clearContainer(inventory.getTools(), false);
        removed += clearContainer(inventory.getArmor(), false);
        return removed;
    }

    private int clearContainer(@Nullable ItemContainer container) {
        return clearContainer(container, true);
    }

    private int clearContainer(@Nullable ItemContainer container, boolean demoOnly) {
        if (container == null) {
            return 0;
        }
        List<Short> slots = new ArrayList<>();
        container.forEach((slot, stack) -> {
            if (!demoOnly || shouldRemove(stack)) {
                slots.add(slot);
            }
        });
        for (short slot : slots) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY);
        }
        return slots.size();
    }

    @Nonnull
    public static ItemStack createDemoStack(@Nonnull String itemId, int quantity) {
        return new ItemStack(itemId, quantity).withMetadata(METADATA_KEY, Codec.STRING, METADATA_VALUE);
    }

    public static boolean shouldRemove(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String marker = stack.getFromMetadataOrNull(METADATA_KEY, Codec.STRING);
        return METADATA_VALUE.equals(marker);
    }

    @Nonnull
    static ItemStack copyStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        BsonDocument metadata = stack.getMetadata() != null ? stack.getMetadata().clone() : new BsonDocument();
        return new SnapshotItemStack(
                stack.getItemId(),
                stack.getQuantity(),
                stack.getDurability(),
                stack.getMaxDurability(),
                stack.getOverrideDroppedItemAnimation(),
                metadata
        );
    }

    private record LoadoutItem(String itemId, int quantity) {
    }

    record DemoInventorySnapshot(Map<String, ContainerSnapshot> containers,
                                 byte activeHotbarSlot,
                                 byte activeUtilitySlot,
                                 byte activeToolsSlot,
                                 Instant createdAt) {
        private static final List<ContainerSpec> CONTAINERS = List.of(
                new ContainerSpec("hotbar", Inventory.HOTBAR_SECTION_ID),
                new ContainerSpec("storage", Inventory.STORAGE_SECTION_ID),
                new ContainerSpec("backpack", Inventory.BACKPACK_SECTION_ID),
                new ContainerSpec("utility", Inventory.UTILITY_SECTION_ID),
                new ContainerSpec("tools", Inventory.TOOLS_SECTION_ID),
                new ContainerSpec("armor", Inventory.ARMOR_SECTION_ID)
        );

        @Nonnull
        static DemoInventorySnapshot capture(@Nonnull Inventory inventory) {
            Map<String, ContainerSnapshot> containers = new LinkedHashMap<>();
            for (ContainerSpec spec : CONTAINERS) {
                containers.put(spec.name(), ContainerSnapshot.capture(inventory.getSectionById(spec.sectionId())));
            }
            return new DemoInventorySnapshot(
                    containers,
                    inventory.getActiveHotbarSlot(),
                    inventory.getActiveUtilitySlot(),
                    inventory.getActiveToolsSlot(),
                    Instant.now()
            );
        }

        void restore(@Nonnull Inventory inventory,
                     @Nullable Ref<EntityStore> playerRef,
                     @Nullable ComponentAccessor<EntityStore> accessor) {
            for (ContainerSpec spec : CONTAINERS) {
                ContainerSnapshot snapshot = containers.get(spec.name());
                if (snapshot != null) {
                    snapshot.restore(inventory.getSectionById(spec.sectionId()));
                }
            }
            if (playerRef != null && accessor != null) {
                inventory.setActiveHotbarSlot(playerRef, activeHotbarSlot, accessor);
                inventory.setActiveUtilitySlot(playerRef, activeUtilitySlot, accessor);
                inventory.setActiveToolsSlot(playerRef, activeToolsSlot, accessor);
            }
        }

        @Nonnull
        BsonDocument toDocument(@Nonnull UUID playerUuid) {
            BsonDocument root = new BsonDocument();
            root.put("version", new BsonInt32(SNAPSHOT_VERSION));
            root.put("playerUuid", new BsonString(playerUuid.toString()));
            root.put("createdAt", new BsonString(createdAt.toString()));
            root.put("activeHotbarSlot", new BsonInt32(activeHotbarSlot));
            root.put("activeUtilitySlot", new BsonInt32(activeUtilitySlot));
            root.put("activeToolsSlot", new BsonInt32(activeToolsSlot));
            BsonDocument containerDocument = new BsonDocument();
            for (Map.Entry<String, ContainerSnapshot> entry : containers.entrySet()) {
                containerDocument.put(entry.getKey(), entry.getValue().toDocument());
            }
            root.put("containers", containerDocument);
            return root;
        }

        @Nonnull
        static DemoInventorySnapshot fromDocument(@Nonnull BsonDocument document) {
            BsonDocument containerDocument = document.getDocument("containers", new BsonDocument());
            Map<String, ContainerSnapshot> containers = new LinkedHashMap<>();
            for (ContainerSpec spec : CONTAINERS) {
                containers.put(spec.name(), ContainerSnapshot.fromDocument(containerDocument.getDocument(spec.name(), new BsonDocument())));
            }
            return new DemoInventorySnapshot(
                    containers,
                    intValue(document, "activeHotbarSlot", Inventory.INACTIVE_SLOT_INDEX).byteValue(),
                    intValue(document, "activeUtilitySlot", Inventory.INACTIVE_SLOT_INDEX).byteValue(),
                    intValue(document, "activeToolsSlot", Inventory.INACTIVE_SLOT_INDEX).byteValue(),
                    Instant.parse(document.getString("createdAt", new BsonString(Instant.now().toString())).getValue())
            );
        }
    }

    record ContainerSnapshot(Map<Short, ItemStack> slots) {
        @Nonnull
        static ContainerSnapshot capture(@Nullable ItemContainer container) {
            Map<Short, ItemStack> slots = new LinkedHashMap<>();
            if (container != null) {
                container.forEach((slot, stack) -> {
                    if (stack != null && !stack.isEmpty()) {
                        slots.put(slot, copyStack(stack));
                    }
                });
            }
            return new ContainerSnapshot(slots);
        }

        void restore(@Nullable ItemContainer container) {
            if (container == null) {
                return;
            }
            for (Map.Entry<Short, ItemStack> entry : slots.entrySet()) {
                container.setItemStackForSlot(entry.getKey(), copyStack(entry.getValue()));
            }
        }

        @Nonnull
        BsonDocument toDocument() {
            BsonDocument document = new BsonDocument();
            for (Map.Entry<Short, ItemStack> entry : slots.entrySet()) {
                document.put(Short.toString(entry.getKey()), stackToDocument(entry.getValue()));
            }
            return document;
        }

        @Nonnull
        static ContainerSnapshot fromDocument(@Nonnull BsonDocument document) {
            Map<Short, ItemStack> slots = new LinkedHashMap<>();
            for (Map.Entry<String, BsonValue> entry : document.entrySet()) {
                slots.put(Short.parseShort(entry.getKey()), stackFromDocument(entry.getValue().asDocument()));
            }
            return new ContainerSnapshot(slots);
        }
    }

    private record ContainerSpec(String name, int sectionId) {
    }

    @Nonnull
    static BsonDocument stackToDocument(@Nonnull ItemStack stack) {
        BsonDocument document = new BsonDocument();
        document.put("itemId", new BsonString(stack.getItemId()));
        document.put("quantity", new BsonInt32(stack.getQuantity()));
        document.put("durability", new BsonDouble(stack.getDurability()));
        document.put("maxDurability", new BsonDouble(stack.getMaxDurability()));
        document.put("overrideDroppedItemAnimation", BsonBoolean.valueOf(stack.getOverrideDroppedItemAnimation()));
        document.put("metadata", stack.getMetadata() != null ? stack.getMetadata().clone() : new BsonDocument());
        return document;
    }

    @Nonnull
    static ItemStack stackFromDocument(@Nonnull BsonDocument document) {
        return new SnapshotItemStack(
                document.getString("itemId").getValue(),
                intValue(document, "quantity", 1),
                doubleValue(document, "durability", 0.0),
                doubleValue(document, "maxDurability", 0.0),
                document.getBoolean("overrideDroppedItemAnimation", BsonBoolean.FALSE).getValue(),
                document.getDocument("metadata", new BsonDocument()).clone()
        );
    }

    private static Integer intValue(@Nonnull BsonDocument document, @Nonnull String key, int fallback) {
        BsonValue value = document.get(key);
        return value != null && value.isNumber() ? value.asNumber().intValue() : fallback;
    }

    private static double doubleValue(@Nonnull BsonDocument document, @Nonnull String key, double fallback) {
        BsonValue value = document.get(key);
        return value != null && value.isNumber() ? value.asNumber().doubleValue() : fallback;
    }

    private static final class SnapshotItemStack extends ItemStack {
        private SnapshotItemStack(@Nonnull String itemId,
                                  int quantity,
                                  double durability,
                                  double maxDurability,
                                  boolean overrideDroppedItemAnimation,
                                  @Nonnull BsonDocument metadata) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.durability = durability;
            this.maxDurability = maxDurability;
            this.overrideDroppedItemAnimation = overrideDroppedItemAnimation;
            this.metadata = metadata;
        }
    }
}
