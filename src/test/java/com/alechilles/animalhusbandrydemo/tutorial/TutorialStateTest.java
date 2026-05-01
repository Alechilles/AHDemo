package com.alechilles.animalhusbandrydemo.tutorial;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TutorialStateTest {
    @Test
    void autoAdvancesWhenCurrentModuleTasksComplete() {
        Instant start = Instant.parse("2026-04-29T00:00:00Z");
        TutorialState state = new TutorialState(start);

        state.update(TutorialSnapshot.empty(), start.plusSeconds(6));
        assertEquals(TutorialModule.TAME_LIVESTOCK, state.currentModule());

        state.update(new TutorialSnapshot(1, 0, false, false, false, false, false), start.plusSeconds(7));
        assertEquals(TutorialModule.COMMAND_TOOLS, state.currentModule());
    }

    @Test
    void resetCurrentModuleTimerDelaysIntroAutoAdvance() {
        Instant start = Instant.parse("2026-04-29T00:00:00Z");
        TutorialState state = new TutorialState(start);

        state.resetCurrentModuleTimer(start.plusSeconds(10));
        state.update(TutorialSnapshot.empty(), start.plusSeconds(14));
        assertEquals(TutorialModule.INTRO, state.currentModule());

        state.update(TutorialSnapshot.empty(), start.plusSeconds(16));
        assertEquals(TutorialModule.TAME_LIVESTOCK, state.currentModule());
    }

    @Test
    void manualNavigationAndHudVisibilityAreTracked() {
        Instant start = Instant.parse("2026-04-29T00:00:00Z");
        TutorialState state = new TutorialState(start);

        state.next(start.plusSeconds(1));
        assertEquals(TutorialModule.TAME_LIVESTOCK, state.currentModule());

        state.previous(start.plusSeconds(2));
        assertEquals(TutorialModule.INTRO, state.currentModule());

        assertFalse(state.isHudHidden());
        state.setHudHidden(true);
        assertTrue(state.isHudHidden());
        assertTrue(state.viewModel().hidden());
    }

    @Test
    void restartModuleClearsOnlyCurrentModuleTasks() {
        Instant start = Instant.parse("2026-04-29T00:00:00Z");
        TutorialState state = new TutorialState(start);

        state.update(TutorialSnapshot.empty(), start.plusSeconds(6));
        state.update(new TutorialSnapshot(1, 0, false, false, false, false, false), start.plusSeconds(7));
        state.next(start.plusSeconds(8));
        assertTrue(state.completedTasks().contains(TutorialTaskId.INTRO_READY));
        assertTrue(state.completedTasks().contains(TutorialTaskId.TAME_LIVESTOCK));
        assertEquals(TutorialModule.CARE, state.currentModule());

        state.restartModule(start.plusSeconds(9));
        assertTrue(state.completedTasks().contains(TutorialTaskId.INTRO_READY));
        assertTrue(state.completedTasks().contains(TutorialTaskId.TAME_LIVESTOCK));
        assertFalse(state.completedTasks().contains(TutorialTaskId.CARE_INTERACT));
    }

    @Test
    void ignoresFutureModuleSignalsUntilThatModuleIsCurrent() {
        Instant start = Instant.parse("2026-04-29T00:00:00Z");
        TutorialState state = new TutorialState(start);
        TutorialSnapshot allSignals = new TutorialSnapshot(2, 1, true, true, true, true, true);

        state.update(allSignals, start.plusSeconds(6));
        assertEquals(TutorialModule.TAME_LIVESTOCK, state.currentModule());
        assertFalse(state.completedTasks().contains(TutorialTaskId.COMMAND_LINK));
        assertFalse(state.completedTasks().contains(TutorialTaskId.CARE_INTERACT));

        state.update(allSignals, start.plusSeconds(7));
        assertEquals(TutorialModule.COMMAND_TOOLS, state.currentModule());
        assertFalse(state.completedTasks().contains(TutorialTaskId.CARE_INTERACT));
    }

    @Test
    void commandLinkAloneDoesNotCompleteCommandTryTask() {
        Instant start = Instant.parse("2026-04-29T00:00:00Z");
        TutorialState state = new TutorialState(start);

        state.update(TutorialSnapshot.empty(), start.plusSeconds(6));
        state.update(new TutorialSnapshot(1, 0, false, false, false, false, false), start.plusSeconds(7));
        state.update(new TutorialSnapshot(1, 0, true, false, false, false, false), start.plusSeconds(8));

        assertEquals(TutorialModule.COMMAND_TOOLS, state.currentModule());
        assertTrue(state.completedTasks().contains(TutorialTaskId.COMMAND_LINK));
        assertFalse(state.completedTasks().contains(TutorialTaskId.COMMAND_TRY));
    }
}
