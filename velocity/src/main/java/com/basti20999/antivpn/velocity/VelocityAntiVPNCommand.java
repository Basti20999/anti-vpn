package com.basti20999.antivpn.velocity;

import com.basti20999.antivpn.common.command.AdminCommands;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.List;

final class VelocityAntiVPNCommand implements SimpleCommand {

    private static final String ADMIN_PERMISSION = "antivpn.admin";

    private final AdminCommands commands;

    VelocityAntiVPNCommand(AdminCommands commands) {
        this.commands = commands;
    }

    @Override
    public void execute(Invocation invocation) {
        String senderName = invocation.source() instanceof Player player
                ? player.getUsername()
                : "console";
        commands.execute(invocation.source(), senderName,
                invocation.source().hasPermission(ADMIN_PERMISSION), invocation.arguments());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return commands.suggest(invocation.source().hasPermission(ADMIN_PERMISSION),
                invocation.arguments());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(ADMIN_PERMISSION);
    }
}
