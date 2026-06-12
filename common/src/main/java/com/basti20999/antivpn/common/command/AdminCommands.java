package com.basti20999.antivpn.common.command;

import com.basti20999.antivpn.common.AntiVPNCore;
import com.basti20999.antivpn.common.config.Settings;
import com.basti20999.antivpn.common.net.IpLiterals;
import com.basti20999.antivpn.common.service.VPNCheckService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Platform-independent implementation of every {@code /antivpn} subcommand.
 * Platforms adapt their command API to {@link #execute} / {@link #suggest}
 * and supply player lookup, scheduling and persistence via the bridge.
 */
public final class AdminCommands {

    /** An online player resolved by name; {@code address} may be null. */
    public record ResolvedPlayer(String name, InetAddress address) {
    }

    public interface PlatformBridge {
        Optional<ResolvedPlayer> findOnlinePlayer(String name);

        List<String> onlinePlayerNames();

        void runAsync(Runnable task);

        /** Runs a task in a context where messages may be delivered safely. */
        void deliver(Runnable task);

        List<String> loadNameWhitelist();

        void saveNameWhitelist(List<String> names);

        void reloadPlugin();
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS =
            List.of("reload", "debug", "check", "whitelist", "stats", "cache", "help");

    private final AntiVPNCore core;
    private final PlatformBridge bridge;

    public AdminCommands(AntiVPNCore core, PlatformBridge bridge) {
        this.core = core;
        this.bridge = bridge;
    }

    public void execute(Audience sender, String senderName, boolean isAdmin, String[] args) {
        if (!isAdmin) {
            sender.sendMessage(core.settings().msg("no-permission"));
            return;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload"    -> handleReload(sender, senderName);
            case "debug"     -> handleDebug(sender);
            case "check"     -> handleCheck(sender, args);
            case "whitelist" -> handleWhitelist(sender, senderName, args);
            case "stats"     -> handleStats(sender);
            case "cache"     -> handleCache(sender, senderName, args);
            default          -> sendHelp(sender);
        }
    }

    private void handleReload(Audience sender, String senderName) {
        bridge.reloadPlugin();
        sender.sendMessage(core.settings().msg("reloaded"));
        core.log().info("Config reloaded by " + senderName);
    }

    private void handleDebug(Audience sender) {
        boolean state = core.toggleDebug();
        sender.sendMessage(core.settings().msg("debug-toggled",
                "<state>", state ? "enabled" : "disabled"));
    }

    private void handleCheck(Audience sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn check <player|ip>"));
            return;
        }
        String target = args[1];

        String name;
        InetAddress address;
        Optional<ResolvedPlayer> player = bridge.findOnlinePlayer(target);
        if (player.isPresent()) {
            name = player.get().name();
            address = player.get().address();
            if (address == null) {
                sender.sendMessage(core.settings().msg("no-ip"));
                return;
            }
        } else {
            Optional<InetAddress> parsed = IpLiterals.parse(target);
            if (parsed.isEmpty()) {
                sender.sendMessage(core.settings().msg("check-invalid-target", "<target>", target));
                return;
            }
            name = target;
            address = parsed.get();
        }

        String ip = IpLiterals.canonical(address);
        sender.sendMessage(core.settings().msg("check-start", "<player>", name, "<ip>", ip));

        bridge.runAsync(() -> {
            try {
                VPNCheckService.Verdict verdict = core.checkService().check(ip, core.settings());
                String source = verdict.source().name();
                Component result = verdict.blocked()
                        ? core.settings().msg("check-result-vpn", "<player>", name, "<source>", source)
                        : core.settings().msg("check-result-clean", "<player>", name, "<source>", source);
                bridge.deliver(() -> sender.sendMessage(result));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                bridge.deliver(() -> sender.sendMessage(
                        core.settings().msg("check-error", "<error>", "interrupted")));
            } catch (Exception e) {
                bridge.deliver(() -> sender.sendMessage(
                        core.settings().msg("check-error", "<error>", String.valueOf(e.getMessage()))));
            }
        });
    }

    private void handleWhitelist(Audience sender, String senderName, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn whitelist <add|remove|list> [name]"));
            return;
        }
        Settings settings = core.settings();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn whitelist add <name>"));
                    return;
                }
                String name = args[2];
                List<String> list = new ArrayList<>(bridge.loadNameWhitelist());
                if (list.stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                    sender.sendMessage(settings.msg("whitelist-exists", "<name>", name));
                    return;
                }
                list.add(name);
                bridge.saveNameWhitelist(list);
                bridge.reloadPlugin();
                sender.sendMessage(core.settings().msg("whitelist-added", "<name>", name));
                core.log().info(name + " added to whitelist by " + senderName);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn whitelist remove <name>"));
                    return;
                }
                String name = args[2];
                List<String> list = new ArrayList<>(bridge.loadNameWhitelist());
                if (!list.removeIf(n -> n.equalsIgnoreCase(name))) {
                    sender.sendMessage(settings.msg("whitelist-missing", "<name>", name));
                    return;
                }
                bridge.saveNameWhitelist(list);
                bridge.reloadPlugin();
                sender.sendMessage(core.settings().msg("whitelist-removed", "<name>", name));
                core.log().info(name + " removed from whitelist by " + senderName);
            }
            case "list" -> {
                List<String> list = bridge.loadNameWhitelist();
                if (list.isEmpty()) {
                    sender.sendMessage(settings.msg("whitelist-empty"));
                } else {
                    sender.sendMessage(settings.msg("whitelist-list", "<names>", String.join(", ", list)));
                }
            }
            default -> sender.sendMessage(
                    MM.deserialize("<yellow>Usage: /antivpn whitelist <add|remove|list> [name]"));
        }
    }

    private void handleStats(Audience sender) {
        Settings settings = core.settings();
        VPNCheckService service = core.checkService();
        long cacheHits = service.getCacheHits();
        long apiCalls = service.getApiCalls();
        long total = cacheHits + apiCalls;
        String hitRate = total == 0 ? "N/A"
                : String.format(Locale.ROOT, "%.1f%%", 100.0 * cacheHits / total);
        sender.sendMessage(settings.msg("stats",
                "<blocks>",    String.valueOf(service.getBlocks()),
                "<cacheSize>", String.valueOf(core.cache().size()),
                "<hitRate>",   hitRate,
                "<apiCalls>",  String.valueOf(apiCalls),
                "<apiErrors>", String.valueOf(service.getApiErrors()),
                "<avgMs>",     String.valueOf(service.getAvgApiLatencyMs()),
                "<failMode>",  settings.failMode().name()));
    }

    private void handleCache(Audience sender, String senderName, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn cache <clear|size>"));
            return;
        }
        Settings settings = core.settings();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "clear" -> {
                int removed = core.cache().clear();
                sender.sendMessage(settings.msg("cache-cleared", "<n>", String.valueOf(removed)));
                core.log().info("Cache cleared by " + senderName + " (" + removed + " entries)");
            }
            case "size" -> sender.sendMessage(
                    settings.msg("cache-size", "<n>", String.valueOf(core.cache().size())));
            default -> sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn cache <clear|size>"));
        }
    }

    private void sendHelp(Audience sender) {
        sender.sendMessage(MM.deserialize("<yellow>AntiVPN commands:"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn reload <gray>- Reload config"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn debug <gray>- Toggle debug mode"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn check <player|ip> <gray>- Check a player or IP"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn whitelist <add|remove|list> [name] <gray>- Manage name whitelist"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn stats <gray>- Show plugin statistics"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn cache <clear|size> <gray>- Manage IP cache"));
    }

    public List<String> suggest(boolean isAdmin, String[] args) {
        if (!isAdmin) {
            return List.of();
        }
        if (args.length == 0) {
            return List.copyOf(SUBCOMMANDS);
        }
        if (args.length == 1) {
            return filter(args[0], SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "check"     -> filter(args[1], bridge.onlinePlayerNames());
                case "whitelist" -> filter(args[1], List.of("add", "remove", "list"));
                case "cache"     -> filter(args[1], List.of("clear", "size"));
                default          -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("whitelist")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return filter(args[2], bridge.onlinePlayerNames());
        }
        return List.of();
    }

    private static List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
