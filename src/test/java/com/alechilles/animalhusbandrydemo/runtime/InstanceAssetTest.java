package com.alechilles.animalhusbandrydemo.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InstanceAssetTest {
    private static final Pattern REGION_FILE = Pattern.compile("(-?\\d+)\\.(-?\\d+)\\.region\\.bin");

    @Test
    void demoInstanceAssetUsesComfortableVisualTime() throws IOException {
        Path asset = Path.of(
                "src",
                "main",
                "resources",
                "Server",
                "Instances",
                DemoSessionService.INSTANCE_ASSET,
                "instance.bson"
        );

        String json = Files.readString(asset);
        assertTrue(json.contains("\"GameTime\": \"0001-01-01T07:00:00Z\""));
        assertTrue(json.contains("\"IsGameTimePaused\": false"));
        assertFalse(json.contains("\"DaytimeDurationSeconds\""));
        assertFalse(json.contains("\"NighttimeDurationSeconds\""));
        assertTrue(json.contains("\"Type\": \"HytaleGenerator\""));
        assertTrue(json.contains("\"WorldStructure\": \"Default_Flat\""));
        assertFalse(json.contains("\"Type\": \"Void\""));
        assertTrue(json.contains("\"X\": -849.48"));
        assertTrue(json.contains("\"Y\": 123.45"));
        assertTrue(json.contains("\"Z\": 130.13"));
        assertTrue(json.contains("\"Yaw\": 0.0"));
        assertTrue(json.contains("\"Type\": \"WorldEmpty\""));
        assertTrue(json.contains("\"TimeoutSeconds\": 10.0"));
        assertTrue(json.contains("\"Type\": \"Timeout\""));
        assertTrue(json.contains("\"TimeoutSeconds\": 7200.0"));
        assertTrue(Files.exists(asset.getParent().resolve("chunks").resolve("-1.0.region.bin")));
    }

    @Test
    void demoInstanceKeepsCompleteCoreRegionsForLighting() throws IOException {
        Path chunks = Path.of(
                "src",
                "main",
                "resources",
                "Server",
                "Instances",
                DemoSessionService.INSTANCE_ASSET,
                "chunks"
        );
        List<Path> regions;
        try (var stream = Files.list(chunks)) {
            regions = stream
                    .filter(path -> REGION_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }

        assertEquals(4, regions.size());
        assertTrue(Files.exists(chunks.resolve("-1.-1.region.bin")));
        assertTrue(Files.exists(chunks.resolve("-1.0.region.bin")));
        assertTrue(Files.exists(chunks.resolve("-2.-1.region.bin")));
        assertTrue(Files.exists(chunks.resolve("-2.0.region.bin")));
        assertFalse(Files.exists(chunks.resolve("0.0.region.bin")));
        assertTrue(Files.size(chunks.resolve("-1.-1.region.bin")) > 20_000_000L);
        assertTrue(Files.size(chunks.resolve("-1.0.region.bin")) > 20_000_000L);
        assertTrue(Files.size(chunks.resolve("-2.-1.region.bin")) > 20_000_000L);
        assertTrue(Files.size(chunks.resolve("-2.0.region.bin")) > 20_000_000L);
    }

    @Test
    void manifestDoesNotHardDependOnAnimalHusbandryAssetPack() throws IOException {
        String json = Files.readString(Path.of("src", "main", "resources", "manifest.json"));

        assertTrue(json.contains("\"Hytale:Instances\": \"*\""));
        assertTrue(json.contains("\"Alechilles:Alec's Tamework!\": \"2.12.x\""));
        assertTrue(json.contains("\"ServerVersion\": \"0.5.x\""));
        assertFalse(json.contains("\"Alechilles:Alec's Animal Husbandry!\""));
    }
}
