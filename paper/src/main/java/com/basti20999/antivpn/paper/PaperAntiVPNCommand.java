package com.basti20999.antivpn.paper;

import com.basti20999.antivpn.common.command.AdminCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

final class PaperAntiVPNCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "antivpn.admin";

    private final AdminCommands commands;

    PaperAntiVPNCommand(AdminCommands commands) {
        this.commands = commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        commands.execute(sender, sender.getName(), sender.hasPermission(ADMIN_PERMISSION), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return commands.suggest(sender.hasPermission(ADMIN_PERMISSION), args);
    }
}
