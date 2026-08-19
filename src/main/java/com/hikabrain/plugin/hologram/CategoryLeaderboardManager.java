package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.stats.StatsManager;
import com.hikabrain.plugin.stats.StatsManager.PlayerStats;
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
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gère des hologrammes de leaderboard INDÉPENDANTS, un par catégorie :
 *   - VICTOIRES / KILLS / KD / PARTIES : top 10 toutes parties confondues
 *   - 1v1 / 2v2 / 3v3 / 4v4 : top 10 par VICTOIRES dans ce format précis
 *
 * Chaque catégorie peut être spawnée/déspawnée séparément, à un endroit
 * différent, via /hb leaderboard <catégorie> [remove|size <taille>].
 *
 * ── Technique (important) ─────────────────────────────────────────────────────────
 * UNE SEULE entité {@link TextDisplay} par catégorie spawnée (multi-lignes en une seule
 * entité), JAMAIS respawnée : le rafraîchissement automatique (toutes les 10 secondes)
 * met juste à jour son texte en place. L'ancienne version supprimait puis recréait
 * toutes les lignes (des ArmorStand empilés) à CHAQUE rafraîchissement, ce qui causait
 * un clignotement visible ; ce n'est plus le cas. L'apparence (fond, ombre,
 * orientation...) est partagée avec les autres hologrammes du plugin, configurable via
 * config.yml "hologram-style" — voir {@link HologramStyle}.
 */
public class CategoryLeaderboardManager {

    // ── Catégories ─────────────────────────────────────────────────────────────

    public enum Category {
        VICTOIRES("victoires", "\uD83C\uDFC6 TOP VICTOIRES", NamedTextColor.GOLD, null),
        KILLS("kills", "\u2694 TOP KILLS", NamedTextColor.RED, null),
        KD("kd", "\uD83D\uDC80 TOP K/D", NamedTextColor.LIGHT_PURPLE, null),
        PARTIES("parties", "\uD83C\uDFAE TOP PARTIES JOUÉES", NamedTextColor.AQUA, null),
        V1V1("1v1", "\u2694 TOP 1v1", NamedTextColor.WHITE, StatsManager.GameMode.V1),
        V2V2("2v2", "\u2694 TOP 2v2", NamedTextColor.WHITE, StatsManager.GameMode.V2),
        V3V3("3v3", "\u2694 TOP 3v3", NamedTextColor.WHITE, StatsManager.GameMode.V3),
        V4V4("4v4", "\u2694 TOP 4v4", NamedTextColor.WHITE, StatsManager.GameMode.V4);

        public final String        key;
        public final String        title;
        public final NamedTextColor color;
        /** null pour les 4 catégories globales, non-null pour les 4 catégories par format (1v1/2v2/3v3/4v4). */
        public final StatsManager.GameMode mode;

        Category(String key, String title, NamedTextColor color, StatsManager.GameMode mode) {
            this.key   = key;
            this.title = title;
            this.color = color;
            this.mode  = mode;
        }

        public boolean isFormatCategory() {
            return mode != null;
        }

        public static Category fromKey(String key) {
            for (Category c : values()) {
                if (c.key.equalsIgnoreCase(key)) return c;
            }
            return null;
        }
    }

    private static final String HOLOGRAM_FILE           = "leaderboards.yml";
    private static final int    TOP_SIZE                = 10;
    /** Rafraîchissement automatique : toutes les 10 secondes. */
    private static final long   REFRESH_INTERVAL_TICKS  = 20L * 10;

    private final HikaBrainPlugin plugin;
    private final File             cfgFile;
    private FileConfiguration      cfg;
    private final NamespacedKey    pdcKey;
    private HologramStyle          style;

    // Données par catégorie active
    private final Map<Category, Location> locations = new EnumMap<>(Category.class);
    private final Map<Category, UUID>     entities  = new EnumMap<>(Category.class);
    private final Map<Category, Double>   scales    = new EnumMap<>(Category.class);
    private BukkitTask refreshTask = null;

