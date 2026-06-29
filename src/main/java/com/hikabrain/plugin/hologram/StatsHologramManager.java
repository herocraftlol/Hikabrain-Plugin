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
import java.util.List;
import java.util.UUID;

/**
 * Gère UN hologramme de leaderboard HikaBrain, placé librement dans n'importe quel monde,
 * totalement indépendant des arènes et du lobby du jeu.
 *
 * ── Persistance ──────────────────────────────────────────────────────────────
 *  • La position et le mode actif sont écrits dans hologram.yml à chaque modification.
 *  • Chaque ArmorStand reçoit un tag PDC (PersistentDataContainer) "hikabrain:hologram"
 *    avec la valeur "stats". Au (re)démarrage, le chunk est forcé en mémoire et tous
 *    les ArmorStands portant ce tag sont supprimés AVANT de respawner l'hologramme.
 *    Ainsi, même si les UUIDs changent entre redémarrages, on ne laisse jamais de
 *    doublons orphelins.
 *
 * ── Structure visuelle (espacement 0.27 block, haut → bas) ───────────────────
 *   ══ HikaBrain Stats ══                           (titre, AQUA BOLD)
 *   [1v1] [2v2] [3v3] [4v4]                        (tabs cliquables)
 *   ─────────────────────                           (séparateur)
 *   ❤ Équipe Rouge                                  (BOLD RED)
 *     Victoires : XX
 *     Kills : XX  Deaths : XX  K/D : X.X
 *   ─────────────────────
 *   ❤ Équipe Bleue                                  (BOLD BLUE)
 *     Victoires : XX
 *     Kills : XX  Deaths : XX  K/D : X.X
 *   ─────────────────────
 *   Parties : XX   Captures : XX
 */
public class StatsHologramManager {

    // ── Constantes ─────────────────────────────────────────────────────────────

    private static final double LINE_GAP       = 0.27;
    private static final String HOLOGRAM_FILE  = "hologram.yml";
    /** Valeur stockée dans le PDC pour identifier nos ArmorStands. */
    private static final String PDC_VALUE      = "stats";

    // ── État ───────────────────────────────────────────────────────────────────

    private final HikaBrainPlugin plugin;
    private final File             cfgFile;
    private FileConfiguration      cfg;

    /** Clé PDC pour marquer tous nos ArmorStands. */
    private final NamespacedKey pdcKey;
    /** Clé PDC supplémentaire pour identifier spécifiquement l'ArmorStand "tabs". */
    private final NamespacedKey tabKey;

    /** UUIDs des ArmorStands actifs (haut → bas). */
    private final List<UUID> lineEntities = new ArrayList<>();

    /** UUID de l'ArmorStand "tabs" cliquable. */
    private UUID tabEntityUUID = null;

    private Location hologramLocation = null;
    private GameMode currentMode      = GameMode.V1;

    // ── Constructeur ───────────────────────────────────────────────────────────

