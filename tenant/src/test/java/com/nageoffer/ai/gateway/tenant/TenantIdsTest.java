package com.nageoffer.ai.gateway.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantIdsTest {

    @Test
    void requireRejectsBlank() {
        assertThrows(MissingTenantException.class, () -> TenantIds.require(" "));
        assertThrows(MissingTenantException.class, () -> TenantIds.require(null));
    }

    @Test
    void requireReturnsTrimmed() {
        assertEquals("t1", TenantIds.require(" t1 "));
    }
}
