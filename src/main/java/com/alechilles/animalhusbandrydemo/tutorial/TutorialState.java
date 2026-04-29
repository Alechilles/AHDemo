package com.alechilles.animalhusbandrydemo.tutorial;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

public final class TutorialState {
    private static final Duration INTRO_DWELL = Duration.ofSeconds(5);

    private final EnumSet<TutorialTaskId> completedTasks = EnumSet.noneOf(TutorialTaskId.class);
    private int moduleIndex;
    private boolean hudHidden;
    private Instant moduleStartedAt;
    private boolean dirty = true;

    public TutorialState(@Nonnull Instant now) {
        this.moduleIndex = 0;
        this.moduleStartedAt = now;
    }

    public boolean update(@Nonnull TutorialSnapshot snapshot, @Nonnull Instant now) {
        boolean changed = false;
        if (Duration.between(moduleStartedAt, now).compareTo(INTRO_DWELL) >= 0) {
            changed |= complete(TutorialTaskId.INTRO_READY);
        }
        if (snapshot.tamedLivestock() > 0) {
            changed |= complete(TutorialTaskId.TAME_LIVESTOCK);
        }
        if (snapshot.commandLinked()) {
            changed |= complete(TutorialTaskId.COMMAND_LINK);
            changed |= complete(TutorialTaskId.COMMAND_TRY);
        }
        if (snapshot.commandTried()) {
            changed |= complete(TutorialTaskId.COMMAND_TRY);
        }
        if (snapshot.careInteracted()) {
            changed |= complete(TutorialTaskId.CARE_INTERACT);
        }
        if (snapshot.hasLivestockPair()) {
            changed |= complete(TutorialTaskId.TAME_PAIR);
        }
        if (snapshot.breedingTriggered()) {
            changed |= complete(TutorialTaskId.BREEDING_TRIGGERED);
        }
        if (snapshot.offspringOrGrowth()) {
            changed |= complete(TutorialTaskId.OFFSPRING_OR_GROWTH);
        }
        if (snapshot.tamedPredators() > 0) {
            changed |= complete(TutorialTaskId.TAME_PREDATOR);
        }
        if (currentModule() == TutorialModule.FINISH) {
            changed |= complete(TutorialTaskId.FINISH_READY);
        }
        if (autoAdvance(now)) {
            changed = true;
        }
        dirty |= changed;
        return changed;
    }

    public void next(@Nonnull Instant now) {
        if (moduleIndex < TutorialModule.values().length - 1) {
            moduleIndex++;
            moduleStartedAt = now;
            dirty = true;
        }
    }

    public void previous(@Nonnull Instant now) {
        if (moduleIndex > 0) {
            moduleIndex--;
            moduleStartedAt = now;
            dirty = true;
        }
    }

    public void restartModule(@Nonnull Instant now) {
        for (TutorialTask task : currentModule().tasks()) {
            completedTasks.remove(task.id());
        }
        moduleStartedAt = now;
        dirty = true;
    }

    public void setHudHidden(boolean hidden) {
        if (hudHidden != hidden) {
            hudHidden = hidden;
            dirty = true;
        }
    }

    public boolean isHudHidden() {
        return hudHidden;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    @Nonnull
    public TutorialModule currentModule() {
        return TutorialModule.values()[moduleIndex];
    }

    public int getModuleIndex() {
        return moduleIndex;
    }

    @Nonnull
    public Set<TutorialTaskId> completedTasks() {
        return EnumSet.copyOf(completedTasks);
    }

    @Nonnull
    public TutorialViewModel viewModel() {
        TutorialModule module = currentModule();
        List<TutorialViewModel.TaskRow> rows = module.tasks().stream()
                .map(task -> new TutorialViewModel.TaskRow(task.label(), completedTasks.contains(task.id())))
                .toList();
        return new TutorialViewModel(
                module.title(),
                module.instruction(),
                "Step " + (moduleIndex + 1) + " of " + TutorialModule.values().length,
                rows,
                hudHidden,
                moduleIndex == 0,
                moduleIndex == TutorialModule.values().length - 1
        );
    }

    private boolean autoAdvance(@Nonnull Instant now) {
        if (moduleIndex >= TutorialModule.values().length - 1 || !isCurrentModuleComplete()) {
            return false;
        }
        moduleIndex++;
        moduleStartedAt = now;
        return true;
    }

    private boolean isCurrentModuleComplete() {
        for (TutorialTask task : currentModule().tasks()) {
            if (!completedTasks.contains(task.id())) {
                return false;
            }
        }
        return true;
    }

    private boolean complete(@Nonnull TutorialTaskId taskId) {
        return completedTasks.add(taskId);
    }
}
