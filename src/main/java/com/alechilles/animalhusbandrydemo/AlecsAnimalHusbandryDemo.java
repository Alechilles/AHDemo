package com.alechilles.animalhusbandrydemo;

import com.alechilles.animalhusbandrydemo.commands.AhDemoCommand;
import com.alechilles.animalhusbandrydemo.interactions.AhDemoGuideInteraction;
import com.alechilles.animalhusbandrydemo.interactions.AhDemoStartInteraction;
import com.alechilles.animalhusbandrydemo.runtime.DemoLoadoutService;
import com.alechilles.animalhusbandrydemo.runtime.DemoSessionService;
import com.alechilles.animalhusbandrydemo.runtime.DemoWorldSeeder;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AlecsAnimalHusbandryDemo extends JavaPlugin {
    private static AlecsAnimalHusbandryDemo instance;

    private DemoLoadoutService loadoutService;
    private DemoWorldSeeder worldSeeder;
    private DemoSessionService sessionService;

    public AlecsAnimalHusbandryDemo(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        loadoutService = new DemoLoadoutService(getDataDirectory().resolve("inventory-stashes"), getLogger());
        worldSeeder = new DemoWorldSeeder(getLogger());
        sessionService = new DemoSessionService(loadoutService, worldSeeder, getLogger());

        Interaction.CODEC.register(
                "AHDemoStart",
                AhDemoStartInteraction.class,
                AhDemoStartInteraction.CODEC
        );
        Interaction.CODEC.register(
                "AHDemoGuide",
                AhDemoGuideInteraction.class,
                AhDemoGuideInteraction.CODEC
        );

        if (getCommandRegistry() != null) {
            getCommandRegistry().registerCommand(new AhDemoCommand(sessionService));
        }
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    @Override
    protected void start() {
        if (sessionService != null) {
            sessionService.startMaintenance();
        }
        getLogger().at(Level.INFO).log("Alec's Animal Husbandry Demo enabled.");
    }

    private void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        if (sessionService != null && event.getPlayerRef() != null && event.getWorld() != null) {
            sessionService.scheduleAutoStart(event.getPlayerRef(), event.getWorld());
        }
    }

    @Override
    protected void shutdown() {
        if (sessionService != null) {
            sessionService.shutdown();
        }
        getLogger().at(Level.INFO).log("Alec's Animal Husbandry Demo disabled.");
        sessionService = null;
        worldSeeder = null;
        loadoutService = null;
        instance = null;
    }

    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        if (sessionService != null && event.getPlayerRef() != null) {
            sessionService.cleanupDisconnect(event.getPlayerRef());
        }
    }

    @Nullable
    public static AlecsAnimalHusbandryDemo getInstance() {
        return instance;
    }

    @Nullable
    public DemoSessionService getSessionService() {
        return sessionService;
    }
}
