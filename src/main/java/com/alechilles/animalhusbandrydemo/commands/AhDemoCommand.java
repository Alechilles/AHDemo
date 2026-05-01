package com.alechilles.animalhusbandrydemo.commands;

import com.alechilles.animalhusbandrydemo.runtime.DemoSessionService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import javax.annotation.Nonnull;

public final class AhDemoCommand extends AbstractPlayerCommand {
    private final DemoSessionService sessionService;

    public AhDemoCommand(@Nonnull DemoSessionService sessionService) {
        super("ahdemo", "Start, leave, reset, or inspect an Animal Husbandry demo instance.");
        this.sessionService = sessionService;
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            commandContext.sendMessage(Message.raw("Player context unavailable."));
            return;
        }

        String action = parseAction(commandContext);
        DemoSessionService.MessageSink sink = message -> commandContext.sendMessage(Message.raw(message));
        switch (action) {
            case "start" -> sessionService.startDemo(player, store, ref, playerRef, world, sink);
            case "leave" -> sessionService.leaveDemo(player, store, ref, playerRef, sink);
            case "reset", "restart" -> sessionService.resetDemo(player, store, ref, playerRef, world, sink);
            case "status" -> sessionService.sendStatus(player, sink);
            case "tutorial" -> sessionService.openTutorialGuide(player, store, ref, playerRef, sink);
            default -> commandContext.sendMessage(Message.raw("Usage: /ahdemo start | leave | reset | restart | status | tutorial"));
        }
    }

    @Nonnull
    private String parseAction(@Nonnull CommandContext commandContext) {
        String input = commandContext.getInputString();
        if (input == null || input.isBlank()) {
            return "status";
        }
        String[] tokens = input.trim().split("\\s+");
        for (String token : tokens) {
            String clean = token.startsWith("/") ? token.substring(1) : token;
            if (clean.equalsIgnoreCase("ahdemo")) {
                continue;
            }
            return clean.toLowerCase(Locale.ROOT);
        }
        return "status";
    }
}
