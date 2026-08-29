package dev.xeaf.wyla;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlayerCommand implements CommandExecutor {
    private final PlayerConfig playerConfig;
    private final BossBarManager bossBarManager;

    public PlayerCommand(PlayerConfig playerConfig, BossBarManager bossBarManager) {
        this.playerConfig = playerConfig;
        this.bossBarManager = bossBarManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }

        Player player = (Player) sender;
        boolean enabled = playerConfig.toggle(player);
        
        if (!enabled) {
            bossBarManager.remove(player);
        }

        player.sendMessage("WYLA is now " + (enabled ? "enabled" : "disabled") + " for you.");
        return true;
    }
}
