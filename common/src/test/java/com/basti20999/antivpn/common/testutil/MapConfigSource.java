package com.basti20999.antivpn.common.testutil;

import com.basti20999.antivpn.common.config.ConfigSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test config backed by a flat map of dotted paths to typed values. */
public final class MapConfigSource implements ConfigSource {

    private final Map<String, Object> values;

    public MapConfigSource(Map<String, Object> values) {
        this.values = values;
    }

    @Override
    public Optional<String> getString(String path) {
        Object value = values.get(path);
        return (value instanceof String || value instanceof Number || value instanceof Boolean)
                ? Optional.of(String.valueOf(value))
                : Optional.empty();
    }

    @Override
    public Optional<Integer> getInt(String path) {
        return values.get(path) instanceof Number number
                ? Optional.of(number.intValue())
                : Optional.empty();
    }

    @Override
    public Optional<Long> getLong(String path) {
        return values.get(path) instanceof Number number
                ? Optional.of(number.longValue())
                : Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean(String path) {
        return values.get(path) instanceof Boolean bool
                ? Optional.of(bool)
                : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getStringList(String path) {
        return values.get(path) instanceof List<?> list
                ? (List<String>) list
                : List.of();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> getStringMap(String path) {
        return values.get(path) instanceof Map<?, ?> map
                ? (Map<String, String>) map
                : Map.of();
    }
}
