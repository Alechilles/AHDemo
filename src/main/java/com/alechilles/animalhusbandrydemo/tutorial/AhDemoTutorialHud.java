package com.alechilles.animalhusbandrydemo.tutorial;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

final class AhDemoTutorialHud extends CustomUIHud {
    static final String UI_PATH = "AhDemoTutorialHud.ui";
    private TutorialViewModel viewModel;

    AhDemoTutorialHud(@Nonnull PlayerRef playerRef, @Nonnull TutorialViewModel viewModel) {
        super(playerRef);
        this.viewModel = viewModel;
    }

    void refresh(@Nonnull TutorialViewModel nextViewModel) {
        this.viewModel = nextViewModel;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        render(commandBuilder);
        update(false, commandBuilder);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append(UI_PATH);
        render(commandBuilder);
    }

    private void render(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#AhDemoTutorialHudRoot.Visible", !viewModel.hidden());
        commandBuilder.set("#AhDemoTutorialHudTitle.Text", viewModel.title());
        commandBuilder.set("#AhDemoTutorialHudProgress.Text", viewModel.progress());
        commandBuilder.set("#AhDemoTutorialHudInstruction.Text", viewModel.instruction());
        commandBuilder.set("#AhDemoTutorialHudTask0.Text", rowText(0));
        commandBuilder.set("#AhDemoTutorialHudTask1.Text", rowText(1));
        commandBuilder.set("#AhDemoTutorialHudTask2.Text", rowText(2));
        commandBuilder.set("#AhDemoTutorialHudHint.Text", "Open Guide: /ahdemo tutorial");
    }

    @Nonnull
    private String rowText(int index) {
        return index < viewModel.rows().size() ? viewModel.rows().get(index).displayText() : "";
    }
}
