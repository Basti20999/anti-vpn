package com.basti20999.antivpn.velocity;

import com.basti20999.antivpn.common.config.ConfigSource;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads config.yml through Configurate, which Velocity bundles. */
final class VelocityConfigSource implements ConfigSource {

    private final ConfigurationNode root;

    private VelocityConfigSource(ConfigurationNode root) {
        this.root = root;
    }

    static VelocityConfigSource load(Path file) {
        try {
            return new VelocityConfigSource(loader(file).load());
        } catch (ConfigurateException e) {
            throw new UncheckedIOException("Could not parse " + file, e);
        }
    }

    /**
     * Rewrites only the whitelist key. Note: Configurate's YAML writer drops
     * comments, so in-game whitelist edits strip them from config.yml.
     */
    static void saveWhitelist(Path file, List<String> names) throws IOException {
        YamlConfigurationLoader loader = loader(file);
        CommentedConfigurationNode node = loader.load();
        try {
            node.node("whitelist").setList(String.class, names);
        } catch (SerializationException e) {
            throw new IOException("Could not serialize whitelist", e);
        }
        loader.save(node);
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }

    private ConfigurationNode node(String path) {
        return root.node((Object[]) path.split("\\."));
    }

    @Override
    public Optional<String> getString(String path) {
        Object value = node(path).raw();
        return (value instanceof String || value instanceof Number || value instanceof Boolean)
                ? Optional.of(String.valueOf(value))
                : Optional.empty();
    }

    @Override
    public Optional<Integer> getInt(String path) {
        return node(path).raw() instanceof Number number
                ? Optional.of(number.intValue())
                : Optional.empty();
    }

    @Override
    public Optional<Long> getLong(String path) {
        return node(path).raw() instanceof Number number
                ? Optional.of(number.longValue())
                : Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean(String path) {
        return node(path).raw() instanceof Boolean bool
                ? Optional.of(bool)
                : Optional.empty();
    }

    @Override
    public List<String> getStringList(String path) {
        ConfigurationNode node = node(path);
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        try {
            List<String> list = node.getList(String.class);
            return list == null ? List.of() : list;
        } catch (SerializationException e) {
            return List.of();
        }
    }

    @Override
    public Map<String, String> getStringMap(String path) {
        ConfigurationNode node = node(path);
        if (node.virtual() || !node.isMap()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String value = entry.getValue().getString();
            if (value != null) {
                result.put(String.valueOf(entry.getKey()), value);
            }
        }
        return result;
    }
}