    public CategoryLeaderboardManager(HikaBrainPlugin plugin) {
        this.plugin  = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), HOLOGRAM_FILE);
        this.pdcKey  = new NamespacedKey(plugin, "category_leaderboard");
        this.style   = HologramStyle.load(plugin);
        loadConfig();
    }

    /** Recharge le style partagé depuis config.yml et le réapplique à tous les leaderboards existants. */
    public void reloadStyle() {
        this.style = HologramStyle.load(plugin);
        for (Category c : locations.keySet()) {
            Entity entity = Bukkit.getEntity(entities.get(c));
            if (entity instanceof TextDisplay display) {
                style.apply(display, scales.getOrDefault(c, style.getDefaultScale()));
            }
        }
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private void loadConfig() {
        if (!cfgFile.exists()) return;
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        ConfigurationSection root = cfg.getConfigurationSection("locations");
        if (root == null) return;

        for (Category c : Category.values()) {
            ConfigurationSection s = root.getConfigurationSection(c.key);
            if (s == null) continue;
            String worldName = s.getString("world");
            World world = worldName == null ? null : plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[HikaBrain] Leaderboard '" + c.key + "' : monde '" + worldName + "' introuvable au démarrage.");
                continue;
            }
            Location loc = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
            locations.put(c, loc);
            scales.put(c, s.getDouble("scale", style.getDefaultScale()));
            Chunk chunk = world.getChunkAt(loc);
            world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        }

        if (!locations.isEmpty()) {
            purgeOrphans();
            for (Category c : locations.keySet()) spawnEntity(c);
            startRefreshTask();
        }
    }

    private void saveConfig() {
        if (cfg == null) cfg = new YamlConfiguration();
        cfg.set("locations", null);
        for (Map.Entry<Category, Location> e : locations.entrySet()) {
            String path = "locations." + e.getKey().key;
            Location loc = e.getValue();
            cfg.set(path + ".world", loc.getWorld().getName());
            cfg.set(path + ".x", loc.getX());
            cfg.set(path + ".y", loc.getY());
            cfg.set(path + ".z", loc.getZ());
            cfg.set(path + ".scale", scales.getOrDefault(e.getKey(), style.getDefaultScale()));
        }
        try {
            cfgFile.getParentFile().mkdirs();
            cfg.save(cfgFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[HikaBrain] Impossible de sauvegarder leaderboards.yml : " + e.getMessage());
        }
    }

    // ── API publique ───────────────────────────────────────────────────────────

    /** Spawne (ou déplace) le leaderboard d'une catégorie à la position donnée. */
    public void spawn(Category category, Location loc) {
        despawnEntity(category);
        locations.put(category, loc.clone());
        scales.putIfAbsent(category, style.getDefaultScale());
        Chunk chunk = loc.getWorld().getChunkAt(loc);
        loc.getWorld().addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        spawnEntity(category);
        startRefreshTask();
        saveConfig();
    }

    /**
     * Modifie la taille (échelle) de l'hologramme d'une catégorie déjà spawnée, en place
     * (pas besoin de reconstruire quoi que ce soit).
     * @param scale multiplicateur de taille (1.0 = taille normale).
     */
    public void setScale(Category category, double scale) {
        if (!locations.containsKey(category)) return;
        scales.put(category, scale);
        Entity entity = Bukkit.getEntity(entities.get(category));
        if (entity instanceof TextDisplay display) {
            style.apply(display, scale);
        }
        saveConfig();
    }

    /** Supprime le leaderboard d'une catégorie. Renvoie false s'il n'était pas spawné. */
    public boolean despawn(Category category) {
        Location loc = locations.get(category);
        if (loc == null) return false;
        Chunk chunk = loc.getWorld().getChunkAt(loc);
        loc.getWorld().removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        despawnEntity(category);
        locations.remove(category);
        scales.remove(category);
        saveConfig();
        if (locations.isEmpty()) stopRefreshTask();
        return true;
    }

    /** Supprime tous les leaderboards (appelé à l'arrêt du plugin). */
    public void despawnAll() {
        stopRefreshTask();
        for (Category c : Category.values()) despawnEntity(c);
        locations.clear();
        scales.clear();
    }

    public boolean isSpawned(Category category) { return locations.containsKey(category); }

    /** Rafraîchit manuellement toutes les catégories spawnées (relit la DB), en place. */
    public void refreshAll() {
        for (Category c : new ArrayList<>(locations.keySet())) refreshText(c);
    }

    // ── Tâche de rafraîchissement automatique ──────────────────────────────────

    private void startRefreshTask() {
        if (refreshTask != null) return;
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::refreshAll,
                REFRESH_INTERVAL_TICKS,
                REFRESH_INTERVAL_TICKS
        );
    }

    private void stopRefreshTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
        refreshTask = null;
    }

    // ── Construction du contenu ────────────────────────────────────────────────

    private void spawnEntity(Category category) {
        Location loc = locations.get(category);
        if (loc == null) return;

        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        double scale = scales.getOrDefault(category, style.getDefaultScale());
        style.apply(display, scale);
        display.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, "leaderboard");
        entities.put(category, display.getUniqueId());

        refreshText(category);
    }

    private void refreshText(Category category) {
        Entity entity = Bukkit.getEntity(entities.get(category));
        if (!(entity instanceof TextDisplay display)) return;

        StatsManager sm = plugin.getStatsManager();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(category.title).color(category.color).decorate(TextDecoration.BOLD));
        lines.add(sep());

        List<Map.Entry<UUID, PlayerStats>> top = fetchTop(sm, category);

        if (top.isEmpty()) {
            lines.add(gray("  Aucune donnée."));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, PlayerStats> entry : top) {
                lines.add(buildLine(category, rank, entry.getValue()));
                rank++;
            }
        }

        display.text(Component.join(JoinConfiguration.separator(Component.newline()), lines));
    }

    private List<Map.Entry<UUID, PlayerStats>> fetchTop(StatsManager sm, Category category) {
        if (category.isFormatCategory()) {
            Comparator<PlayerStats> comparator = Comparator.comparingInt(s -> s.getWins(category.mode));
            return sm.getTopPlayersByMode(TOP_SIZE, category.mode, comparator);
        }
        Comparator<PlayerStats> comparator = switch (category) {
            case VICTOIRES -> Comparator.comparingInt(s -> s.gamesWon);
            case KILLS     -> Comparator.comparingInt(s -> s.kills);
            case KD        -> Comparator.comparingDouble(PlayerStats::getKD);
            case PARTIES   -> Comparator.comparingInt(s -> s.gamesPlayed);
            default        -> Comparator.comparingInt(s -> s.gamesWon); // inatteignable (catégories de format déjà filtrées ci-dessus)
        };
        return sm.getTopPlayers(TOP_SIZE, comparator);
    }

    private Component buildLine(Category category, int rank, PlayerStats ps) {
        Component prefix = rankPrefix(rank)
                .append(Component.text(ps.name + "  ").color(NamedTextColor.WHITE));

        if (category.isFormatCategory()) {
            StatsManager.GameMode m = category.mode;
            return prefix
                    .append(Component.text(ps.getWins(m) + " wins").color(NamedTextColor.YELLOW))
                    .append(gray("  K/D: "))
                    .append(Component.text(String.valueOf(ps.getKD(m))).color(NamedTextColor.GREEN))
                    .append(gray("  (" + ps.getGamesPlayed(m) + " parties)"));
        }

        return switch (category) {
            case VICTOIRES -> prefix
                    .append(Component.text(ps.gamesWon + " wins").color(NamedTextColor.YELLOW))
                    .append(gray("  K/D: "))
                    .append(Component.text(String.valueOf(ps.getKD())).color(NamedTextColor.GREEN));
            case KILLS -> prefix
                    .append(Component.text(ps.kills + " kills").color(NamedTextColor.RED))
                    .append(gray("  morts: "))
                    .append(Component.text(String.valueOf(ps.deaths)).color(NamedTextColor.GRAY));
            case KD -> prefix
                    .append(Component.text("K/D: " + ps.getKD()).color(NamedTextColor.GREEN))
                    .append(gray("  (" + ps.kills + "K / " + ps.deaths + "D)"));
            case PARTIES -> {
                double wr = ps.gamesPlayed > 0
                        ? Math.round((double) ps.gamesWon / ps.gamesPlayed * 1000.0) / 10.0
                        : 0.0;
                yield prefix
                        .append(Component.text(ps.gamesPlayed + " parties").color(NamedTextColor.AQUA))
                        .append(gray("  WR: " + wr + "%"));
            }
            default -> prefix; // inatteignable (catégories de format déjà gérées au-dessus)
        };
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

    // ── Spawn / despawn ────────────────────────────────────────────────────────

    private void despawnEntity(Category category) {
        UUID id = entities.remove(category);
        if (id == null) return;
        Entity e = plugin.getServer().getEntity(id);
        if (e != null) e.remove();
    }

    private void purgeOrphans() {
        Map<String, World> worlds = new java.util.HashMap<>();
        for (Location loc : locations.values()) worlds.put(loc.getWorld().getName(), loc.getWorld());
        for (World world : worlds.values()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof TextDisplay display) {
                    String val = display.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                    if ("leaderboard".equals(val)) display.remove();
                }
            }
        }
    }
}
