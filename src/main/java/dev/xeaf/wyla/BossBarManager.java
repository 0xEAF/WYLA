package dev.xeaf.wyla;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {
    private final JavaPlugin plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public BossBarManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, Component name, float progress) {
        boolean scheduled = player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                /*plugin.getLogger().info("[WYLA-DEBUG] BossBarManager.update task ran for "
                        + player.getName() + " but they are no longer online, skipping.");*/
                return;
            }

            /*boolean isNewBar = !bars.containsKey(player.getUniqueId());*/
            BossBar bar = bars.computeIfAbsent(
                player.getUniqueId(),
                uuid -> BossBar.bossBar(
                    name,
                    progress,
                    BossBar.Color.GREEN,
                    BossBar.Overlay.PROGRESS
                )
            );

            bar.name(name);
            bar.progress(progress);
            player.showBossBar(bar);

            /*plugin.getLogger().info("[WYLA-DEBUG] BossBarManager.update: showBossBar() called for "
                    + player.getName() + " (newBar=" + isNewBar + ", progress=" + progress + ")");*/

        }, null) != null;

        if (!scheduled) {
            /*plugin.getLogger().warning("[WYLA-DEBUG] BossBarManager.update: scheduler.run() returned null "
                    + "(task was NOT scheduled) for " + player.getName()
                    + " - the update was silently dropped!");*/
        }
    }

    public void remove(Player player) {

        boolean scheduled = player.getScheduler().run(plugin, task -> {

            BossBar bar = bars.remove(player.getUniqueId());

            if (bar != null) {
                player.hideBossBar(bar);
                /*plugin.getLogger().info("[WYLA-DEBUG] BossBarManager.remove: hideBossBar() called for "
                        + player.getName());*/
            }

        }, null) != null;

        if (!scheduled) {
            /*plugin.getLogger().warning("[WYLA-DEBUG] BossBarManager.remove: scheduler.run() returned null "
                    + "(task was NOT scheduled) for " + player.getName());*/
        }
    }
}