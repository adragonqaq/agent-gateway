package com.nageoffer.ai.gateway.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SecretBoxTest {

    @Test
    void roundTrip() {
        SecretBox box = new SecretBox("0123456789abcdef".getBytes());
        String cipher = box.encrypt("{\"Authorization\":\"Bearer x\"}");
        assertNotEquals("{\"Authorization\":\"Bearer x\"}", cipher);
        assertEquals("{\"Authorization\":\"Bearer x\"}", box.decrypt(cipher));
    }
}
