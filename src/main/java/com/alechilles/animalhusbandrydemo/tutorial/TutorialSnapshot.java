package com.alechilles.animalhusbandrydemo.tutorial;

public record TutorialSnapshot(
        int tamedLivestock,
        int tamedPredators,
        boolean commandLinked,
        boolean commandTried,
        boolean careInteracted,
        boolean breedingTriggered,
        boolean offspringOrGrowth
) {
    public static TutorialSnapshot empty() {
        return new TutorialSnapshot(0, 0, false, false, false, false, false);
    }

    public boolean hasLivestockPair() {
        return tamedLivestock >= 2;
    }
}
