package com.policyengine.core;

import com.policyengine.model.Policy;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ground-truth store for parsed policies.
 * The Caffeine cache holds compiled forms; this map holds the canonical parsed objects.
 * Thread-safe via ConcurrentHashMap (lock-free reads).
 */
public class PolicyRepository {

    private final ConcurrentHashMap<String, Policy> policies = new ConcurrentHashMap<>();

    public void add(Policy policy) {
        policies.put(policy.getId(), policy);
    }

    public Optional<Policy> get(String id) {
        return Optional.ofNullable(policies.get(id));
    }

    public Collection<Policy> getAll() {
        return Collections.unmodifiableCollection(policies.values());
    }

    public void remove(String id) {
        policies.remove(id);
    }

    public boolean contains(String id) {
        return policies.containsKey(id);
    }

    public int size() {
        return policies.size();
    }
}
