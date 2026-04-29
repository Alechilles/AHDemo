package com.alechilles.animalhusbandrydemo.tutorial;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class AhDemoTutorialPage extends InteractiveCustomUIPage<AhDemoTutorialPage.EventPayload> {
    public static final String UI_PATH = "AhDemoTutorialPage.ui";

    private static final String KEY_ACTION = "Action";
    private static final String ACTION_PREVIOUS = "Previous";
    private static final String ACTION_NEXT = "Next";
    private static final String ACTION_RESTART = "Restart";
    private static final String ACTION_TOGGLE_HUD = "ToggleHud";
    private static final String ACTION_CLOSE = "Close";

    private final AhDemoTutorialService tutorialService;
    private final UUID playerUuid;

    public AhDemoTutorialPage(@Nonnull PlayerRef playerRef,
                              @Nonnull AhDemoTutorialService tutorialService,
                              @Nonnull UUID playerUuid) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
        this.tutorialService = tutorialService;
        this.playerUuid = playerUuid;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        render(commandBuilder);
        bindEvents(eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull EventPayload data) {
        if (ACTION_CLOSE.equals(data.action)) {
            close();
            return;
        }
        tutorialService.handleGuideAction(playerUuid, data.action);
        refreshUi();
    }

    private void refreshUi() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        render(commandBuilder);
        bindEvents(eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void render(@Nonnull UICommandBuilder commandBuilder) {
        TutorialViewModel viewModel = tutorialService.viewModel(playerUuid);
        commandBuilder.set("#AhDemoTutorialPageTitle.Text", viewModel.title());
        commandBuilder.set("#AhDemoTutorialPageProgress.Text", viewModel.progress());
        commandBuilder.set("#AhDemoTutorialPageInstruction.Text", viewModel.instruction());
        commandBuilder.set("#AhDemoTutorialPageTask0.Text", rowText(viewModel, 0));
        commandBuilder.set("#AhDemoTutorialPageTask1.Text", rowText(viewModel, 1));
        commandBuilder.set("#AhDemoTutorialPageTask2.Text", rowText(viewModel, 2));
        commandBuilder.set("#AhDemoTutorialPagePreviousButton.Visible", !viewModel.firstModule());
        commandBuilder.set("#AhDemoTutorialPageNextButton.Text", viewModel.lastModule() ? "Finish" : "Next");
        commandBuilder.set("#AhDemoTutorialPageHudButton.Text", viewModel.hidden() ? "Show HUD" : "Hide HUD");
    }

    private void bindEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AhDemoTutorialPagePreviousButton",
                EventData.of(KEY_ACTION, ACTION_PREVIOUS),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AhDemoTutorialPageNextButton",
                EventData.of(KEY_ACTION, ACTION_NEXT),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AhDemoTutorialPageRestartButton",
                EventData.of(KEY_ACTION, ACTION_RESTART),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AhDemoTutorialPageHudButton",
                EventData.of(KEY_ACTION, ACTION_TOGGLE_HUD),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AhDemoTutorialPageCloseButton",
                EventData.of(KEY_ACTION, ACTION_CLOSE),
                false
        );
    }

    @Nonnull
    private static String rowText(@Nonnull TutorialViewModel viewModel, int index) {
        return index < viewModel.rows().size() ? viewModel.rows().get(index).displayText() : "";
    }

    public static final class EventPayload {
        static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .<String>append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (x, v) -> x.action = v, x -> x.action)
                .add()
                .build();

        private String action;
    }
}
