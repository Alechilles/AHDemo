package com.alechilles.animalhusbandrydemo.tutorial;

import javax.annotation.Nonnull;

public record TutorialTask(@Nonnull TutorialTaskId id, @Nonnull String label) {
}
