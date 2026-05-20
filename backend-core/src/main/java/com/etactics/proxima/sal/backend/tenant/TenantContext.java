package com.etactics.proxima.sal.backend.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) { HOLDER.set(tenantId); }
    public static Long get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
