package dev.xeaf.wyla;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent;
import net.kyori.adventure.text.Component;

import dev.xeaf.wyla.BossBarManager;

public class WYLA extends JavaPlugin implements Listener {
    private final BossBarManager bossBarManager = new BossBarManager();
    private final Map<UUID, BreakData> breaking = new ConcurrentHashMap<>();
    private PlayerConfig playerConfig;
    private boolean performanceMode;
    private int updateTicks;
    private int reachDistance;
    private String initialMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        playerConfig = new PlayerConfig(this);
        getCommand("wyla").setExecutor(new PlayerCommand(playerConfig, bossBarManager));

        performanceMode = getConfig().getBoolean("performance-mode");
        updateTicks = Math.abs(getConfig().getInt("update-ticks"));
        reachDistance = getConfig().getInt("reach-distance");
        initialMessage = getConfig().getString("initial-message", "");
    }

    private boolean updateRay(Player player) {
        if (!player.isOnline()) {
            return false;
        }

        if (!playerConfig.get(player)) {
            bossBarManager.remove(player);
            return true;
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        System.out.println("Player reach distance: " + player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).getValue());
        RayTraceResult result = player.getWorld().rayTrace(
            eye, direction, reachDistance < 0
                    ? player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).getValue()
                    : reachDistance,
            FluidCollisionMode.ALWAYS, false, 0.0,
            entity -> entity != player
        );

        Entity entity = null;
        Block block = null;

        if (result != null) {
            entity = result.getHitEntity();
            block = result.getHitBlock();
        }

        if (entity != null) {
            String key = entity.getType().translationKey();
            Component name = Component.translatable(key);
            Component displayName = entity.customName();

            if (displayName != null) {
                name = displayName;
            }

            if (entity instanceof LivingEntity) {
                if (entity instanceof Player) {
                    name = Component.text(((Player) entity).getName());
                }

                LivingEntity livingEntity = (LivingEntity) entity;
                double health = livingEntity.getHealth();
                double maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                float progress = (float) (health / maxHealth);
                bossBarManager.update(player, name, progress);
            } else {
                bossBarManager.update(player, name, 1.0f);
            }
        } else if (block != null) {
            String key = block.getType().translationKey();
            Component name = Component.translatable(key);
            bossBarManager.update(player, name, 
                breaking.containsKey(player.getUniqueId()) 
                    ? 1.0f - breaking.get(player.getUniqueId()).progress()
                    : 1.0f
            );
        } else {
            bossBarManager.remove(player);
        }

        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!playerConfig.exists(player) && initialMessage != null && !initialMessage.isEmpty()) {
            player.sendMessage(Component.text(initialMessage));
            playerConfig.set(player, true);
        }

        if (performanceMode) {
            return;
        }

        player.getScheduler().runAtFixedRate(this, task -> {
            if (!updateRay(player)) {
                task.cancel();
            }
        }, null, 1, updateTicks);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!performanceMode) {
            return;
        }

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        boolean rotated =
                from.getYaw() != to.getYaw()
                || from.getPitch() != to.getPitch();
        
        if (!rotated) {
            return;
        }

        updateRay(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProgressUpdate(BlockBreakProgressUpdateEvent event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof Player player)) {
            return;
        }

        Block block = event.getBlock();
        float progress = event.getProgress();

        breaking.put(
            player.getUniqueId(),
            new BreakData(
                Component.translatable(
                    block.getType()
                        .translationKey()
                ), progress
            )
        );
    }

    @EventHandler
    public void onDamageAbort(BlockDamageAbortEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        remove((Player) event.getPlayer());
    }

    private void remove(Player player) {
        breaking.remove(player.getUniqueId());
        bossBarManager.remove(player);
    }

    public boolean isBreaking(Player player) {
        return breaking.containsKey(player.getUniqueId());
    }

    public Component getBlockName(Player player) {
        BreakData data = breaking.get(player.getUniqueId());
        return data != null ? data.blockName() : null;
    }

    public float getProgress(Player player) {
        BreakData data = breaking.get(player.getUniqueId());
        return data == null ? 0.0f : data.progress();
    }

    public BreakData getBreakData(Player player) {
        return breaking.get(player.getUniqueId());
    }

    public record BreakData(Component blockName, float progress) {}
}