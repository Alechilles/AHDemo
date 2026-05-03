package com.alechilles.animalhusbandrydemo.runtime;

import com.alechilles.alecstamework.api.TameworkProgressionTimeScales;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression coverage for demo-world-only progression acceleration. */
final class DemoProgressionTimeScaleTest {

    @Test
    void registerDemoProgressionTimingUsesDemoScaleForOnlyThatWorld() {
        UUID demoWorldUuid = UUID.randomUUID();
        UUID normalWorldUuid = UUID.randomUUID();
        DemoSessionService.clearDemoProgressionTiming(demoWorldUuid);
        DemoSessionService.clearDemoProgressionTiming(normalWorldUuid);

        DemoSessionService.registerDemoProgressionTiming(demoWorldUuid);

        assertEquals(DemoSessionService.DEMO_PROGRESSION_TIME_SCALE,
                TameworkProgressionTimeScales.getWorldScale(demoWorldUuid));
        assertEquals(1.0, TameworkProgressionTimeScales.getWorldScale(normalWorldUuid));

        DemoSessionService.clearDemoProgressionTiming(demoWorldUuid);
    }

    @Test
    void clearDemoProgressionTimingRestoresDefaultScale() {
        UUID demoWorldUuid = UUID.randomUUID();
        DemoSessionService.registerDemoProgressionTiming(demoWorldUuid);

        DemoSessionService.clearDemoProgressionTiming(demoWorldUuid);

        assertEquals(1.0, TameworkProgressionTimeScales.getWorldScale(demoWorldUuid));
    }
}
