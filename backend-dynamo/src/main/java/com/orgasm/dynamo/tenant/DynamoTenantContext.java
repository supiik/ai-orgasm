package com.orgasm.dynamo.tenant;

public final class DynamoTenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private DynamoTenantContext() {}

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    /** Defaults to tenant 1 when unset, matching Lambda's current (auth-less) single-tenant behavior. */
    public static Long get() {
        Long tenantId = CURRENT.get();
        return tenantId != null ? tenantId : 1L;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
