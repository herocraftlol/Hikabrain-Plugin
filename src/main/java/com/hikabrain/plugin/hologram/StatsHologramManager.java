package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.stats.StatsManager;
import com.hikabrain.plugin.stats.StatsManager.GameMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gère un hologramme de leaderboard HikaBrain affiché via des ArmorStands invisibles.
 *
 * L'hologramme affiche les stats (Victoires, Kills, Deaths, K/D) pour Rouge et Bleu,
 * avec un bouton de mode (1v1 / 2v2 / 3v3 / 4v4) cliquable (voir StatsHologramListener).
 *
 * Position et mode actuel sont persistés dans hologram.yml.
 *
 * Structure verticale (espacement 0.25 block) :
 *   [Ligne 0]  ══ HikaBrain Stats ══         (titre)
 *   [Ligne 1]  [ 1v1 ] [ 2v2 ] [ 3v3 ] [ 4v4 ]   (modes – ArmorStand "tab")
 *   [Ligne 2]  ──────────────────────
 *   [Ligne 3]  ❤ Équipe Rouge
 *   [Ligne 4]    Victoires : XX
 *   [Ligne 5]    Kills : XX  Deaths : XX  K/D : X.X
 *   [Ligne 6]  ──────────────────────
 *   [Ligne 7]  ❤ Équipe Bleue
 *   [Ligne 8]    Victoires : XX
 *   [Ligne 9]    Kills : XX  Deaths : XX  K/D : X.X
 *   [Ligne 10] ──────────────────────
 *   [Ligne 11] Parties jouées : XX   Captures : XX
 */
public class StatsHologramManager {

    private static final double LINE_GAP = 0.27;
    private static final String HOLOGRAM_FILE = "hologram.yml";

    private final HikaBrainPlugin plugin;
    private final File             cfgFile;
    private FileConfiguration      cfg;

    /** UUID des ArmorStands constituant l'hologramme (dans l'ordre haut→bas). */
    private final List<UUID> lineEntities = new ArrayList<>();

    /** UUID de l'ArmorStand "tab" cliquable (la ligne des modes). */
    private UUID tabEntityUUID = null;

