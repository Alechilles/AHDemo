package com.alechilles.animalhusbandrydemo.tutorial;

import java.util.List;
import javax.annotation.Nonnull;

public record TutorialViewModel(
        @Nonnull String title,
        @Nonnull String instruction,
        @Nonnull String progress,
        @Nonnull List<TaskRow> rows,
        boolean hidden,
        boolean firstModule,
        boolean lastModule
) {
    public record TaskRow(@Nonnull String label, boolean complete) {
        @Nonnull
        public String displayText() {
            return (complete ? "[x] " : "[ ] ") + label;
        }
    }
}
