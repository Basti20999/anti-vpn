package com.basti20999.antivpn.paper;

import com.basti20999.antivpn.common.config.ConfigSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class BukkitConfigSource implements ConfigSource {

    private final FileConfiguration config;

    BukkitConfigSource(FileConfiguration config) {
        this.config = config;
    }

    @Override
    public Optional<String> getString(String path) {
        return Optional.ofNullable(config.getString(path, null));
    }

    @Override
    public Optional<Integer> getInt(String path) {
        return config.get(path) instanceof Number number
                ? Optional.of(number.intValue())
                : Optional.empty();
    }

    @Override
    public Optional<Long> getLong(String path) {
        return config.get(path) instanceof Number number
                ? Optional.of(number.longValue())
                : Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean(String path) {
        return config.get(path) instanceof Boolean bool
                ? Optional.of(bool)
                : Optional.empty();
    }

    @Override
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    @Override
    public Map<String, String> getStringMap(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }
}
