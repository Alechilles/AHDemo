package com.alechilles.animalhusbandrydemo.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class DemoWorldSeeder {
    private final HytaleLogger logger;
    private final Set<UUID> seededWorlds = ConcurrentHashMap.newKeySet();

    public DemoWorldSeeder(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    public void seed(@Nonnull World world) {
        UUID worldUuid = world.getWorldConfig().getUuid();
        if (worldUuid != null && !seededWorlds.add(worldUuid)) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            logger.at(Level.WARNING).log("Animal Husbandry demo seeding skipped in %s: NPCPlugin unavailable", world.getName());
            return;
        }

        spawnRole(store, npcPlugin, "Cow", 4, -856.0, 134.0);
        spawnRole(store, npcPlugin, "Chicken", 5, -850.0, 134.0);
        spawnRole(store, npcPlugin, "Sheep", 3, -856.0, 140.0);
        spawnRole(store, npcPlugin, "Fox", 2, -850.0, 140.0);

        logger.at(Level.INFO).log("Seeded Animal Husbandry demo world %s", world.getName());
    }

    private void spawnRole(@Nonnull Store<EntityStore> store,
                           @Nonnull NPCPlugin npcPlugin,
                           @Nonnull String roleId,
                           int count,
                           double baseX,
                           double baseZ) {
        int roleIndex = npcPlugin.getIndex(roleId);
        if (roleIndex < 0) {
            logger.at(Level.WARNING).log("Animal Husbandry demo role '%s' is not loaded; skipping.", roleId);
            return;
        }
        for (int i = 0; i < count; i++) {
            double x = baseX + (i % 3) * 1.8;
            double z = baseZ + (i / 3) * 1.8;
            Vector3d position = new Vector3d(x + 0.5, 80.0, z + 0.5);
            Vector3f rotation = new Vector3f(0.0f, 180.0f, 0.0f);
            Pair<Ref<EntityStore>, NPCEntity> spawned = npcPlugin.spawnEntity(store, roleIndex, position, rotation, null, null);
            if (spawned == null || spawned.first() == null) {
                logger.at(Level.WARNING).log("Failed to spawn demo role '%s'.", roleId);
                return;
            }
        }
    }
}
