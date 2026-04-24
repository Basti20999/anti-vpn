package com.basti20999.antiVpn.command;

import com.basti20999.antiVpn.AntiVPN;
import com.basti20999.antiVpn.config.PluginSettings;
import com.basti20999.antiVpn.service.VPNCheckService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AntiVPNCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS =
            List.of("reload", "debug", "check", "whitelist", "stats", "cache", "help");

    private final AntiVPN plugin;
    private final VPNCheckService service;

    public AntiVPNCommand(AntiVPN plugin, VPNCheckService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antivpn.admin")) {
            sender.sendMessage(plugin.getSettings().msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload"    -> handleReload(sender);
            case "debug"     -> handleDebug(sender);
            case "check"     -> handleCheck(sender, args);
            case "whitelist" -> handleWhitelist(sender, args);
            case "stats"     -> handleStats(sender);
            case "cache"     -> handleCache(sender, args);
            default          -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadSettings();
        sender.sendMessage(plugin.getSettings().msg("reloaded"));
        plugin.getLogger().info("[AntiVPN] Config reloaded by " + sender.getName());
    }

    private void handleDebug(CommandSender sender) {
        boolean newDebug = !plugin.getSettings().debugMode();
        plugin.getConfig().set("debug-mode", newDebug);
        plugin.saveConfig();
        plugin.reloadSettings();
        sender.sendMessage(plugin.getSettings().msg("debug-toggled",
                "<state>", newDebug ? "enabled" : "disabled"));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn check <player>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getSettings().msg("player-not-found", "<player>", args[1]));
            return;
        }
        if (target.getAddress() == null) {
            sender.sendMessage(plugin.getSettings().msg("no-ip"));
            return;
        }
        String ip = target.getAddress().getAddress().getHostAddress();
        String name = target.getName();
        sender.sendMessage(plugin.getSettings().msg("check-start",
                "<player>", name, "<ip>", ip));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                VPNCheckService.Verdict verdict = service.check(ip, plugin.getSettings());
                String source = verdict.source().name();
                Component result = verdict.blocked()
                        ? plugin.getSettings().msg("check-result-vpn",
                                "<player>", name, "<source>", source)
                        : plugin.getSettings().msg("check-result-clean",
                                "<player>", name, "<source>", source);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getSettings().msg("check-error",
                                "<error>", "interrupted")));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getSettings().msg("check-error",
                                "<error>", String.valueOf(e.getMessage()))));
            }
        });
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn whitelist <add|remove|list> [name]"));
            return;
        }
        PluginSettings settings = plugin.getSettings();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn whitelist add <name>"));
                    return;
                }
                String name = args[2];
                List<String> list = new ArrayList<>(plugin.getConfig().getStringList("whitelist"));
                if (list.stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                    sender.sendMessage(settings.msg("whitelist-exists", "<name>", name));
                    return;
                }
                list.add(name);
                plugin.getConfig().set("whitelist", list);
                plugin.saveConfig();
                plugin.reloadSettings();
                sender.sendMessage(plugin.getSettings().msg("whitelist-added", "<name>", name));
                plugin.getLogger().info("[AntiVPN] " + name + " added to whitelist by " + sender.getName());
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn whitelist remove <name>"));
                    return;
                }
                String name = args[2];
                List<String> list = new ArrayList<>(plugin.getConfig().getStringList("whitelist"));
                boolean removed = list.removeIf(n -> n.equalsIgnoreCase(name));
                if (!removed) {
                    sender.sendMessage(settings.msg("whitelist-missing", "<name>", name));
                    return;
                }
                plugin.getConfig().set("whitelist", list);
                plugin.saveConfig();
                plugin.reloadSettings();
                sender.sendMessage(plugin.getSettings().msg("whitelist-removed", "<name>", name));
                plugin.getLogger().info("[AntiVPN] " + name + " removed from whitelist by " + sender.getName());
            }
            case "list" -> {
                List<String> list = plugin.getConfig().getStringList("whitelist");
                if (list.isEmpty()) {
                    sender.sendMessage(settings.msg("whitelist-empty"));
                } else {
                    sender.sendMessage(settings.msg("whitelist-list",
                            "<names>", String.join(", ", list)));
                }
            }
            default -> sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn whitelist <add|remove|list> [name]"));
        }
    }

    private void handleStats(CommandSender sender) {
        PluginSettings settings = plugin.getSettings();
        long blocks = service.getBlocks();
        long cacheHits = service.getCacheHits();
        long apiCalls = service.getApiCalls();
        long total = cacheHits + apiCalls;
        String hitRate = total == 0 ? "N/A" : String.format(Locale.ROOT, "%.1f%%", 100.0 * cacheHits / total);
        sender.sendMessage(settings.msg("stats",
                "<blocks>",    String.valueOf(blocks),
                "<cacheSize>", String.valueOf(plugin.getCache().size()),
                "<hitRate>",   hitRate,
                "<avgMs>",     String.valueOf(service.getAvgApiLatencyMs()),
                "<failMode>",  settings.failMode().name()));
    }

    private void handleCache(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn cache <clear|size>"));
            return;
        }
        PluginSettings settings = plugin.getSettings();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "clear" -> {
                int removed = plugin.getCache().clear();
                sender.sendMessage(settings.msg("cache-cleared", "<n>", String.valueOf(removed)));
                plugin.getLogger().info("[AntiVPN] Cache cleared by " + sender.getName() + " (" + removed + " entries)");
            }
            case "size" -> sender.sendMessage(settings.msg("cache-size",
                    "<n>", String.valueOf(plugin.getCache().size())));
            default -> sender.sendMessage(MM.deserialize("<yellow>Usage: /antivpn cache <clear|size>"));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<yellow>AntiVPN commands:"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn reload <gray>- Reload config"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn debug <gray>- Toggle debug mode"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn check <player> <gray>- Check a player's IP"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn whitelist <add|remove|list> [name] <gray>- Manage name whitelist"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn stats <gray>- Show plugin statistics"));
        sender.sendMessage(MM.deserialize("<yellow>/antivpn cache <clear|size> <gray>- Manage IP cache"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antivpn.admin")) return List.of();
        if (args.length == 1) return filter(args[0], SUBCOMMANDS);
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "check"     -> filterOnlinePlayers(args[1]);
                case "whitelist" -> filter(args[1], List.of("add", "remove", "list"));
                case "cache"     -> filter(args[1], List.of("clear", "size"));
                default          -> List.of();
            };
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("whitelist")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return filterOnlinePlayers(args[2]);
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.startsWith(lower)) result.add(opt);
        }
        return result;
    }

    private List<String> filterOnlinePlayers(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(p.getName());
            }
        }
        return result;
    }
}
