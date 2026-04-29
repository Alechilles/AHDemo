package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

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

    private static BsonDocument metadata(String value) {
        return new BsonDocument(DemoLoadoutService.METADATA_KEY, new BsonString(value));
    }

    private static ItemStack testStack(BsonDocument metadata) {
        return testStack(metadata, 1);
    }

    private static ItemStack testStack(BsonDocument metadata, int quantity) {
        return new TestItemStack("Test_Item", quantity, metadata);
    }

    private static final class TestItemStack extends ItemStack {
        private TestItemStack(String itemId, int quantity, BsonDocument metadata) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.metadata = metadata;
        }
    }
}
