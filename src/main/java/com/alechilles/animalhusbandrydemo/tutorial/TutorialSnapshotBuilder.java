package com.alechilles.animalhusbandrydemo.tutorial;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TutorialSnapshotBuilder {
    private final Map<UUID, NpcCareState> previousCareState = new HashMap<>();

    @Nonnull
    public TutorialSnapshot build(@Nonnull Iterable<NpcObservation> observations) {
        int tamedLivestock = 0;
        int tamedPredators = 0;
        boolean commandLinked = false;
        boolean careInteracted = false;
        boolean breedingTriggered = false;
        boolean offspringOrGrowth = false;
        Map<UUID, NpcCareState> nextCareState = new HashMap<>();

        for (NpcObservation observation : observations) {
            if (!observation.ownedAndTamed()) {
                continue;
            }
            if (isLivestock(observation.roleId())) {
                tamedLivestock++;
            }
            if (isPredator(observation.roleId())) {
                tamedPredators++;
            }
            commandLinked |= observation.commandLinked();
            breedingTriggered |= observation.breedingTriggered();
            offspringOrGrowth |= observation.offspringOrGrowth();

            NpcCareState currentCare = new NpcCareState(
                    observation.hunger(),
                    observation.thirst(),
                    observation.happiness()
            );
            if (observation.npcUuid() != null) {
                NpcCareState previous = previousCareState.get(observation.npcUuid());
                careInteracted |= currentCare.improvedOver(previous);
                nextCareState.put(observation.npcUuid(), currentCare);
            }
        }

        previousCareState.clear();
        previousCareState.putAll(nextCareState);
        return new TutorialSnapshot(
                tamedLivestock,
                tamedPredators,
                commandLinked,
                false,
                careInteracted,
                breedingTriggered,
                offspringOrGrowth
        );
    }

    public void reset() {
        previousCareState.clear();
    }

    static boolean isLivestock(@Nullable String roleId) {
        String normalized = normalizeRole(roleId);
        return normalized.contains("cow")
                || normalized.contains("chicken")
                || normalized.contains("tetrabird")
                || normalized.contains("turkey")
                || normalized.contains("bison")
                || normalized.contains("sheep");
    }

    static boolean isPredator(@Nullable String roleId) {
        String normalized = normalizeRole(roleId);
        return normalized.contains("fox")
                || normalized.contains("wolf")
                || normalized.contains("bear")
                || normalized.contains("raptor")
                || normalized.contains("rex");
    }

    @Nonnull
    private static String normalizeRole(@Nullable String roleId) {
        if (roleId == null) {
            return "";
        }
        String lower = roleId.toLowerCase(Locale.ROOT);
        return lower.startsWith("tamed_") ? lower.substring("tamed_".length()) : lower;
    }

    public record NpcObservation(@Nullable UUID npcUuid,
                                 @Nullable String roleId,
                                 boolean ownedAndTamed,
                                 boolean commandLinked,
                                 @Nullable Double hunger,
                                 @Nullable Double thirst,
                                 @Nullable Double happiness,
                                 boolean breedingTriggered,
                                 boolean offspringOrGrowth) {
    }

    private record NpcCareState(@Nullable Double hunger, @Nullable Double thirst, @Nullable Double happiness) {
        private static final double IMPROVEMENT_EPSILON = 0.001;

        boolean improvedOver(@Nullable NpcCareState previous) {
            if (previous == null) {
                return false;
            }
            return improved(hunger, previous.hunger)
                    || improved(thirst, previous.thirst)
                    || improved(happiness, previous.happiness);
        }

        private static boolean improved(@Nullable Double current, @Nullable Double previous) {
            return current != null && previous != null && current > previous + IMPROVEMENT_EPSILON;
        }
    }
}
