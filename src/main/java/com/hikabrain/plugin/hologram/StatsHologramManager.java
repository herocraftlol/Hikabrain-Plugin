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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hologrammes de STATISTIQUES PERSONNELLES : contrairement au leaderboard (voir
 * {@link CategoryLeaderboardManager}, qui affiche le même top 10 à tout le monde), ces
 * hologrammes affichent VRAIMENT les stats propres à CHAQUE joueur qui s'approche —
 * simultanément, chacun voit uniquement les siennes, jamais celles d'un autre joueur qui
 * serait aussi à proximité.
 *
 * On peut en poser plusieurs (un peu partout dans le hub, au spawn, etc.) via
 * /hb statshologram.
 *
 * ── Technique (important) ─────────────────────────────────────────────────────────
 * Une entité {@link TextDisplay} EST créée séparément POUR CHAQUE JOUEUR qui s'approche
 * (rendue invisible à tout le monde par défaut via {@link Entity#setVisibleByDefault},
 * puis montrée UNIQUEMENT à ce joueur via {@link Player#showEntity}) — c'est la méthode
 * officiellement documentée par Paper pour afficher un contenu différent à chaque joueur
 * sur une même position. Plusieurs joueurs peuvent donc se tenir au même endroit et
 * voir chacun ses propres statistiques en même temps, sans jamais se marcher dessus.
 *
 * Tant qu'un joueur reste à portée, SON entité reste affichée et à jour en continu (mise
 * à jour de son texte en place toutes les 2 secondes, jamais de placeholder "en attente"
 * pour lui) — elle n'est retirée que lorsqu'il s'éloigne ou se déconnecte.
 */
public class StatsHologramManager {

    private static final double DETECTION_RADIUS = 20.0;
    /**
     * Rayon de SORTIE, volontairement plus large que le rayon de détection/création
     * (DETECTION_RADIUS) : sans cette marge ("hystérésis"), un joueur qui se tient pile
     * à la limite du rayon pouvait faire supprimer puis recréer son hologramme à chaque
     * micro-mouvement (léger regard, tremblement de position...), ce qui donnait
     * l'impression qu'il disparaissait/réapparaissait sans arrêt.
     */
    private static final double REMOVAL_RADIUS = DETECTION_RADIUS + 3.0;
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
        double scale;
        /** Joueur -> SON entité TextDisplay personnelle et cachée à cet endroit précis. */
        final Map<UUID, UUID> playerDisplays = new HashMap<>();
    }

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), HOLOGRAMS_FILE);
        this.pdcKey = new NamespacedKey(plugin, "personal_stats_hologram");
        this.style = HologramStyle.load(plugin);
        load();
        startRefreshTask();
    }

    /** Recharge le style partagé depuis config.yml et le réapplique à tous les hologrammes existants (toutes entités personnelles comprises). */
    public void reloadStyle() {
        this.style = HologramStyle.load(plugin);
        for (HologramInstance instance : instances) {
            for (UUID entityId : instance.playerDisplays.values()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity instanceof TextDisplay display) {
                    style.apply(display, instance.scale);
                }
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
            registerInstance(loc, scale);
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
        registerInstance(location, style.getDefaultScale());
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
        for (UUID entityId : instance.playerDisplays.values()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof TextDisplay display) {
                style.apply(display, scale);
            }
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

    // ── Enregistrement d'un emplacement (aucune entité tant qu'aucun joueur n'est à proximité) ──

    private void registerInstance(Location location, double scale) {
        HologramInstance instance = new HologramInstance();
        instance.location = location.clone();
        instance.scale = scale;

        World world = location.getWorld();
        Chunk chunk = world.getChunkAt(location);
        world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);

        instances.add(instance);
    }

    private void despawnInstance(HologramInstance instance) {
        for (UUID entityId : instance.playerDisplays.values()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        instance.playerDisplays.clear();
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
            // Étape 1 : tout joueur dans le rayon de DÉTECTION obtient/garde son
            // hologramme personnel, et on lui RE-confirme la visibilité à CHAQUE cycle
            // (voir getOrCreatePersonalDisplay) — c'est le vrai correctif du
            // clignotement : le "showEntity" initial ne suffit pas forcément dans la
            // durée si le client re-suit/dé-suit l'entité (limite de distance de rendu,
            // changement de chunk, etc.), donc on le réaffirme en continu plutôt qu'une
            // seule fois à la création.
            for (Player player : findPlayersInRange(instance.location, DETECTION_RADIUS)) {
                TextDisplay display = getOrCreatePersonalDisplay(instance, player);
                if (display != null) {
                    renderPlayerStats(display, player);
                }
            }

            // Étape 2 : on ne retire l'hologramme personnel d'un joueur que s'il est
            // sorti du rayon de SORTIE, plus large que celui de détection (hystérésis).
            // Sans cette marge, un joueur pile à la limite du rayon de détection ferait
            // supprimer/recréer son hologramme à chaque minuscule mouvement.
            Set<UUID> stillWithinRemovalRadius = new HashSet<>();
            for (Player player : findPlayersInRange(instance.location, REMOVAL_RADIUS)) {
                stillWithinRemovalRadius.add(player.getUniqueId());
            }

            java.util.Iterator<Map.Entry<UUID, UUID>> it = instance.playerDisplays.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, UUID> entry = it.next();
                Player player = Bukkit.getPlayer(entry.getKey());
                boolean stillConnected = player != null && player.isOnline();
                if (!stillConnected || !stillWithinRemovalRadius.contains(entry.getKey())) {
                    Entity entity = Bukkit.getEntity(entry.getValue());
                    if (entity != null) entity.remove();
                    it.remove();
                }
            }
        }
    }

    /**
     * Récupère l'entité TextDisplay personnelle déjà créée pour ce joueur à cet endroit,
     * ou en crée une nouvelle (cachée à tout le monde, montrée uniquement à lui) s'il
     * vient d'arriver à portée. Dans tous les cas, RÉ-AFFIRME sa visibilité pour lui à
     * chaque appel (showEntity est sans effet si déjà visible, donc sans coût), ce qui
     * évite qu'elle redevienne invisible pour lui suite à un dé-suivi/re-suivi côté client.
     */
    private TextDisplay getOrCreatePersonalDisplay(HologramInstance instance, Player player) {
        UUID existingId = instance.playerDisplays.get(player.getUniqueId());
        if (existingId != null) {
            Entity existing = Bukkit.getEntity(existingId);
            if (existing instanceof TextDisplay display) {
                player.showEntity(plugin, display);
                return display;
            }
            instance.playerDisplays.remove(player.getUniqueId()); // entité disparue entre-temps, on la recrée
        }

        World world = instance.location.getWorld();
        TextDisplay display = (TextDisplay) world.spawnEntity(instance.location, EntityType.TEXT_DISPLAY);
        style.apply(display, instance.scale);
        display.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, PDC_VALUE);

        // Le cœur de la personnalisation par joueur : cachée par défaut à tout le monde,
        // montrée UNIQUEMENT à ce joueur précis (méthode officiellement recommandée par
        // Paper pour afficher un contenu différent à chaque joueur sur une même position).
        display.setVisibleByDefault(false);
        player.showEntity(plugin, display);

        instance.playerDisplays.put(player.getUniqueId(), display.getUniqueId());
        return display;
    }

    private List<Player> findPlayersInRange(Location location, double radius) {
        List<Player> players = new ArrayList<>();
        if (location.getWorld() == null) return players;
        BoundingBox box = BoundingBox.of(location, radius, radius, radius);
        for (Entity entity : location.getWorld().getNearbyEntities(box)) {
            if (entity instanceof Player player) players.add(player);
        }
        return players;
    }

    // ── Construction du contenu ────────────────────────────────────────────────

    private void renderPlayerStats(TextDisplay display, Player player) {
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

        display.text(Component.join(JoinConfiguration.separator(Component.newline()), lines));
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
}