    private Location      hologramLocation = null;
    private GameMode      currentMode      = GameMode.V1;

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin  = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), HOLOGRAM_FILE);
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
    }

    private void saveConfig() {
        if (cfg == null) cfg = new YamlConfiguration();
        cfg.set("current-mode", currentMode.getLabel());
        if (hologramLocation != null) {
            cfg.set("location.world",  hologramLocation.getWorld().getName());
            cfg.set("location.x",      hologramLocation.getX());
            cfg.set("location.y",      hologramLocation.getY());
            cfg.set("location.z",      hologramLocation.getZ());
        }
        try { cfg.save(cfgFile); }
        catch (IOException e) { plugin.getLogger().warning("Impossible de sauvegarder hologram.yml: " + e.getMessage()); }
    }

    // ── API publique ───────────────────────────────────────────────────────────

    /**
     * Crée (ou recrée) l'hologramme à la position donnée, dans le mode actuel.
     */
    public void spawn(Location loc) {
        despawn();
        hologramLocation = loc.clone();
        buildLines();
        saveConfig();
    }

    /** Supprime l'hologramme s'il existe. */
    public void despawn() {
        for (UUID id : lineEntities) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        }
        lineEntities.clear();
        tabEntityUUID = null;
        hologramLocation = null;
        saveConfig();
    }

    /** Change le mode affiché et rafraîchit les lignes. */
    public void setMode(GameMode mode) {
        this.currentMode = mode;
        refresh();
        saveConfig();
    }

    public GameMode getCurrentMode() { return currentMode; }

    /** Rafraîchit les noms de tous les ArmorStands existants. */
    public void refresh() {
        if (hologramLocation == null || lineEntities.isEmpty()) return;
        // Supprime et recrée (plus simple que de mapper chaque ligne)
        Location saved = hologramLocation.clone();
        despawn();
        hologramLocation = saved;
        buildLines();
    }

    public boolean isSpawned() { return !lineEntities.isEmpty(); }

    public Location getLocation() { return hologramLocation == null ? null : hologramLocation.clone(); }

    /** Retourne l'UUID de l'ArmorStand représentant les tabs de mode (cliquable). */
    public UUID getTabEntityUUID() { return tabEntityUUID; }

    // ── Construction de l'hologramme ───────────────────────────────────────────

    private void buildLines() {
        StatsManager sm = plugin.getStatsManager();
        GameMode     m  = currentMode;
        World        w  = hologramLocation.getWorld();

        List<Component> lines = new ArrayList<>();

        // Titre
        lines.add(Component.text("══ HikaBrain Stats ══")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD));

        // Ligne onglets modes (sera l'ArmorStand cliquable)
        lines.add(buildTabLine(m));

        // Séparateur
        lines.add(Component.text("─────────────────────").color(NamedTextColor.DARK_GRAY));

        // Équipe Rouge
        lines.add(Component.text("❤ Équipe Rouge").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        lines.add(Component.empty()
                .append(Component.text("  Victoires : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getRedWins(m)).color(NamedTextColor.RED)));
        lines.add(Component.empty()
                .append(Component.text("  Kills : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getRedKills(m)).color(NamedTextColor.RED))
                .append(Component.text("  Deaths : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getRedDeaths(m)).color(NamedTextColor.RED))
                .append(Component.text("  K/D : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getRedKD(m)).color(NamedTextColor.GOLD)));

        // Séparateur
        lines.add(Component.text("─────────────────────").color(NamedTextColor.DARK_GRAY));

        // Équipe Bleue
        lines.add(Component.text("❤ Équipe Bleue").color(NamedTextColor.BLUE).decorate(TextDecoration.BOLD));
        lines.add(Component.empty()
                .append(Component.text("  Victoires : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getBlueWins(m)).color(NamedTextColor.BLUE)));
        lines.add(Component.empty()
                .append(Component.text("  Kills : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getBlueKills(m)).color(NamedTextColor.BLUE))
                .append(Component.text("  Deaths : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getBlueDeaths(m)).color(NamedTextColor.BLUE))
                .append(Component.text("  K/D : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getBlueKD(m)).color(NamedTextColor.GOLD)));

        // Séparateur bas
        lines.add(Component.text("─────────────────────").color(NamedTextColor.DARK_GRAY));

        // Totaux
        lines.add(Component.empty()
                .append(Component.text("Parties : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getTotalGames()).color(NamedTextColor.WHITE))
                .append(Component.text("   Captures : ").color(NamedTextColor.GRAY))
                .append(Component.text(sm.getTotalCaptures()).color(NamedTextColor.WHITE)));

        // Spawner chaque ligne (haut → bas, donc index 0 est en haut)
        double topY = hologramLocation.getY() + (lines.size() - 1) * LINE_GAP;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = hologramLocation.clone();
            lineLoc.setY(topY - i * LINE_GAP);
            ArmorStand as = spawnLine(w, lineLoc, lines.get(i));
            lineEntities.add(as.getUniqueId());
            // L'index 1 = ligne des onglets = cliquable
            if (i == 1) tabEntityUUID = as.getUniqueId();
        }
    }

    /** Construit la ligne des onglets de mode avec le mode actif mis en évidence. */
    private Component buildTabLine(GameMode active) {
        Component line = Component.empty();
        for (GameMode m : GameMode.values()) {
            boolean isActive = m == active;
            Component tab = Component.text("[" + m.getLabel() + "]")
                    .color(isActive ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.BOLD, isActive)
                    .decoration(TextDecoration.ITALIC, false);
            line = line.append(Component.text(" ").color(NamedTextColor.DARK_GRAY)).append(tab);
        }
        return line;
    }

    private ArmorStand spawnLine(World world, Location loc, Component name) {
        ArmorStand as = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        as.customName(name);
        as.setCustomNameVisible(true);
        as.setInvisible(true);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setSmall(true);
        as.setCollidable(false);
        as.setMarker(true);  // pas de hitbox physique, mais encore cliquable en Minecraft 1.17+
        return as;
    }
}
