package dev.xeaf.wyla;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public void show(Player player, Component blockName, float progress) {
        BossBar bar = bars.computeIfAbsent(
                player.getUniqueId(),
                uuid -> BossBar.bossBar(
                        blockName,
                        progress,
                        BossBar.Color.GREEN,
                        BossBar.Overlay.PROGRESS
                )
        );

        bar.name(blockName);
        bar.progress(progress);

        player.showBossBar(bar);
    }

    public void update(Player player, Component blockName, float progress) {
        BossBar bar = bars.get(player.getUniqueId());

        if (bar == null) {
            show(player, blockName, progress);
            return;
        }

        bar.name(blockName);
        bar.progress(progress);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());

        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void remove(Player player) {
        hide(player);
    }
}