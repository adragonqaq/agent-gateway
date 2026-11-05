package com.agent.gateway.tenant;

import java.util.Optional;

public interface TenantRepository {

    void insertTenant(TenantRecord tenant);

    void insertApiKey(ApiKeyRecord apiKey);

    Optional<TenantRecord> findTenant(String tenantId);

    Optional<ApiKeyRecord> findActiveByKeyHash(String keyHash);
}
