package com.alechilles.animalhusbandrydemo.tutorial;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TutorialSnapshotBuilderTest {
    @Test
    void detectsLivestockPredatorsLinksBreedingAndGrowth() {
        TutorialSnapshotBuilder builder = new TutorialSnapshotBuilder();

        TutorialSnapshot snapshot = builder.build(List.of(
                observation("Cow", true, true, 50.0, 50.0, true, false),
                observation("Tamed_Fox", true, false, 25.0, 25.0, false, true)
        ));

        assertTrue(snapshot.tamedLivestock() == 1);
        assertTrue(snapshot.tamedPredators() == 1);
        assertTrue(snapshot.commandLinked());
        assertFalse(snapshot.commandTried());
        assertTrue(snapshot.breedingTriggered());
        assertTrue(snapshot.offspringOrGrowth());
    }

    @Test
    void detectsCareOnlyAfterTrackedValuesImprove() {
        UUID npcUuid = UUID.randomUUID();
        TutorialSnapshotBuilder builder = new TutorialSnapshotBuilder();

        TutorialSnapshot first = builder.build(List.of(new TutorialSnapshotBuilder.NpcObservation(
                npcUuid,
                "Cow",
                true,
                false,
                25.0,
                20.0,
                Set.of(),
                false,
                false
        )));
        TutorialSnapshot second = builder.build(List.of(new TutorialSnapshotBuilder.NpcObservation(
                npcUuid,
                "Cow",
                true,
                false,
                30.0,
                20.0,
                Set.of(),
                false,
                false
        )));

        assertFalse(first.careInteracted());
        assertTrue(second.careInteracted());
    }

    @Test
    void doesNotDetectCareFromStableNeeds() {
        UUID npcUuid = UUID.randomUUID();
        TutorialSnapshotBuilder builder = new TutorialSnapshotBuilder();

        TutorialSnapshot first = builder.build(List.of(new TutorialSnapshotBuilder.NpcObservation(
                npcUuid,
                "Cow",
                true,
                false,
                25.0,
                20.0,
                Set.of(),
                false,
                false
        )));
        TutorialSnapshot second = builder.build(List.of(new TutorialSnapshotBuilder.NpcObservation(
                npcUuid,
                "Cow",
                true,
                false,
                25.0,
                20.0,
                Set.of(),
                false,
                false
        )));

        assertFalse(first.careInteracted());
        assertFalse(second.careInteracted());
    }

    @Test
    void detectsCareFromNewFeedImpulse() {
        UUID npcUuid = UUID.randomUUID();
        TutorialSnapshotBuilder builder = new TutorialSnapshotBuilder();

        TutorialSnapshot first = builder.build(List.of(new TutorialSnapshotBuilder.NpcObservation(
                npcUuid,
                "Cow",
                true,
                false,
                100.0,
                100.0,
                Set.of(),
                false,
                false
        )));
        TutorialSnapshot second = builder.build(List.of(new TutorialSnapshotBuilder.NpcObservation(
                npcUuid,
                "Cow",
                true,
                false,
                100.0,
                100.0,
                Set.of("feed:hand|12345|null"),
                false,
                false
        )));

        assertFalse(first.careInteracted());
        assertTrue(second.careInteracted());
    }

    @Test
    void carriesExternalCommandTriedSignal() {
        TutorialSnapshotBuilder builder = new TutorialSnapshotBuilder();

        TutorialSnapshot snapshot = builder.build(List.of(), true);

        assertTrue(snapshot.commandTried());
    }

    private static TutorialSnapshotBuilder.NpcObservation observation(String roleId,
                                                                      boolean ownedAndTamed,
                                                                      boolean commandLinked,
                                                                      double hunger,
                                                                      double thirst,
                                                                      boolean breeding,
                                                                      boolean growth) {
        return new TutorialSnapshotBuilder.NpcObservation(
                UUID.randomUUID(),
                roleId,
                ownedAndTamed,
                commandLinked,
                hunger,
                thirst,
                Set.of(),
                breeding,
                growth
        );
    }
}
