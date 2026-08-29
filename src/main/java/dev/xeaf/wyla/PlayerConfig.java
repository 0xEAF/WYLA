package dev.xeaf.wyla;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerConfig {
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration data;

    public PlayerConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "players.yml");

        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized boolean get(Player player) {
        return data.getBoolean("players." + player.getUniqueId(), true);
    }

    public synchronized boolean exists(Player player) {
        return data.contains("players." + player.getUniqueId());
    }

    public synchronized void set(Player player, boolean value) {
        data.set("players." + player.getUniqueId(), value);
        save();
    }

    public synchronized boolean toggle(Player player) {
        boolean value = !get(player);
        set(player, value);
        return value;
    }

    private synchronized void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save players.yml!");
            e.printStackTrace();
        }
    }
}
