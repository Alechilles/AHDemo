package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DemoLoadoutService {
    public static final String METADATA_KEY = "AlecsAnimalHusbandryDemo.Grant";
    public static final String METADATA_VALUE = "v1";

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

    public void grant(@Nonnull Player player,
                      @Nonnull Ref<EntityStore> playerRef,
                      @Nonnull ComponentAccessor<EntityStore> accessor) {
        clear(player);
        for (LoadoutItem item : LOADOUT) {
            ItemStackTransaction transaction = player.giveItem(createDemoStack(item.itemId(), item.quantity()), playerRef, accessor);
            if (transaction != null && transaction.getRemainder() != null && !transaction.getRemainder().isEmpty()) {
                player.giveItem(transaction.getRemainder(), playerRef, accessor);
            }
        }
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
        return removed;
    }

    private int clearContainer(@Nullable ItemContainer container) {
        if (container == null) {
            return 0;
        }
        List<Short> slots = new ArrayList<>();
        container.forEach((slot, stack) -> {
            if (shouldRemove(stack)) {
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

    private record LoadoutItem(String itemId, int quantity) {
    }
}
