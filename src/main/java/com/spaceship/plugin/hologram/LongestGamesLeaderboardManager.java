package com.spaceship.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.spaceship.plugin.game.Team;
import com.spaceship.plugin.stats.GameHistoryManager;
import com.spaceship.plugin.stats.GameRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gère UN hologramme "anthologique" : le classement des parties SpaceShip les plus
 * longues jamais jouées, toutes arènes confondues. Contrairement aux leaderboards de
 * {@link CategoryLeaderboardManager} (une ligne = un joueur), chaque entrée ici couvre
 * plusieurs lignes puisqu'elle raconte toute une partie : sa durée, sa date, les joueurs
 * qui y étaient, et le nombre de kills/points marqués.
 *
 * Spawn/déplacement/suppression/taille via /ss longestgames hologram [remove|size <taille>].
 */
public class LongestGamesLeaderboardManager {

    private static final double LINE_GAP              = 0.27;
    private static final String HOLOGRAM_FILE         = "longestgames.yml";
    /** Nombre de parties affichées sur l'hologramme (moins que la commande /ss longestgames pour rester lisible). */
    private static final int    TOP_SIZE              = 5;
    private static final double DEFAULT_SCALE         = 1.0;
    private static final long   REFRESH_INTERVAL_TICKS = 20L * 10;

    private final HikaBrainPlugin plugin;
    private final File            cfgFile;
    private FileConfiguration     cfg;
    private final NamespacedKey   pdcKey;

    private Location        location = null;
    private double           scale    = DEFAULT_SCALE;
    private final List<UUID> lineEntities = new ArrayList<>();
    private BukkitTask       refreshTask  = null;

