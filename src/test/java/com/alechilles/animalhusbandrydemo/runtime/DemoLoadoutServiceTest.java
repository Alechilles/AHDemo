package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DemoLoadoutServiceTest {
    @Test
    void selectorRemovesOnlyDemoGrantedStacks() {
        ItemStack demoStack = testStack(metadata(DemoLoadoutService.METADATA_VALUE));
        ItemStack normalSameItem = testStack(new BsonDocument());
        ItemStack normalDifferentItem = testStack(metadata("other"));
        ItemStack emptyStack = testStack(new BsonDocument(), 0);

        assertTrue(DemoLoadoutService.shouldRemove(demoStack));
        assertFalse(DemoLoadoutService.shouldRemove(normalSameItem));
        assertFalse(DemoLoadoutService.shouldRemove(normalDifferentItem));
        assertFalse(DemoLoadoutService.shouldRemove(emptyStack));
    }

    @Test
    void containerSnapshotRestoresOriginalStacksAfterDemoInventoryIsCleared() {
        SimpleItemContainer container = new SimpleItemContainer((short) 2);
        ItemStack hotbarStack = testStack("Original_Item", 5, metadata("original-hotbar"));
        container.setItemStackForSlot((short) 0, hotbarStack);

        DemoLoadoutService.ContainerSnapshot snapshot = DemoLoadoutService.ContainerSnapshot.capture(container);

        container.clear();
        container.setItemStackForSlot((short) 0, testStack("Demo_Item", 1, metadata(DemoLoadoutService.METADATA_VALUE)));

        snapshot.restore(container);

        assertEquals("Original_Item", container.getItemStack((short) 0).getItemId());
        assertEquals(5, container.getItemStack((short) 0).getQuantity());
        assertEquals("original-hotbar", container.getItemStack((short) 0)
                .getFromMetadataOrNull(DemoLoadoutService.METADATA_KEY, com.hypixel.hytale.codec.Codec.STRING));
    }

    @Test
    void itemStackSnapshotRoundTripsStackMetadataAndDurability() {
        ItemStack stack = testStack("Original_Item", 3, 7.5, 10.0, true, metadata("roundtrip"));

        ItemStack restored = DemoLoadoutService.stackFromDocument(DemoLoadoutService.stackToDocument(stack));

        assertEquals("Original_Item", restored.getItemId());
        assertEquals(3, restored.getQuantity());
        assertEquals(7.5, restored.getDurability());
        assertEquals(10.0, restored.getMaxDurability());
        assertTrue(restored.getOverrideDroppedItemAnimation());
        assertEquals("roundtrip", restored.getFromMetadataOrNull(
                DemoLoadoutService.METADATA_KEY,
                com.hypixel.hytale.codec.Codec.STRING
        ));
    }

    @Test
    void staleDiskStashDoesNotCountAsActiveStash(@TempDir Path tempDir) throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Files.writeString(tempDir.resolve(playerUuid + ".json"), new BsonDocument().toJson());

        DemoLoadoutService loadoutService = new DemoLoadoutService(tempDir, null);

        assertFalse(loadoutService.hasStash(playerUuid));
    }

    @Test
    void restoreRemovesStashEvenWhenPlayerUnavailable(@TempDir Path tempDir) throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Path stashPath = tempDir.resolve(playerUuid + ".json");
        BsonDocument stash = new BsonDocument();
        stash.put("createdAt", new BsonString(Instant.now().toString()));
        stash.put("containers", new BsonDocument());
        Files.writeString(stashPath, stash.toJson());

        DemoLoadoutService loadoutService = new DemoLoadoutService(tempDir, null);
        loadoutService.restoreOriginalInventory(playerUuid, null, null, null);

        assertFalse(Files.exists(stashPath));
    }

    private static BsonDocument metadata(String value) {
        return new BsonDocument(DemoLoadoutService.METADATA_KEY, new BsonString(value));
    }

    private static ItemStack testStack(BsonDocument metadata) {
        return testStack(metadata, 1);
    }

    private static ItemStack testStack(BsonDocument metadata, int quantity) {
        return testStack("Test_Item", quantity, metadata);
    }

    private static ItemStack testStack(String itemId, int quantity, BsonDocument metadata) {
        return testStack(itemId, quantity, 0.0, 0.0, false, metadata);
    }

    private static ItemStack testStack(String itemId,
                                      int quantity,
                                      double durability,
                                      double maxDurability,
                                      boolean overrideDroppedItemAnimation,
                                      BsonDocument metadata) {
        return new TestItemStack(itemId, quantity, durability, maxDurability, overrideDroppedItemAnimation, metadata);
    }

    private static final class TestItemStack extends ItemStack {
        private TestItemStack(String itemId,
                              int quantity,
                              double durability,
                              double maxDurability,
                              boolean overrideDroppedItemAnimation,
                              BsonDocument metadata) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.durability = durability;
            this.maxDurability = maxDurability;
            this.overrideDroppedItemAnimation = overrideDroppedItemAnimation;
            this.metadata = metadata;
        }
    }
}
