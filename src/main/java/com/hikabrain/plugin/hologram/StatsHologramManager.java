package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.levels.LevelManager;
import com.hikabrain.plugin.stats.MatchHistoryManager;
import com.hikabrain.plugin.stats.StatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hologrammes de STATISTIQUES PERSONNELLES : contrairement au leaderboard (voir
 * {@link CategoryLeaderboardManager}, qui affiche le même top 10 à tout le monde), ces
 * hologrammes affichent les stats du joueur actuellement le plus proche — chaque joueur
 * qui s'approche voit donc SES PROPRES statistiques.
 *
 * On peut en poser plusieurs (un peu partout dans le hub, au spawn, etc.) via
 * /hb statshologram. Chacun se met à jour tout seul toutes les 2 secondes, en cherchant
 * le joueur le plus proche dans un petit rayon.
 *
 * ── Contenu affiché ────────────────────────────────────────────────────────────
 *   ✦ Statistiques HikaBrain ✦
 *   <Pseudo>
 *   Niveau X  |  Y points
 *   ⚔ K/D: Z  (K kills / D morts)
 *   🏆 W victoires  -  🎮 P parties
 *   ⏱ Temps de jeu total
 *   ─────────────
 *   📅 Jour #.. 🗓 Semaine #.. 🕰 Total #..
 *
 * Techniquement : un ArmorStand invisible par ligne (nombre de lignes FIXE, seul le
 * texte change à chaque rafraîchissement) — jamais respawné, juste mis à jour en place,
 * pour rester fluide et ne jamais scintiller.
 */
public class StatsHologramManager {

    private static final double LINE_GAP = 0.27;
    private static final int LINE_COUNT = 8;
    private static final double DETECTION_RADIUS = 5.0;
    private static final long REFRESH_INTERVAL_TICKS = 40L; // 2 secondes
    private static final String HOLOGRAMS_FILE = "personal-holograms.yml";
    private static final String PDC_VALUE = "personal-stats";

    private final HikaBrainPlugin plugin;
    private final File dataFile;
    private final NamespacedKey pdcKey;

    private final List<HologramInstance> instances = new ArrayList<>();
    private BukkitTask refreshTask;

