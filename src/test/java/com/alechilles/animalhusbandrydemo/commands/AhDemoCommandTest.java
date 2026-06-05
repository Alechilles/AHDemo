package com.alechilles.animalhusbandrydemo.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AhDemoCommandTest {

    @Test
    void permissionDoesNotUseManifestDisplayName() {
        AhDemoCommand command = new AhDemoCommand(null);

        assertEquals(AhDemoCommand.PERMISSION, command.getPermission());
    }
}
