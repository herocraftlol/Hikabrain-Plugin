package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.stats.StatsManager;
import com.hikabrain.plugin.stats.StatsManager.PlayerStats;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hologramme de leaderboard HikaBrain — affiche les meilleurs joueurs
 * par domaine (victoires, kills, K/D, parties jouées) et se rafraîchit
 * automatiquement depuis la base de données.
 *
 * ── Structure visuelle ────────────────────────────────────────────────────────
 *   ══ HikaBrain Leaderboard ══
 *   ─────────────────────
 *   🏆 TOP VICTOIRES
 *   #1 Joueur1   42 wins   K/D: 4.2
 *   #2 Joueur2   39 wins   K/D: 3.1
 *   #3 Joueur3   28 wins   K/D: 2.8
 *   ─────────────────────
 *   ⚔ TOP KILLS
 *   #1 ...
 *   ─────────────────────
 *   💀 TOP K/D
 *   #1 ...
 *   ─────────────────────
 *   🎮 TOP PARTIES JOUÉES
 *   #1 ...
 *   ─────────────────────
 *   Parties : XX   Captures : XX
 *
 * Rafraîchissement automatique toutes les REFRESH_INTERVAL_TICKS ticks.
 */
public class StatsHologramManager {

    // ── Constantes ─────────────────────────────────────────────────────────────

    private static final double LINE_GAP               = 0.27;
    private static final String HOLOGRAM_FILE          = "hologram.yml";
    private static final String PDC_VALUE              = "stats";
    private static final int    TOP_SIZE               = 3;
    /** Rafraîchissement automatique : 20 ticks × 10 = toutes les 10 secondes. */
    private static final long   REFRESH_INTERVAL_TICKS = 20L * 10;

    // ── Interface fonctionnelle interne ────────────────────────────────────────

    @FunctionalInterface
    private interface TopLineBuilder {
        Component build(int rank, PlayerStats ps);
    }

    // ── Champs ─────────────────────────────────────────────────────────────────

    private final HikaBrainPlugin plugin;
    private final File             cfgFile;
    private FileConfiguration      cfg;
    private final NamespacedKey    pdcKey;

    private final List<UUID> lineEntities    = new ArrayList<>();
    private Location         hologramLocation = null;
    private BukkitTask       refreshTask      = null;

