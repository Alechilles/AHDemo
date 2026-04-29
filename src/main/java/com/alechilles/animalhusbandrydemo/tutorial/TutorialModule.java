package com.alechilles.animalhusbandrydemo.tutorial;

import java.util.List;
import javax.annotation.Nonnull;

public enum TutorialModule {
    INTRO(
            "Welcome",
            "Take a look around the private farm. This guide will follow your progress.",
            List.of(new TutorialTask(TutorialTaskId.INTRO_READY, "Arrive in your private demo farm."))
    ),
    TAME_LIVESTOCK(
            "Tame Livestock",
            "Use preferred food on a cow, chicken, or tetrabird until it becomes yours.",
            List.of(new TutorialTask(TutorialTaskId.TAME_LIVESTOCK, "Tame one livestock animal."))
    ),
    COMMAND_TOOLS(
            "Command Tools",
            "Use a command item or flute on a tamed animal, then try a basic command.",
            List.of(
                    new TutorialTask(TutorialTaskId.COMMAND_LINK, "Link a tamed animal to a command tool."),
                    new TutorialTask(TutorialTaskId.COMMAND_TRY, "Try a command from the command menu.")
            )
    ),
    CARE(
            "Care",
            "Feed or water one of your tamed animals directly or through a trough.",
            List.of(new TutorialTask(TutorialTaskId.CARE_INTERACT, "Feed or water a tamed animal."))
    ),
    BREEDING(
            "Breeding",
            "Tame a compatible pair, feed them, and watch for cooldown, offspring, or growth.",
            List.of(
                    new TutorialTask(TutorialTaskId.TAME_PAIR, "Own two compatible livestock animals."),
                    new TutorialTask(TutorialTaskId.BREEDING_TRIGGERED, "Trigger breeding or a breeding cooldown."),
                    new TutorialTask(TutorialTaskId.OFFSPRING_OR_GROWTH, "Observe offspring or growth progress.")
            )
    ),
    PREDATOR_TAMING(
            "Predator Taming",
            "Use tranquilizer gear and food to tame a fox or another predator.",
            List.of(new TutorialTask(TutorialTaskId.TAME_PREDATOR, "Tame one predator."))
    ),
    FINISH(
            "Free Play",
            "You have seen the core flows. Keep experimenting, reset, or leave the demo.",
            List.of(new TutorialTask(TutorialTaskId.FINISH_READY, "Continue free play or leave when ready."))
    );

    private final String title;
    private final String instruction;
    private final List<TutorialTask> tasks;

    TutorialModule(@Nonnull String title, @Nonnull String instruction, @Nonnull List<TutorialTask> tasks) {
        this.title = title;
        this.instruction = instruction;
        this.tasks = List.copyOf(tasks);
    }

    @Nonnull
    public String title() {
        return title;
    }

    @Nonnull
    public String instruction() {
        return instruction;
    }

    @Nonnull
    public List<TutorialTask> tasks() {
        return tasks;
    }
}
