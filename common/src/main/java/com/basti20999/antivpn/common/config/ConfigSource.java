package com.basti20999.antivpn.common.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view over a platform configuration. Paths are dot-separated
 * ("api.timeout-ms"). Absent or mistyped values yield empty results so
 * {@link Settings#load} can fall back to its defaults.
 */
public interface ConfigSource {

    Optional<String> getString(String path);

    Optional<Integer> getInt(String path);

    Optional<Long> getLong(String path);

    Optional<Boolean> getBoolean(String path);

    /** Returns an empty list when the path is absent. */
    List<String> getStringList(String path);

    /** Returns the scalar children of a section as strings; empty when absent. */
    Map<String, String> getStringMap(String path);

    static ConfigSource empty() {
        return new ConfigSource() {
            @Override
            public Optional<String> getString(String path) {
                return Optional.empty();
            }

            @Override
            public Optional<Integer> getInt(String path) {
                return Optional.empty();
            }

            @Override
            public Optional<Long> getLong(String path) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> getBoolean(String path) {
                return Optional.empty();
            }

            @Override
            public List<String> getStringList(String path) {
                return List.of();
            }

            @Override
            public Map<String, String> getStringMap(String path) {
                return Map.of();
            }
        };
    }
}
