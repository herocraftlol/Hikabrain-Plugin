package com.hikabrain.plugin.tournament;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registre des arènes de duel (1v1/2v2/FFA/Faction), persistées dans
 * plugins/HikaBrain/tournaments/duel-arenas.yml
 */
public class DuelArenaManager {

    private final HikaBrainPlugin plugin;
    private final File file;
    private final Map<String, DuelArena> arenas = new LinkedHashMap<>();

    public DuelArenaManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "tournaments");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "duel-arenas.yml");
    }

    public void loadAll() {
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getConfigurationSection("arenas") == null) return;
        for (String name : config.getConfigurationSection("arenas").getKeys(false)) {
            DuelArena arena = DuelArena.loadFromConfig(config, "arenas." + name, name);
            arenas.put(name.toLowerCase(), arena);
        }
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        for (DuelArena arena : arenas.values()) {
            arena.saveToConfig(config, "arenas." + arena.getName());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder les arènes de duel : " + e.getMessage());
        }
    }

    public DuelArena getOrCreate(String name) {
        return arenas.computeIfAbsent(name.toLowerCase(), k -> new DuelArena(name));
    }

    public DuelArena get(String name) {
        if (name == null) return null;
        return arenas.get(name.toLowerCase());
    }

    public boolean delete(String name) {
        return arenas.remove(name.toLowerCase()) != null;
    }

    public Collection<DuelArena> getAll() {
        return arenas.values();
    }

    public List<String> getNames() {
        return new ArrayList<>(arenas.keySet());
    }

    /** Renvoie une arène disponible (configurée pour au moins "requiredSlots") et non utilisée par les matchs "busyArenas". */
    public DuelArena findFreeArena(int requiredSlots, Collection<String> busyArenaNames) {
        for (DuelArena arena : arenas.values()) {
            if (!busyArenaNames.contains(arena.getName()) && arena.isReady(requiredSlots)) {
                return arena;
            }
        }
        return null;
    }
}
