package com.nageoffer.ai.gateway.tenant;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantResolverTest {

    @Test
    void unknownKeyThrows() {
        TenantRepository repo = mock(TenantRepository.class);
        when(repo.findActiveByKeyHash(anyString())).thenReturn(Optional.empty());
        TenantResolver resolver = new TenantResolver(repo);
        assertThrows(UnauthorizedException.class, () -> resolver.resolve("sk_bad"));
    }

    @Test
    void knownKeyResolvesTenant() {
        TenantRepository repo = mock(TenantRepository.class);
        String raw = "sk_live_abc";
        String hash = ApiKeyHasher.sha256Hex(raw);
        when(repo.findActiveByKeyHash(hash)).thenReturn(Optional.of(
                new ApiKeyRecord("k1", "tA", hash, "sk_live", "RUNTIME", "ACTIVE")));
        when(repo.findTenant("tA")).thenReturn(Optional.of(
                new TenantRecord("tA", "A", "ACTIVE", "standard", "platform", 5)));
        TenantResolver resolver = new TenantResolver(repo);
        TenantContext ctx = resolver.resolve(raw);
        assertEquals("tA", ctx.tenantId());
        assertEquals("k1", ctx.apiKeyId());
        assertEquals("RUNTIME", ctx.role());
    }
}
