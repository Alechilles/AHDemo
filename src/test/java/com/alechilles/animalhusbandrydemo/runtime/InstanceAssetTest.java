package com.alechilles.animalhusbandrydemo.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InstanceAssetTest {
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
        assertTrue(json.contains("\"IsGameTimePaused\": false"));
        assertFalse(json.contains("\"DaytimeDurationSeconds\""));
        assertFalse(json.contains("\"NighttimeDurationSeconds\""));
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
    void manifestDoesNotHardDependOnAnimalHusbandryAssetPack() throws IOException {
        String json = Files.readString(Path.of("src", "main", "resources", "manifest.json"));

        assertTrue(json.contains("\"Hytale:Instances\": \"*\""));
        assertTrue(json.contains("\"Alechilles:Alec's Tamework!\": \"2.9.x\""));
        assertFalse(json.contains("\"Alechilles:Alec's Animal Husbandry!\""));
    }
}