    // ── Constructeur ───────────────────────────────────────────────────────────

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin  = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), HOLOGRAM_FILE);
        this.pdcKey  = new NamespacedKey(plugin, "hologram");
        loadConfig();
    }

    // ── Config persistante ─────────────────────────────────────────────────────

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
                startRefreshTask();
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

    /** Spawne l'hologramme et démarre le rafraîchissement automatique. */
    public void spawn(Location loc) {
        despawnEntities();
        stopRefreshTask();
        hologramLocation = loc.clone();
        Chunk chunk = loc.getWorld().getChunkAt(loc);
        loc.getWorld().addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        buildLines();
        startRefreshTask();
        saveConfig();
    }

    /** Supprime l'hologramme et arrête le rafraîchissement automatique. */
    public void despawn() {
        stopRefreshTask();
        if (hologramLocation != null) {
            Chunk chunk = hologramLocation.getWorld().getChunkAt(hologramLocation);
            hologramLocation.getWorld().removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        }
        despawnEntities();
        hologramLocation = null;
        saveConfig();
    }

    /** Rafraîchit manuellement l'hologramme (relit la DB). */
    public void refresh() {
        if (hologramLocation == null) return;
        Location saved = hologramLocation.clone();
        despawnEntities();
        hologramLocation = saved;
        buildLines();
    }

    public boolean  isSpawned()   { return !lineEntities.isEmpty(); }
    public Location getLocation() { return hologramLocation == null ? null : hologramLocation.clone(); }

    // ── Tâche de rafraîchissement automatique ──────────────────────────────────

    private void startRefreshTask() {
        stopRefreshTask();
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::refresh,
                REFRESH_INTERVAL_TICKS,
                REFRESH_INTERVAL_TICKS
        );
    }

    private void stopRefreshTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }
        refreshTask = null;
    }

    // ── Construction des lignes ────────────────────────────────────────────────

    /**
     * Construit toutes les lignes de l'hologramme depuis le StatsManager.
     *
     * Les stats affichées sont les mêmes que /hb stats (global) :
     *  • gamesWon     → section Victoires
     *  • kills/deaths → section Kills et section K/D
     *  • gamesPlayed  → section Parties jouées
     */
    private void buildLines() {
        StatsManager sm = plugin.getStatsManager();
        World        w  = hologramLocation.getWorld();
        List<Component> lines = new ArrayList<>();

        // ── Titre ──────────────────────────────────────────────────────────────
        lines.add(Component.text("══ HikaBrain Leaderboard ══")
                .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

        // ── 🏆 Top Victoires ──────────────────────────────────────────────────
        lines.add(sep());
        lines.add(Component.text("🏆 TOP VICTOIRES")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        addTop(lines, sm, Comparator.comparingInt(s -> s.gamesWon), (rank, ps) ->
                rankPrefix(rank)
                        .append(Component.text(ps.name + "  ").color(NamedTextColor.WHITE))
                        .append(Component.text(ps.gamesWon + " wins").color(NamedTextColor.YELLOW))
                        .append(Component.text("  K/D: ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.valueOf(ps.getKD())).color(NamedTextColor.GREEN))
        );

        // ── ⚔ Top Kills ───────────────────────────────────────────────────────
        lines.add(sep());
        lines.add(Component.text("⚔ TOP KILLS")
                .color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        addTop(lines, sm, Comparator.comparingInt(s -> s.kills), (rank, ps) ->
                rankPrefix(rank)
                        .append(Component.text(ps.name + "  ").color(NamedTextColor.WHITE))
                        .append(Component.text(ps.kills + " kills").color(NamedTextColor.RED))
                        .append(Component.text("  morts: ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.valueOf(ps.deaths)).color(NamedTextColor.GRAY))
        );

        // ── 💀 Top K/D ────────────────────────────────────────────────────────
        lines.add(sep());
        lines.add(Component.text("💀 TOP K/D")
                .color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        addTop(lines, sm, Comparator.comparingDouble(PlayerStats::getKD), (rank, ps) ->
                rankPrefix(rank)
                        .append(Component.text(ps.name + "  ").color(NamedTextColor.WHITE))
                        .append(Component.text("K/D: " + ps.getKD()).color(NamedTextColor.GREEN))
                        .append(Component.text("  (" + ps.kills + "K / " + ps.deaths + "D)").color(NamedTextColor.GRAY))
        );

        // ── 🎮 Top Parties jouées ─────────────────────────────────────────────
        lines.add(sep());
        lines.add(Component.text("🎮 TOP PARTIES JOUÉES")
                .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        addTop(lines, sm, Comparator.comparingInt(s -> s.gamesPlayed), (rank, ps) -> {
            double wr = ps.gamesPlayed > 0
                    ? Math.round((double) ps.gamesWon / ps.gamesPlayed * 1000.0) / 10.0
                    : 0.0;
            return rankPrefix(rank)
                    .append(Component.text(ps.name + "  ").color(NamedTextColor.WHITE))
                    .append(Component.text(ps.gamesPlayed + " parties").color(NamedTextColor.AQUA))
                    .append(Component.text("  WR: " + wr + "%").color(NamedTextColor.YELLOW));
        });

        // ── Footer ────────────────────────────────────────────────────────────
        lines.add(sep());
        lines.add(gray("Parties : ")
                .append(Component.text(sm.getTotalGames()).color(NamedTextColor.WHITE))
                .append(gray("   Captures : "))
                .append(Component.text(sm.getTotalCaptures()).color(NamedTextColor.WHITE)));

        // ── Spawn des armor stands (du haut vers le bas) ───────────────────────
        double topY = hologramLocation.getY() + (lines.size() - 1) * LINE_GAP;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = hologramLocation.clone();
            lineLoc.setY(topY - i * LINE_GAP);
            ArmorStand as = spawnStand(w, lineLoc, lines.get(i));
            lineEntities.add(as.getUniqueId());
        }
    }

    /**
     * Ajoute TOP_SIZE lignes de joueurs triées par comparator.
     * Si moins de TOP_SIZE joueurs ont des stats, des lignes vides complètent
     * pour conserver une hauteur d'hologramme stable.
     */
    private void addTop(List<Component> lines,
                        StatsManager sm,
                        Comparator<PlayerStats> comparator,
                        TopLineBuilder builder) {
        List<Map.Entry<UUID, PlayerStats>> top = sm.getTopPlayers(TOP_SIZE, comparator);
        if (top.isEmpty()) {
            lines.add(gray("  Aucune donnée."));
            for (int i = 1; i < TOP_SIZE; i++) lines.add(Component.empty());
        } else {
            int rank = 1;
            for (Map.Entry<UUID, PlayerStats> entry : top) {
                lines.add(builder.build(rank, entry.getValue()));
                rank++;
            }
            for (int i = top.size(); i < TOP_SIZE; i++) lines.add(Component.empty());
        }
    }

    // ── Helpers visuels ────────────────────────────────────────────────────────

    private static Component rankPrefix(int rank) {
        NamedTextColor color = switch (rank) {
            case 1  -> NamedTextColor.GOLD;
            case 2  -> NamedTextColor.GRAY;
            case 3  -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
        return Component.text("#" + rank + " ").color(color).decorate(TextDecoration.BOLD);
    }

    private static Component sep() {
        return Component.text("─────────────────────").color(NamedTextColor.DARK_GRAY);
    }

    private static Component gray(String text) {
        return Component.text(text).color(NamedTextColor.GRAY);
    }

    // ── Spawn / dépawn des armor stands ────────────────────────────────────────

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
}
