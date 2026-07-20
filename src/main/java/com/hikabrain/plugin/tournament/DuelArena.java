package com.hikabrain.plugin.tournament;

import com.hikabrain.plugin.game.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Arène utilisée pour les matchs de tournoi qui ne passent pas par le moteur
 * HikaBrain (1v1, 2v2, FFA, Faction vs Faction) : des duels résolus aux kills.
 *
 * Contrairement à {@link com.hikabrain.plugin.game.Arena} qui n'a que 2 équipes
 * fixes (RED/BLUE), une DuelArena a un nombre variable de "slots" (0, 1, 2, 3...)
 * pour pouvoir accueillir un FFA à plus de 2 compétiteurs. Chaque slot peut avoir
 * plusieurs points de spawn (pour étaler les membres d'une même équipe en 2v2/Faction).
 */
public class DuelArena {

    private final String name;
    private Location waitingSpawn;
    private Location spectatorSpawn;
    private CuboidRegion bounds;

    /** Spawns par slot (index 0, 1, 2...). Un slot peut avoir plusieurs points. */
    private final List<List<Location>> slotSpawns = new ArrayList<>();

    public DuelArena(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getWaitingSpawn() {
        return waitingSpawn;
    }

    public void setWaitingSpawn(Location waitingSpawn) {
        this.waitingSpawn = waitingSpawn;
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }

    public CuboidRegion getBounds() {
        return bounds;
    }

    public void setBounds(CuboidRegion bounds) {
        this.bounds = bounds;
    }

    /** true si le point est en dehors des limites configurées (chute dans le vide, etc.). Sans limites définies : toujours false. */
    public boolean isOutOfBounds(Location loc) {
        return bounds != null && !bounds.contains(loc);
    }

    public int getSlotCount() {
        return slotSpawns.size();
    }

    public void ensureSlots(int count) {
        while (slotSpawns.size() < count) {
            slotSpawns.add(new ArrayList<>());
        }
    }

    public boolean addSpawn(int slotIndex, Location loc) {
        if (slotIndex < 0) return false;
        ensureSlots(slotIndex + 1);
        slotSpawns.get(slotIndex).add(loc);
        return true;
    }

    public void clearSpawns(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < slotSpawns.size()) {
            slotSpawns.get(slotIndex).clear();
        }
    }

    public Location getSpawn(int slotIndex, int memberOffset) {
        if (slotIndex < 0 || slotIndex >= slotSpawns.size() || slotSpawns.get(slotIndex).isEmpty()) {
            return null;
        }
        List<Location> spawns = slotSpawns.get(slotIndex);
        if (memberOffset < spawns.size()) {
            return spawns.get(memberOffset);
        }
        // Plus de membres que de spawns dédiés : on pioche aléatoirement parmi les existants.
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
    }

    /** true si l'arène peut accueillir au moins "requiredSlots" compétiteurs, chacun avec au moins un spawn. */
    public boolean isReady(int requiredSlots) {
        if (waitingSpawn == null) return false;
        if (slotSpawns.size() < requiredSlots) return false;
        for (int i = 0; i < requiredSlots; i++) {
            if (slotSpawns.get(i).isEmpty()) return false;
        }
        return true;
    }

    // ---- Persistance ----

    public void saveToConfig(FileConfiguration config, String path) {
        saveLocation(config, path + ".waiting", waitingSpawn);
        saveLocation(config, path + ".spectator", spectatorSpawn);
        if (bounds != null) {
            saveLocation(config, path + ".bounds.corner1", bounds.getCorner1());
            saveLocation(config, path + ".bounds.corner2", bounds.getCorner2());
        }
        for (int i = 0; i < slotSpawns.size(); i++) {
            List<Location> spawns = slotSpawns.get(i);
            for (int j = 0; j < spawns.size(); j++) {
                saveLocation(config, path + ".slots." + i + "." + j, spawns.get(j));
            }
        }
    }

    public static DuelArena loadFromConfig(FileConfiguration config, String path, String name) {
        DuelArena arena = new DuelArena(name);
        arena.waitingSpawn = loadLocation(config, path + ".waiting");
        arena.spectatorSpawn = loadLocation(config, path + ".spectator");
        Location c1 = loadLocation(config, path + ".bounds.corner1");
        Location c2 = loadLocation(config, path + ".bounds.corner2");
        if (c1 != null && c2 != null) {
            arena.bounds = new CuboidRegion(c1, c2);
        }
        if (config.getConfigurationSection(path + ".slots") != null) {
            for (String slotKey : config.getConfigurationSection(path + ".slots").getKeys(false)) {
                int slotIndex = Integer.parseInt(slotKey);
                arena.ensureSlots(slotIndex + 1);
                String slotPath = path + ".slots." + slotKey;
                if (config.getConfigurationSection(slotPath) == null) continue;
                for (String spawnKey : config.getConfigurationSection(slotPath).getKeys(false)) {
                    Location loc = loadLocation(config, slotPath + "." + spawnKey);
                    if (loc != null) {
                        arena.slotSpawns.get(slotIndex).add(loc);
                    }
                }
            }
        }
        return arena;
    }

    private static void saveLocation(FileConfiguration config, String path, Location loc) {
        if (loc == null) return;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }

    private static Location loadLocation(FileConfiguration config, String path) {
        if (!config.isSet(path + ".world")) return null;
        World world = org.bukkit.Bukkit.getWorld(config.getString(path + ".world"));
        if (world == null) return null;
        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch")
        );
    }
}