    public StatsHologramManager(HikaBrainPlugin plugin) {
        this.plugin  = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), HOLOGRAM_FILE);
        this.pdcKey  = new NamespacedKey(plugin, "hologram");
        this.tabKey  = new NamespacedKey(plugin, "hologram_tab");
        loadConfig();
    }

    // ── Config persistante ─────────────────────────────────────────────────────

    private void loadConfig() {
        if (!cfgFile.exists()) return;
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        // Mode actif
        String modeStr = cfg.getString("current-mode", "1v1");
        for (GameMode m : GameMode.values()) {
            if (m.getLabel().equals(modeStr)) { currentMode = m; break; }
        }

        // Position sauvegardée → on charge le chunk et on respawne
        if (cfg.contains("location.world")) {
            String worldName = cfg.getString("location.world");
            World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                double x = cfg.getDouble("location.x");
                double y = cfg.getDouble("location.y");
                double z = cfg.getDouble("location.z");
                hologramLocation = new Location(world, x, y, z);
                // Charger le chunk en mémoire (nécessaire avant de spawner des entités)
                Chunk chunk = world.getChunkAt(hologramLocation);
                world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
                // Nettoyer les éventuels ArmorStands orphelins du run précédent
                purgeOrphanArmorStands(world);
                // Respawner proprement
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
            cfg.set("location.x",     hologramLocation.getX());
            cfg.set("location.y",     hologramLocation.getY());
            cfg.set("location.z",     hologramLocation.getZ());
        } else {
            cfg.set("location", null);
        }
        try { cfg.save(cfgFile); }
        catch (IOException e) { plugin.getLogger().warning("[HikaBrain] Impossible de sauvegarder hologram.yml : " + e.getMessage()); }
    }

    // ── API publique ───────────────────────────────────────────────────────────

    /**
     * Place (ou déplace) l'hologramme à la position donnée.
     * Supprime l'ancien s'il existe, charge le chunk cible.
     */
    public void spawn(Location loc) {
        despawnEntities();   // retire les ArmorStands existants
        hologramLocation = loc.clone();
        // Charger le chunk pour que les entités puissent être créées et restent en mémoire
        Chunk chunk = loc.getWorld().getChunkAt(loc);
        loc.getWorld().addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        buildLines();
        saveConfig();
    }

    /**
     * Supprime l'hologramme (entités + config de position).
     */
    public void despawn() {
        if (hologramLocation != null) {
            // Libérer le ticket de chunk
            Chunk chunk = hologramLocation.getWorld().getChunkAt(hologramLocation);
            hologramLocation.getWorld().removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        }
        despawnEntities();
        hologramLocation = null;
        saveConfig();
    }

    /** Change le mode affiché et rafraîchit les lignes. */
    public void setMode(GameMode mode) {
        this.currentMode = mode;
        refresh();
        saveConfig();
    }

    /** Rafraîchit le contenu de l'hologramme (appelé après chaque fin de partie). */
    public void refresh() {
        if (hologramLocation == null) return;
        Location saved = hologramLocation.clone();
        despawnEntities();
        hologramLocation = saved;
        buildLines();
    }

    public boolean   isSpawned()       { return !lineEntities.isEmpty(); }
    public Location  getLocation()     { return hologramLocation == null ? null : hologramLocation.clone(); }
    public GameMode  getCurrentMode()  { return currentMode; }
    public UUID      getTabEntityUUID(){ return tabEntityUUID; }

    // ── Construction des lignes ────────────────────────────────────────────────

    private void buildLines() {
        StatsManager sm = plugin.getStatsManager();
        GameMode     m  = currentMode;
        World        w  = hologramLocation.getWorld();

        List<Component> lines = new ArrayList<>();

        // 0 — Titre
        lines.add(Component.text("══ HikaBrain Stats ══")
                .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

        // 1 — Tabs de mode (cliquable)
        lines.add(buildTabLine(m));

        // 2 — Séparateur
        lines.add(sep());

        // 3‑5 — Équipe Rouge
        lines.add(Component.text("❤ Équipe Rouge").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        lines.add(gray("  Victoires : ").append(Component.text(sm.getRedWins(m)).color(NamedTextColor.RED)));
        lines.add(gray("  Kills : ").append(Component.text(sm.getRedKills(m)).color(NamedTextColor.RED))
                .append(gray("  Deaths : ")).append(Component.text(sm.getRedDeaths(m)).color(NamedTextColor.RED))
                .append(gray("  K/D : ")).append(Component.text(sm.getRedKD(m)).color(NamedTextColor.GOLD)));

        // 6 — Séparateur
        lines.add(sep());

        // 7‑9 — Équipe Bleue
        lines.add(Component.text("❤ Équipe Bleue").color(NamedTextColor.BLUE).decorate(TextDecoration.BOLD));
        lines.add(gray("  Victoires : ").append(Component.text(sm.getBlueWins(m)).color(NamedTextColor.BLUE)));
        lines.add(gray("  Kills : ").append(Component.text(sm.getBlueKills(m)).color(NamedTextColor.BLUE))
                .append(gray("  Deaths : ")).append(Component.text(sm.getBlueDeaths(m)).color(NamedTextColor.BLUE))
                .append(gray("  K/D : ")).append(Component.text(sm.getBlueKD(m)).color(NamedTextColor.GOLD)));

        // 10 — Séparateur
        lines.add(sep());

        // 11 — Totaux
        lines.add(gray("Parties : ").append(Component.text(sm.getTotalGames()).color(NamedTextColor.WHITE))
                .append(gray("   Captures : ")).append(Component.text(sm.getTotalCaptures()).color(NamedTextColor.WHITE)));

        // Spawner du haut vers le bas
        double topY = hologramLocation.getY() + (lines.size() - 1) * LINE_GAP;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = hologramLocation.clone();
            lineLoc.setY(topY - i * LINE_GAP);
            ArmorStand as = spawnStand(w, lineLoc, lines.get(i));
            lineEntities.add(as.getUniqueId());
            if (i == 1) {
                tabEntityUUID = as.getUniqueId();
                // Tag PDC supplémentaire pour identifier le stand "tabs" après un redémarrage
                as.getPersistentDataContainer().set(tabKey, PersistentDataType.STRING, "tab");
            }
        }
    }

    private Component buildTabLine(GameMode active) {
        Component line = Component.empty();
        for (GameMode m : GameMode.values()) {
            boolean on = m == active;
            line = line.append(Component.text(" ").color(NamedTextColor.DARK_GRAY))
                       .append(Component.text("[" + m.getLabel() + "]")
                               .color(on ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                               .decoration(TextDecoration.BOLD,   on)
                               .decoration(TextDecoration.ITALIC, false));
        }
        return line;
    }

    // ── Spawn / dépawn des entités ─────────────────────────────────────────────

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
        // Tag PDC pour identifier nos ArmorStands même après redémarrage
        as.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, PDC_VALUE);
        return as;
    }

    /** Retire uniquement les entités dont on a l'UUID en mémoire. */
    private void despawnEntities() {
        for (UUID id : lineEntities) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        }
        lineEntities.clear();
        tabEntityUUID = null;
    }

    /**
     * Parcourt toutes les entités du monde pour supprimer les ArmorStands
     * qui portent notre tag PDC (reliquats d'un run précédent dont les UUIDs
     * ne sont plus en mémoire).
     */
    private void purgeOrphanArmorStands(World world) {
        for (Entity e : world.getEntities()) {
            if (e instanceof ArmorStand as) {
                String val = as.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                if (PDC_VALUE.equals(val)) {
                    as.remove();
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Component sep() {
        return Component.text("─────────────────────").color(NamedTextColor.DARK_GRAY);
    }

    private static Component gray(String text) {
        return Component.text(text).color(NamedTextColor.GRAY);
    }
}
