package com.alechilles.animalhusbandrydemo.interactions;

import com.alechilles.animalhusbandrydemo.AlecsAnimalHusbandryDemo;
import com.alechilles.animalhusbandrydemo.runtime.DemoSessionService;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class AhDemoStartInteraction extends SimpleInteraction {
    public static final BuilderCodec<AhDemoStartInteraction> CODEC = BuilderCodec.builder(
            AhDemoStartInteraction.class,
            AhDemoStartInteraction::new,
            SimpleInteraction.CODEC
    )
            .documentation("Starts a private Alec's Animal Husbandry demo instance for the interacting player.")
            .build();

    protected AhDemoStartInteraction() {
        super();
    }

    public AhDemoStartInteraction(String id) {
        super(id);
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void tick0(boolean firstRun,
                         float time,
                         @Nonnull InteractionType type,
                         @Nonnull InteractionContext context,
                         @Nonnull CooldownHandler cooldownHandler) {
        if (!firstRun) {
            super.tick0(false, time, type, context, cooldownHandler);
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> playerRef = context.getEntity();
        if (commandBuffer == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            super.tick0(true, time, type, context, cooldownHandler);
            return;
        }

        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
        AlecsAnimalHusbandryDemo plugin = AlecsAnimalHusbandryDemo.getInstance();
        DemoSessionService sessionService = plugin != null ? plugin.getSessionService() : null;
        if (player == null || player.getPlayerRef() == null || sessionService == null) {
            context.getState().state = InteractionState.Failed;
            super.tick0(true, time, type, context, cooldownHandler);
            return;
        }

        commandBuffer.run(store -> {
            World world = store.getExternalData().getWorld();
            sessionService.startDemo(
                    player,
                    store,
                    playerRef,
                    player.getPlayerRef(),
                    world,
                    message -> player.sendMessage(Message.raw(message))
            );
        });

        super.tick0(true, time, type, context, cooldownHandler);
    }

    @Override
    protected void simulateTick0(boolean firstRun,
                                 float time,
                                 @Nonnull InteractionType type,
                                 @Nonnull InteractionContext context,
                                 @Nonnull CooldownHandler cooldownHandler) {
        if (context.getServerState() != null && context.getServerState().state == InteractionState.Failed) {
            context.getState().state = InteractionState.Failed;
        }
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }
}
