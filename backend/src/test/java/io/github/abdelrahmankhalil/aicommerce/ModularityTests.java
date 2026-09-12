package io.github.abdelrahmankhalil.aicommerce;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(AiCommerceApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }
}
