package com.policyengine.cache;

import java.util.function.Supplier;

public interface PolicyCache {

    /**
     * Returns the compiled policy for {@code policyId}, loading it via {@code loader}
     * if not already cached.
     */
    CompiledPolicy get(String policyId, Supplier<CompiledPolicy> loader);

    void put(String policyId, CompiledPolicy compiled);

    void invalidate(String policyId);

    void invalidateAll();
}
