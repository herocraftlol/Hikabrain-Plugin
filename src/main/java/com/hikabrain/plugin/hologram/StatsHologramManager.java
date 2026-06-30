package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.stats.StatsManager;
import com.hikabrain.plugin.stats.StatsManager.GameMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hologramme de leaderboard HikaBrain — affiche TOUS les modes d'un coup.
 *
 * ── Structure visuelle ────────────────────────────────────────────────────────
 *   ══ HikaBrain ══                     (titre, AQUA BOLD)
 *   ─────────────────────
 *   ◆ 1v1
 *   🏆 #1 Joueur1  12 wins  ⚔ K/D: 4.2
 *   🏆 #2 Joueur2   9 wins  ⚔ K/D: 3.1
 *   🏆 #3 Joueur3   7 wins  ⚔ K/D: 2.8
 *   ─────────────────────
 *   ◆ 2v2
 *   ...
 *   ─────────────────────
 *   Parties : XX   Captures : XX
 */
public class StatsHologramManager {

    private static final double LINE_GAP      = 0.27;
    private static final String HOLOGRAM_FILE = "hologram.yml";
    private static final String PDC_VALUE     = "stats";
    private static final int    TOP_SIZE      = 3;

    private final HikaBrainPlugin plugin;
    private final File             cfgFile;
    private FileConfiguration      cfg;

    private final NamespacedKey pdcKey;

    private final List<UUID> lineEntities = new ArrayList<>();

    private Location hologramLocation = null;

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin  = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), HOLOGRAM_FILE);
        this.pdcKey  = new NamespacedKey(plugin, "hologram");
        loadConfig();
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    private void loadConfig() {
        if (!cfgFile.exists()) return;
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        if (cfg.contains("location.world")) {
            String worldName = cfg.getString("location.world");
            World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                double x = cfg.getDouble("location.x");
                double y = cfg.getDouble("location.y");
                double z = cfg.getDouble("location.z");
                hologramLocation = new Location(world, x, y, z);
                Chunk chunk = world.getChunkAt(hologramLocation);
                world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
                purgeOrphanArmorStands(world);
                buildLines();
            } else {
                plugin.getLogger().warning("[HikaBrain] Hologramme : monde '" + worldName + "' introuvable au démarrage.");
            }
        }
    }

    private void saveConfig() {
        if (cfg == null) cfg = new YamlConfiguration();
        if (hologramLocation != null) {
            cfg.set("location.world", hologramLocation.getWorld().getName());
            cfg.set("location.x",    hologramLocation.getX());
            cfg.set("location.y",    hologramLocation.getY());
            cfg.set("location.z",    hologramLocation.getZ());
        } else {
            cfg.set("location", null);
        }
        try { cfg.save(cfgFile); }
        catch (IOException e) { plugin.getLogger().warning("[HikaBrain] Impossible de sauvegarder hologram.yml : " + e.getMessage()); }
    }

    // ── API publique ───────────────────────────────────────────────────────────

    public void spawn(Location loc) {
        despawnEntities();
        hologramLocation = loc.clone();
        Chunk chunk = loc.getWorld().getChunkAt(loc);
        loc.getWorld().addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        buildLines();
        saveConfig();
    }

    public void despawn() {
        if (hologramLocation != null) {
            Chunk chunk = hologramLocation.getWorld().getChunkAt(hologramLocation);
            hologramLocation.getWorld().removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        }
        despawnEntities();
        hologramLocation = null;
        saveConfig();
    }

    public void refresh() {
        if (hologramLocation == null) return;
        Location saved = hologramLocation.clone();
        despawnEntities();
        hologramLocation = saved;
        buildLines();
    }

    public boolean  isSpawned()   { return !lineEntities.isEmpty(); }
    public Location getLocation() { return hologramLocation == null ? null : hologramLocation.clone(); }

    // ── Construction des lignes ────────────────────────────────────────────────

    private void buildLines() {
        StatsManager sm = plugin.getStatsManager();
        World        w  = hologramLocation.getWorld();

        List<Component> lines = new ArrayList<>();

        // Titre global
        lines.add(Component.text("══ HikaBrain ══")
                .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

        // Une section par mode
        for (GameMode mode : GameMode.values()) {
            lines.add(sep());

            // En-tête du mode
            lines.add(Component.text("◆ " + mode.getLabel())
                    .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));

            // Top joueurs : on fusionne victoires + K/D sur la même ligne
            List<Map.Entry<UUID, StatsManager.PlayerStats>> topWins =
                    sm.getTopPlayersByMode(TOP_SIZE, mode,
                            Comparator.comparingInt(s -> s.getWins(mode)));

            if (topWins.isEmpty()) {
                lines.add(gray("  Aucune donnée."));
            } else {
                int rank = 1;
                for (Map.Entry<UUID, StatsManager.PlayerStats> e : topWins) {
                    StatsManager.PlayerStats ps = e.getValue();
                    lines.add(rankColor(rank)
                            .append(Component.text(ps.name + "  ").color(NamedTextColor.WHITE))
                            .append(Component.text(ps.getWins(mode) + " wins").color(NamedTextColor.YELLOW))
                            .append(Component.text("  ⚔ ").color(NamedTextColor.DARK_GRAY))
                            .append(Component.text("K/D: " + ps.getKD(mode)).color(NamedTextColor.GREEN)));
                    rank++;
                }
                while (rank <= TOP_SIZE) { lines.add(Component.empty()); rank++; }
            }
        }

        // Séparateur + totaux globaux
        lines.add(sep());
        lines.add(gray("Parties : ").append(Component.text(sm.getTotalGames()).color(NamedTextColor.WHITE))
                .append(gray("   Captures : ")).append(Component.text(sm.getTotalCaptures()).color(NamedTextColor.WHITE)));

        // Spawner du haut vers le bas
        double topY = hologramLocation.getY() + (lines.size() - 1) * LINE_GAP;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = hologramLocation.clone();
            lineLoc.setY(topY - i * LINE_GAP);
            ArmorStand as = spawnStand(w, lineLoc, lines.get(i));
            lineEntities.add(as.getUniqueId());
        }
    }

    /** Préfixe coloré #1 / #2 / #3. */
    private static Component rankColor(int rank) {
        NamedTextColor color = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
        return Component.text("#" + rank + " ").color(color).decorate(TextDecoration.BOLD);
    }

    // ── Spawn / dépawn ─────────────────────────────────────────────────────────

    private ArmorStand spawnStand(World world, Location loc, Component name) {
        ArmorStand as = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        as.customName(name);
        as.setCustomNameVisible(true);
        as.setInvisible(true);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setSmall(true);
        as.setCollidable(false);
        as.setMarker(true);
        as.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, PDC_VALUE);
        return as;
    }

    private void despawnEntities() {
        for (UUID id : lineEntities) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        }
        lineEntities.clear();
    }

    private void purgeOrphanArmorStands(World world) {
        for (Entity e : world.getEntities()) {
            if (e instanceof ArmorStand as) {
                String val = as.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                if (PDC_VALUE.equals(val)) as.remove();
            }
        }
    }

    private static Component sep() {
        return Component.text("─────────────────────").color(NamedTextColor.DARK_GRAY);
    }

    private static Component gray(String text) {
        return Component.text(text).color(NamedTextColor.GRAY);
    }
}
