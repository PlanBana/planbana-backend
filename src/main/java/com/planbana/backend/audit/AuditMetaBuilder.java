package com.planbana.backend.audit;

import java.util.HashMap;
import java.util.Map;

public final class AuditMetaBuilder {

    private final Map<String, Object> meta = new HashMap<>();

    private AuditMetaBuilder() {
    }

    public static AuditMetaBuilder create() {
        return new AuditMetaBuilder();
    }

    public AuditMetaBuilder put(String key, Object value) {
        // null-safe by design
        meta.put(key, value);
        return this;
    }

    public AuditMetaBuilder putIfNotNull(String key, Object value) {
        if (value != null) {
            meta.put(key, value);
        }
        return this;
    }

    public Map<String, Object> build() {
        return meta;
    }
}
