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
 * Hologramme de leaderboard HikaBrain — meilleurs JOUEURS.
 *
 * ── Structure visuelle ────────────────────────────────────────────────────────
 *   ══ HikaBrain ══                     (titre, AQUA BOLD)
 *   [1v1] [2v2] [3v3] [4v4]            (onglets cliquables — change la catégorie)
 *   ─────────────────────
 *   🏆 Top Victoires
 *     #1 Joueur1  12 wins
 *     #2 Joueur2   9 wins
 *     #3 Joueur3   7 wins
 *   ─────────────────────
 *   ⚔ Meilleur K/D
 *     #1 Joueur4  4.2
 *     #2 Joueur1  3.1
 *     #3 Joueur5  2.8
 *   ─────────────────────
 *   Parties : XX   Captures : XX
 *
 * Cliquer sur l'ArmorStand tabs fait défiler : 1v1 → 2v2 → 3v3 → 4v4 → 1v1…
 * L'hologramme est entièrement indépendant des arènes.
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
    private final NamespacedKey tabKey;

    private final List<UUID> lineEntities = new ArrayList<>();
    private UUID             tabEntityUUID = null;

    private Location hologramLocation = null;
    private GameMode currentMode      = GameMode.V1;

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin  = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), HOLOGRAM_FILE);
        this.pdcKey  = new NamespacedKey(plugin, "hologram");
        this.tabKey  = new NamespacedKey(plugin, "hologram_tab");
        loadConfig();
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    private void loadConfig() {
        if (!cfgFile.exists()) return;
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        String modeStr = cfg.getString("current-mode", "1v1");
        for (GameMode m : GameMode.values()) {
            if (m.getLabel().equals(modeStr)) { currentMode = m; break; }
        }

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
        cfg.set("current-mode", currentMode.getLabel());
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

    public void setMode(GameMode mode) {
        this.currentMode = mode;
        refresh();
        saveConfig();
    }

    public void refresh() {
        if (hologramLocation == null) return;
        Location saved = hologramLocation.clone();
        despawnEntities();
        hologramLocation = saved;
        buildLines();
    }

    public boolean  isSpawned()        { return !lineEntities.isEmpty(); }
    public Location getLocation()      { return hologramLocation == null ? null : hologramLocation.clone(); }
    public GameMode getCurrentMode()   { return currentMode; }
    public UUID     getTabEntityUUID() { return tabEntityUUID; }

    // ── Construction des lignes ────────────────────────────────────────────────

    private void buildLines() {
        StatsManager sm   = plugin.getStatsManager();
        GameMode     mode = currentMode;
        World        w    = hologramLocation.getWorld();

        List<Component> lines = new ArrayList<>();

        // 0 — Titre
        lines.add(Component.text("══ HikaBrain ══")
                .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

        // 1 — Onglets (cliquable)
        lines.add(buildTabLine(mode));

        // 2 — Séparateur
        lines.add(sep());

        // 3 — Titre section victoires
        lines.add(Component.text("🏆 Top Victoires (" + mode.getLabel() + ")")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        // 4‑6 — Top 3 victoires
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
                        .append(Component.text(ps.getWins(mode) + " wins").color(NamedTextColor.YELLOW)));
                rank++;
            }
            // Compléter avec des lignes vides si moins de 3
            while (rank <= TOP_SIZE) { lines.add(Component.empty()); rank++; }
        }

        // Séparateur
        lines.add(sep());

        // Titre section K/D
        lines.add(Component.text("⚔ Meilleur K/D (" + mode.getLabel() + ")")
                .color(NamedTextColor.RED).decorate(TextDecoration.BOLD));

        // Top 3 K/D
        List<Map.Entry<UUID, StatsManager.PlayerStats>> topKD =
                sm.getTopPlayersByMode(TOP_SIZE, mode,
                        Comparator.comparingDouble(s -> s.getKD(mode)));
        if (topKD.isEmpty()) {
            lines.add(gray("  Aucune donnée."));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, StatsManager.PlayerStats> e : topKD) {
                StatsManager.PlayerStats ps = e.getValue();
                lines.add(rankColor(rank)
                        .append(Component.text(ps.name + "  ").color(NamedTextColor.WHITE))
                        .append(Component.text("K/D: " + ps.getKD(mode)).color(NamedTextColor.GREEN)));
                rank++;
            }
            while (rank <= TOP_SIZE) { lines.add(Component.empty()); rank++; }
        }

        // Séparateur final
        lines.add(sep());

        // Totaux globaux
        lines.add(gray("Parties : ").append(Component.text(sm.getTotalGames()).color(NamedTextColor.WHITE))
                .append(gray("   Captures : ")).append(Component.text(sm.getTotalCaptures()).color(NamedTextColor.WHITE)));

        // Spawner du haut vers le bas
        double topY = hologramLocation.getY() + (lines.size() - 1) * LINE_GAP;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = hologramLocation.clone();
            lineLoc.setY(topY - i * LINE_GAP);
            ArmorStand as = spawnStand(w, lineLoc, lines.get(i));
            lineEntities.add(as.getUniqueId());
            if (i == 1) {   // l'ArmorStand des onglets
                tabEntityUUID = as.getUniqueId();
                as.getPersistentDataContainer().set(tabKey, PersistentDataType.STRING, "tab");
            }
        }
    }

    private Component buildTabLine(GameMode active) {
        Component line = Component.empty();
        for (GameMode m : GameMode.values()) {
            boolean on = m == active;
            line = line
                    .append(Component.text(" ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text("[" + m.getLabel() + "]")
                            .color(on ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.BOLD,   on)
                            .decoration(TextDecoration.ITALIC, false));
        }
        return line;
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
        tabEntityUUID = null;
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
