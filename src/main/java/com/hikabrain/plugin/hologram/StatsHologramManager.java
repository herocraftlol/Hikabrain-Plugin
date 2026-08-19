package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.levels.LevelManager;
import com.hikabrain.plugin.stats.MatchHistoryManager;
import com.hikabrain.plugin.stats.StatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
 * ── Technique (important) ─────────────────────────────────────────────────────────
 * UNE SEULE entité {@link TextDisplay} par hologramme (le vrai type "hologramme" natif
 * de Minecraft depuis 1.19.4, multi-lignes en une seule entité), JAMAIS respawnée : à
 * chaque rafraîchissement, on met juste à jour son texte en place (display.text(...)).
 * L'ancienne version utilisait 8 ArmorStand empilés (un par ligne) qui pouvaient se
 * désynchroniser ou clignoter ; un TextDisplay unique élimine complètement ce problème
 * et reste affiché en permanence, sans jamais disparaître entre deux rafraîchissements.
 * L'apparence (fond, ombre, orientation...) est partagée avec les autres hologrammes du
 * plugin, configurable via config.yml "hologram-style" — voir {@link HologramStyle}.
 */
public class StatsHologramManager {

    private static final double DETECTION_RADIUS = 5.0;
    private static final long REFRESH_INTERVAL_TICKS = 40L; // 2 secondes
    private static final String HOLOGRAMS_FILE = "personal-holograms.yml";
    private static final String PDC_VALUE = "personal-stats";

    private final HikaBrainPlugin plugin;
    private final File dataFile;
    private final NamespacedKey pdcKey;
    private HologramStyle style;

    private final List<HologramInstance> instances = new ArrayList<>();
    private BukkitTask refreshTask;

    private static class HologramInstance {
        Location location;
        UUID entityId;
        double scale;
        UUID currentlyShown; // joueur actuellement affiché, pour savoir quand re-render en placeholder
    }

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), HOLOGRAMS_FILE);
        this.pdcKey = new NamespacedKey(plugin, "personal_stats_hologram");
        this.style = HologramStyle.load(plugin);
        load();
        startRefreshTask();
    }

    /** Recharge le style partagé depuis config.yml et le réapplique à tous les hologrammes existants. */
    public void reloadStyle() {
        this.style = HologramStyle.load(plugin);
        for (HologramInstance instance : instances) {
            Entity entity = Bukkit.getEntity(instance.entityId);
            if (entity instanceof TextDisplay display) {
                style.apply(display, instance.scale);
            }
        }
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
            double scale = section.getDouble("scale", style.getDefaultScale());
            spawnInternal(loc, scale);
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
            map.put("scale", instance.scale);
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

    /** Pose un nouvel hologramme de stats personnelles à cet endroit, à l'échelle par défaut. */
    public void spawn(Location location) {
        spawnInternal(location, style.getDefaultScale());
        save();
    }

    /**
     * Change l'échelle de l'hologramme le plus proche de cet endroit (rayon de détection).
     * Renvoie false s'il n'y en a pas à proximité.
     */
    public boolean setNearestScale(Location location, double scale) {
        HologramInstance instance = findNearestInstance(location);
        if (instance == null) return false;

        instance.scale = scale;
        Entity entity = Bukkit.getEntity(instance.entityId);
        if (entity instanceof TextDisplay display) {
            style.apply(display, scale);
        }
        save();
        return true;
    }

    /**
     * Supprime l'hologramme le plus proche de cet endroit, s'il y en a un dans un rayon
     * de {@link #DETECTION_RADIUS} blocs. Renvoie true si un hologramme a été supprimé.
     */
    public boolean removeNearest(Location location) {
        HologramInstance closest = findNearestInstance(location);
        if (closest == null) return false;

        despawnInstance(closest);
        instances.remove(closest);
        save();
        return true;
    }

    private HologramInstance findNearestInstance(Location location) {
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
        return closest;
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

    private void spawnInternal(Location location, double scale) {
        HologramInstance instance = new HologramInstance();
        instance.location = location.clone();
        instance.scale = scale;

        World world = location.getWorld();
        Chunk chunk = world.getChunkAt(location);
        world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);

        TextDisplay display = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);
        style.apply(display, scale);
        display.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, PDC_VALUE);
        instance.entityId = display.getUniqueId();

        instances.add(instance);
        renderPlaceholder(instance); // contenu initial en attendant le premier rafraîchissement
    }

    private void despawnInstance(HologramInstance instance) {
        Entity entity = Bukkit.getEntity(instance.entityId);
        if (entity != null) entity.remove();
        Chunk chunk = instance.location.getWorld().getChunkAt(instance.location);
        instance.location.getWorld().removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
    }

    /** Retire tout TextDisplay orphelin (ex: après un crash serveur laissant des restes). */
    public void purgeOrphans(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof TextDisplay display) {
                String value = display.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                if (PDC_VALUE.equals(value)) display.remove();
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
        setText(instance, lines);
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

        setText(instance, lines);
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

    /** Assemble les lignes en un seul Component multi-lignes et le pousse sur l'entité (jamais de respawn). */
    private void setText(HologramInstance instance, List<Component> lines) {
        Entity entity = Bukkit.getEntity(instance.entityId);
        if (entity instanceof TextDisplay display) {
            display.text(Component.join(JoinConfiguration.separator(Component.newline()), lines));
        }
    }
}
