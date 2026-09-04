package dev.xeaf.wyla;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
    private final BossBarManager bossBarManager = new BossBarManager(this);
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

        try {
            return doUpdateRay(player);
        } catch (Exception e) {
            /*getLogger().log(Level.WARNING, "[WYLA-DEBUG] updateRay threw an exception for "
                    + player.getName() + ": " + e, e);*/
            return true;
        }
    }

    private boolean doUpdateRay(Player player) {
        if (!playerConfig.get(player)) {
            return true;
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();

        double blockRange;
        double entityRange;

        if (reachDistance <= 0) {
            AttributeInstance blockRangeAttr = player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE);
            AttributeInstance entityRangeAttr = player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE);
            blockRange = blockRangeAttr != null ? blockRangeAttr.getValue() : 4.5;
            entityRange = entityRangeAttr != null ? entityRangeAttr.getValue() : 3.0;
            /*getLogger().info("[WYLA-DEBUG] " + player.getName()
                    + " using auto reach - blockRange=" + blockRange + ", entityRange=" + entityRange
                    + " (config reach-distance=" + reachDistance + ")");*/
        } else {
            blockRange = reachDistance;
            entityRange = reachDistance;
        }

        double maxDistance = Math.max(blockRange, entityRange);

        RayTraceResult result = player.getWorld().rayTrace(
            eye, direction, maxDistance,
            FluidCollisionMode.ALWAYS, false, 0.0,
            entity -> entity != player && !(entity instanceof Projectile)
        );

        Entity entity = null;
        Block block = null;

        if (result != null) {
            entity = result.getHitEntity();
            block = result.getHitBlock();
            double hitDistance = result.getHitPosition().distance(eye.toVector());

            if (entity != null && hitDistance > entityRange) {
                entity = null;
            }
            
            if (block != null && hitDistance > blockRange) {
                block = null;
            }
        }

        /*getLogger().info("[WYLA-DEBUG] " + player.getName()
                + " ray result -> entity=" + (entity != null ? entity.getType() : "none")
                + ", block=" + (block != null ? block.getType() : "none"));*/

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
                AttributeInstance maxHealthAttr = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH);

                if (maxHealthAttr == null) {
                    /*getLogger().warning("[WYLA-DEBUG] " + entity.getType()
                            + " has no GENERIC_MAX_HEALTH attribute, defaulting progress to 1.0");*/
                    bossBarManager.update(player, name, 1.0f);
                    return true;
                }

                double maxHealth = maxHealthAttr.getValue();
                float progress = maxHealth > 0
                        ? (float) Math.max(0.0, Math.min(1.0, health / maxHealth))
                        : 1.0f;

                /*getLogger().info("[WYLA-DEBUG] Calling bossBarManager.update() for "
                        + player.getName() + " -> entity name, progress=" + progress);*/
                bossBarManager.update(player, name, progress);
            } else {
                /*getLogger().info("[WYLA-DEBUG] Calling bossBarManager.update() for "
                        + player.getName() + " -> non-living entity, progress=1.0");*/
                bossBarManager.update(player, name, 1.0f);
            }
        } else if (block != null) {
            String key = block.getType().translationKey();
            Component name = Component.translatable(key);
            float progress = breaking.containsKey(player.getUniqueId())
                    ? 1.0f - breaking.get(player.getUniqueId()).progress()
                    : 1.0f;

            /*getLogger().info("[WYLA-DEBUG] Calling bossBarManager.update() for "
                    + player.getName() + " -> block name, progress=" + progress);*/
            bossBarManager.update(player, name, progress);
        } else {
            /*getLogger().info("[WYLA-DEBUG] Calling bossBarManager.remove() for " + player.getName()
                    + " (no entity/block hit)");*/
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