    private static class HologramInstance {
        Location location;
        final List<UUID> lineEntityIds = new ArrayList<>();
        UUID currentlyShown; // joueur actuellement affiché, pour éviter de re-render inutilement
    }

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), HOLOGRAMS_FILE);
        this.pdcKey = new NamespacedKey(plugin, "personal_stats_hologram");
        load();
        startRefreshTask();
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<?> rawList = config.getList("locations");
        if (rawList == null) return;

        for (Object entry : rawList) {
            if (!(entry instanceof ConfigurationSection section)) continue;
            String worldName = section.getString("world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world == null) {
                plugin.getLogger().warning("[HikaBrain] Hologramme de stats personnelles : monde '" + worldName + "' introuvable, ignoré.");
                continue;
            }
            Location loc = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
            spawnInternal(loc);
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        for (HologramInstance instance : instances) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("world", instance.location.getWorld().getName());
            map.put("x", instance.location.getX());
            map.put("y", instance.location.getY());
            map.put("z", instance.location.getZ());
            list.add(map);
        }
        config.set("locations", list);
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[HikaBrain] Impossible de sauvegarder " + HOLOGRAMS_FILE + " : " + e.getMessage());
        }
    }

    // ── API publique ───────────────────────────────────────────────────────────

    /** Pose un nouvel hologramme de stats personnelles à cet endroit. */
    public void spawn(Location location) {
        spawnInternal(location);
        save();
    }

    /**
     * Supprime l'hologramme le plus proche de cet endroit, s'il y en a un dans un rayon
     * de {@link #DETECTION_RADIUS} blocs. Renvoie true si un hologramme a été supprimé.
     */
    public boolean removeNearest(Location location) {
        HologramInstance closest = null;
        double closestDistSq = DETECTION_RADIUS * DETECTION_RADIUS;

        for (HologramInstance instance : instances) {
            if (!instance.location.getWorld().equals(location.getWorld())) continue;
            double distSq = instance.location.distanceSquared(location);
            if (distSq <= closestDistSq) {
                closestDistSq = distSq;
                closest = instance;
            }
        }

        if (closest == null) return false;

        despawnInstance(closest);
        instances.remove(closest);
        save();
        return true;
    }

    public void despawnAll() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (HologramInstance instance : instances) {
            despawnInstance(instance);
        }
        instances.clear();
    }

    public int count() {
        return instances.size();
    }

    // ── Construction / suppression des entités ───────────────────────────────────

    private void spawnInternal(Location location) {
        HologramInstance instance = new HologramInstance();
        instance.location = location.clone();

        World world = location.getWorld();
        Chunk chunk = world.getChunkAt(location);
        world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);

        double topY = location.getY() + (LINE_COUNT - 1) * LINE_GAP;
        for (int i = 0; i < LINE_COUNT; i++) {
            Location lineLoc = location.clone();
            lineLoc.setY(topY - i * LINE_GAP);
            ArmorStand stand = spawnLine(world, lineLoc, Component.empty());
            instance.lineEntityIds.add(stand.getUniqueId());
        }

        instances.add(instance);
        renderPlaceholder(instance); // contenu initial en attendant le premier rafraîchissement
    }

    private void despawnInstance(HologramInstance instance) {
        for (UUID id : instance.lineEntityIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        Chunk chunk = instance.location.getWorld().getChunkAt(instance.location);
        instance.location.getWorld().removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
    }

    private ArmorStand spawnLine(World world, Location loc, Component name) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.customName(name);
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.setCollidable(false);
        stand.setMarker(true);
        stand.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, PDC_VALUE);
        return stand;
    }

    /** Retire tout ArmorStand orphelin (ex: après un crash serveur laissant des restes). */
    public void purgeOrphans(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ArmorStand stand) {
                String value = stand.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                if (PDC_VALUE.equals(value)) stand.remove();
            }
        }
    }

    // ── Rafraîchissement automatique ──────────────────────────────────────────

    private void startRefreshTask() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    private void refreshAll() {
        for (HologramInstance instance : instances) {
            Player nearest = findNearestPlayer(instance.location);
            if (nearest == null) {
                if (instance.currentlyShown != null) {
                    renderPlaceholder(instance);
                    instance.currentlyShown = null;
                }
                continue;
            }
            // On peut se permettre de re-render à chaque cycle même si c'est le même
            // joueur : ses stats peuvent avoir changé (partie terminée entre-temps).
            renderPlayerStats(instance, nearest);
            instance.currentlyShown = nearest.getUniqueId();
        }
    }

    private Player findNearestPlayer(Location location) {
        if (location.getWorld() == null) return null;
        BoundingBox box = BoundingBox.of(location, DETECTION_RADIUS, DETECTION_RADIUS, DETECTION_RADIUS);
        Player nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Entity entity : location.getWorld().getNearbyEntities(box)) {
            if (entity instanceof Player player) {
                double distSq = player.getLocation().distanceSquared(location);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    // ── Construction du contenu ────────────────────────────────────────────────

    private void renderPlaceholder(HologramInstance instance) {
        List<Component> lines = new ArrayList<>();
        lines.add(title());
        lines.add(Component.text("En attente d'un joueur...").color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
        for (int i = 2; i < LINE_COUNT; i++) lines.add(Component.empty());
        applyLines(instance, lines);
    }

    private void renderPlayerStats(HologramInstance instance, Player player) {
        UUID uuid = player.getUniqueId();
        StatsManager statsManager = plugin.getStatsManager();
        LevelManager levelManager = plugin.getLevelManager();
        MatchHistoryManager historyManager = plugin.getMatchHistoryManager();

        StatsManager.PlayerStats stats = statsManager.getPlayerStats(uuid, player.getName());
        int level = levelManager.getLevel(uuid);
        int points = levelManager.getPoints(uuid);

        LocalDate[] todayRange = historyManager.rangeForToday();
        LocalDate[] weekRange = historyManager.rangeForWeek();
        int rankAllTime = levelManager.getPointsRank(uuid);
        int rankWeek = historyManager.getPointsRankForPeriod(uuid, weekRange[0], weekRange[1]);
        int rankToday = historyManager.getPointsRankForPeriod(uuid, todayRange[0], todayRange[1]);

        List<Component> lines = new ArrayList<>();
        lines.add(title());
        lines.add(Component.text(player.getName()).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        lines.add(Component.text("Niveau ").color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(level)).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                .append(Component.text("  |  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(points + " pts").color(NamedTextColor.YELLOW)));
        lines.add(Component.text("\u2694 K/D: ").color(NamedTextColor.RED)
                .append(Component.text(String.valueOf(stats.getKD())).color(NamedTextColor.GREEN))
                .append(Component.text("  (" + stats.kills + "K / " + stats.deaths + "D)").color(NamedTextColor.GRAY)));
        lines.add(Component.text("\uD83C\uDFC6 ").color(NamedTextColor.GOLD)
                .append(Component.text(stats.gamesWon + " victoires").color(NamedTextColor.GOLD))
                .append(Component.text("  -  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("\uD83C\uDFAE ").color(NamedTextColor.AQUA))
                .append(Component.text(stats.gamesPlayed + " parties").color(NamedTextColor.AQUA)));
        lines.add(Component.text("\u23F1 ").color(NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(formatPlaytime(stats.playtimeSeconds)).color(NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" de jeu").color(NamedTextColor.GRAY)));
        lines.add(Component.text("─────────────").color(NamedTextColor.DARK_GRAY));
        lines.add(rankLine(rankToday, rankWeek, rankAllTime));

        applyLines(instance, lines);
    }

    private Component title() {
        return Component.text("\u2726 Statistiques HikaBrain \u2726").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    private Component rankLine(int rankToday, int rankWeek, int rankAllTime) {
        return Component.text("\uD83D\uDCC5").color(NamedTextColor.GREEN)
                .append(rankValue(rankToday))
                .append(Component.text("  \uD83D\uDDD3").color(NamedTextColor.YELLOW))
                .append(rankValue(rankWeek))
                .append(Component.text("  \u23F0").color(NamedTextColor.GOLD))
                .append(rankValue(rankAllTime));
    }

    private Component rankValue(int rank) {
        return Component.text(rank > 0 ? "#" + rank : "-").color(NamedTextColor.WHITE);
    }

    private String formatPlaytime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "h" + (minutes < 10 ? "0" : "") + minutes;
        if (minutes > 0) return minutes + " min";
        return totalSeconds + " s";
    }

    private void applyLines(HologramInstance instance, List<Component> lines) {
        for (int i = 0; i < instance.lineEntityIds.size() && i < lines.size(); i++) {
            Entity entity = Bukkit.getEntity(instance.lineEntityIds.get(i));
            if (entity instanceof ArmorStand stand) {
                stand.customName(lines.get(i));
            }
        }
    }
}