    public LongestGamesLeaderboardManager(HikaBrainPlugin plugin) {
        this.plugin  = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), HOLOGRAM_FILE);
        this.pdcKey  = new NamespacedKey(plugin, "longest_games_leaderboard");
        loadConfig();
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private void loadConfig() {
        if (!cfgFile.exists()) return;
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        ConfigurationSection s = cfg.getConfigurationSection("location");
        if (s == null) return;

        String worldName = s.getString("world");
        World world = worldName == null ? null : plugin.getServer().getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[SpaceShip] Leaderboard 'parties les plus longues' : monde '" + worldName + "' introuvable au démarrage.");
            return;
        }
        location = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
        scale = s.getDouble("scale", DEFAULT_SCALE);
        Chunk chunk = world.getChunkAt(location);
        world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);

        purgeOrphanArmorStands();
        buildLines();
        startRefreshTask();
    }

    private void saveConfig() {
        if (cfg == null) cfg = new YamlConfiguration();
        cfg.set("location", null);
        if (location != null) {
            cfg.set("location.world", location.getWorld().getName());
            cfg.set("location.x", location.getX());
            cfg.set("location.y", location.getY());
            cfg.set("location.z", location.getZ());
            cfg.set("location.scale", scale);
        }
        try {
            cfgFile.getParentFile().mkdirs();
            cfg.save(cfgFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[SpaceShip] Impossible de sauvegarder " + HOLOGRAM_FILE + " : " + e.getMessage());
        }
    }

    // ── API publique ───────────────────────────────────────────────────────────

    public void spawn(Location loc) {
        despawnEntities();
        location = loc.clone();
        Chunk chunk = loc.getWorld().getChunkAt(loc);
        loc.getWorld().addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        buildLines();
        startRefreshTask();
        saveConfig();
    }

    public void setScale(double scale) {
        if (location == null) return;
        this.scale = scale;
        refresh();
        saveConfig();
    }

    public boolean despawn() {
        if (location == null) return false;
        Chunk chunk = location.getWorld().getChunkAt(location);
        location.getWorld().removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        despawnEntities();
        location = null;
        stopRefreshTask();
        saveConfig();
        return true;
    }

    public void despawnAll() {
        stopRefreshTask();
        despawnEntities();
        location = null;
    }

    public boolean isSpawned() { return location != null; }

    public void refreshAll() { refresh(); }

    private void refresh() {
        if (location == null) return;
        despawnEntities();
        buildLines();
    }

    // ── Tâche de rafraîchissement automatique ──────────────────────────────────

    private void startRefreshTask() {
        if (refreshTask != null) return;
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::refresh, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    private void stopRefreshTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
        refreshTask = null;
    }

    // ── Construction des lignes ────────────────────────────────────────────────

    private void buildLines() {
        if (location == null) return;
        World w = location.getWorld();
        GameHistoryManager hm = plugin.getSpaceGameHistoryManager();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("\u23f1 TOP PARTIES LES PLUS LONGUES").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        lines.add(sep());

        List<GameRecord> top = hm.getLongestGames(TOP_SIZE);

        if (top.isEmpty()) {
            lines.add(gray("  Aucune partie enregistrée."));
        } else {
            int rank = 1;
            for (GameRecord record : top) {
                lines.addAll(buildEntryLines(rank, record));
                rank++;
            }
        }

        double lineGap = LINE_GAP * scale;
        double topY = location.getY() + (lines.size() - 1) * lineGap;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = location.clone();
            lineLoc.setY(topY - i * lineGap);
            ArmorStand as = spawnStand(w, lineLoc, lines.get(i), scale);
            lineEntities.add(as.getUniqueId());
        }
    }

    /** Construit les (jusqu'à) 3 lignes d'une entrée : rang+durée+date, joueurs, kills/points. */
    private List<Component> buildEntryLines(int rank, GameRecord record) {
        List<Component> lines = new ArrayList<>();

        lines.add(rankPrefix(rank)
                .append(Component.text(record.getFormattedDuration() + "  ").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(gray(record.getFormattedDate())));

        Component playersLine = Component.text("  ").append(teamLabel(Team.BLACK, record.getBlackPlayers()));
        if (!record.getBlackPlayers().isEmpty() && !record.getWhitePlayers().isEmpty()) {
            playersLine = playersLine.append(gray("  |  "));
        }
        playersLine = playersLine.append(teamLabel(Team.WHITE, record.getWhitePlayers()));
        lines.add(playersLine);

        lines.add(Component.text("  \u2694 ").color(NamedTextColor.RED)
                .append(Component.text(record.getTotalKills() + " kills").color(NamedTextColor.WHITE))
                .append(gray("   \u2691 "))
                .append(Component.text(record.getTotalPoints() + " points").color(NamedTextColor.AQUA))
                .append(gray("   Vainqueur: "))
                .append(Component.text(record.getWinner().getDisplayName())
                        .color(record.getWinner() == Team.BLACK ? NamedTextColor.GRAY : NamedTextColor.WHITE)));

        return lines;
    }

    private Component teamLabel(Team team, List<String> names) {
        NamedTextColor color = team == Team.BLACK ? NamedTextColor.GRAY : NamedTextColor.WHITE;
        String joined = names.isEmpty() ? "(aucun)" : String.join(", ", names);
        return Component.text(team.getDisplayName() + ": ").color(color).decorate(TextDecoration.BOLD)
                .append(Component.text(joined).color(color));
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
        return Component.text("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500").color(NamedTextColor.DARK_GRAY);
    }

    private static Component gray(String text) {
        return Component.text(text).color(NamedTextColor.GRAY);
    }

    // ── Spawn / despawn des armor stands ───────────────────────────────────────

    private ArmorStand spawnStand(World world, Location loc, Component name, double scale) {
        ArmorStand as = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        as.customName(name);
        as.setCustomNameVisible(true);
        as.setInvisible(true);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setSmall(true);
        as.setCollidable(false);
        as.setMarker(true);
        as.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, "longest_games");
        applyScale(as, scale);
        return as;
    }

    private void applyScale(LivingEntity entity, double scale) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_SCALE);
        if (attr != null) {
            attr.setBaseValue(scale);
        }
    }

    private void despawnEntities() {
        for (UUID id : lineEntities) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        }
        lineEntities.clear();
    }

    private void purgeOrphanArmorStands() {
        if (location == null || location.getWorld() == null) return;
        for (Entity e : location.getWorld().getEntities()) {
            if (e instanceof ArmorStand as) {
                String val = as.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                if ("longest_games".equals(val)) as.remove();
            }
        }
    }
}
