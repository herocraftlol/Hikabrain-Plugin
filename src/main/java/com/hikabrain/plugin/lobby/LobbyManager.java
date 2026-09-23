package com.hikabrain.plugin.lobby;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.hologram.HologramStyle;
import com.hikabrain.plugin.util.MessageUtil;
import fr.cabintransport.manager.RouteManager;
import fr.cabintransport.model.CameraPoint;
import fr.cabintransport.model.PointRef;
import fr.cabintransport.model.Route;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Génère et gère le lobby central du réseau : plateforme physique, DEUX titres flottants
 * (HikaBrain à gauche, SpaceShip à droite — même taille), 3 emplacements réservés pour
 * les PNJ SpaceShip (juste les coordonnées : la pose des PNJ eux-mêmes reste manuelle),
 * et le trajet de la caméra de découverte (voir fr.cabintransport), reconfiguré
 * automatiquement pour correspondre à la structure qui vient d'être posée.
 *
 * Rien de tout ça n'est actif tant que /hb lobby generate n'a pas été exécuté — et la
 * visite caméra elle-même reste MANUELLEMENT activable/désactivable ensuite via
 * /hb lobby tour <on|off>, justement pour pouvoir d'abord construire et vérifier le
 * lobby avant de l'activer pour de vrai auprès des joueurs (voir /hb lobby tour test
 * pour la tester soi-même sans attendre une vraie première connexion).
 */
public class LobbyManager {

    /** Échelle commune aux deux titres flottants — GARANTIT qu'ils font la même taille. */
    private static final double TITLE_SCALE = 4.0;
    private static final String ROUTE_ID = "lobby-tour";

    private final HikaBrainPlugin plugin;
    private final File dataFile;
    private final NamespacedKey pdcKey;

    private boolean generated = false;
    private Location spawnLocation;
    private Location hikabrainTitleLocation;
    private Location spaceshipTitleLocation;
    private UUID hikabrainTitleEntity;
    private UUID spaceshipTitleEntity;
    private final List<Location> spaceshipNpcSpots = new ArrayList<>();

    public LobbyManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "lobby.yml");
        this.pdcKey = new NamespacedKey(plugin, "lobby_title");
        load();
    }

    public boolean isGenerated() {
        return generated;
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    public List<Location> getSpaceshipNpcSpots() {
        List<Location> copy = new ArrayList<>();
        for (Location loc : spaceshipNpcSpots) copy.add(loc.clone());
        return copy;
    }

    // ── Génération ─────────────────────────────────────────────────────────────

    /**
     * Construit le lobby à cet endroit (plateforme + dôme + les deux titres + emplacements
     * PNJ + trajet caméra de découverte). Si un lobby existait déjà, ses anciens titres
     * sont retirés avant de reconstruire — pas de doublons en cas de ré-exécution.
     */
    public void generate(World world, int cx, int cy, int cz) {
        removeExistingTitles();

        buildStructure(world, cx, cy, cz);

        spawnLocation = new Location(world, cx + 0.5, cy + 1, cz + 0.5, 0f, 0f);

        // Titre HikaBrain à gauche (-X), titre SpaceShip à droite (+X) — symétriques,
        // même hauteur, même échelle : c'est ce qui garantit visuellement "la même taille".
        hikabrainTitleLocation = new Location(world, cx - 20.5, cy + 6, cz + 0.5);
        spaceshipTitleLocation = new Location(world, cx + 20.5, cy + 6, cz + 0.5);

        HologramStyle style = HologramStyle.load(plugin);
        TextDisplay hikabrainTitle = spawnTitle(hikabrainTitleLocation, "&6&l⚔ HikaBrain ⚔", style);
        TextDisplay spaceshipTitle = spawnTitle(spaceshipTitleLocation, "&b&l🚀 SpaceShip 🚀", style);
        hikabrainTitleEntity = hikabrainTitle.getUniqueId();
        spaceshipTitleEntity = spaceshipTitle.getUniqueId();

        // 3 emplacements réservés côté SpaceShip (à droite), pour que l'admin y pose ses
        // propres PNJ ensuite — voir /hb lobby npcspots pour récupérer ces coordonnées.
        spaceshipNpcSpots.clear();
        spaceshipNpcSpots.add(new Location(world, cx + 15.5, cy + 1, cz - 5.5, -90f, 0f));
        spaceshipNpcSpots.add(new Location(world, cx + 15.5, cy + 1, cz + 0.5, -90f, 0f));
        spaceshipNpcSpots.add(new Location(world, cx + 15.5, cy + 1, cz + 6.5, -90f, 0f));

        generated = true;
        save();

        buildDiscoveryRoute(world, cx, cy, cz);
    }

    private void removeExistingTitles() {
        if (hikabrainTitleEntity != null) {
            Entity e = Bukkit.getEntity(hikabrainTitleEntity);
            if (e != null) e.remove();
        }
        if (spaceshipTitleEntity != null) {
            Entity e = Bukkit.getEntity(spaceshipTitleEntity);
            if (e != null) e.remove();
        }
    }

    // ── Construction physique ─────────────────────────────────────────────────

    /**
     * Plateforme circulaire (quartz + anneau lumineux + bordure) surmontée d'un dôme de
     * verre avec quelques accents "étoiles" (end rod), centrée sur (cx, cy, cz). Zone
     * dégagée en dessous et au-dessus au préalable pour ne dépendre d'aucun terrain
     * préexistant (utile vu l'éloignement volontaire des coordonnées choisies).
     */
    private void buildStructure(World world, int cx, int cy, int cz) {
        int radius = 22;
        int domeHeight = 12;

        // Dégagement complet de la zone (sol -3 à dôme +domeHeight, large de radius+2)
        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                for (int dy = -3; dy <= domeHeight + 2; dy++) {
                    world.getBlockAt(cx + dx, cy + dy, cz + dz).setType(Material.AIR, false);
                }
            }
        }

        // Plateforme : quartz au centre, anneau de sea lantern, bordure en fer
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;
                Material mat;
                if (dist > radius - 1.2) mat = Material.IRON_BLOCK;
                else if (dist > radius - 2.5) mat = Material.SEA_LANTERN;
                else mat = Material.SMOOTH_QUARTZ;
                world.getBlockAt(cx + dx, cy, cz + dz).setType(mat, false);
            }
        }

        // Dôme de verre avec quelques accents lumineux ("étoiles")
        java.util.Random random = new java.util.Random(cx * 341873128712L + cz * 132897987541L);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 1; dy <= domeHeight; dy++) {
                    double dist3d = Math.sqrt(dx * dx + dz * dz + (dy - domeHeight * 0.3) * (dy - domeHeight * 0.3) * 0.6);
                    if (dist3d > radius - 1 && dist3d <= radius) {
                        Material mat = random.nextInt(40) == 0 ? Material.END_ROD : Material.LIGHT_BLUE_STAINED_GLASS;
                        world.getBlockAt(cx + dx, cy + dy, cz + dz).setType(mat, false);
                    }
                }
            }
        }
    }

    private TextDisplay spawnTitle(Location loc, String legacyText, HologramStyle style) {
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        style.apply(display, TITLE_SCALE);
        display.text(MessageUtil.formatComponent(legacyText));
        display.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, "lobby_title");
        return display;
    }

    /**
     * (Re)crée le trajet caméra "lobby-tour" utilisé par la visite de découverte
     * (fr.cabintransport.manager.DiscoveryManager) : part du spawn, survole le titre
     * HikaBrain, une vue d'ensemble du centre, puis le titre SpaceShip, et termine près
     * du spawn — environ 10 secondes au total. Le joueur est de toute façon ramené à SA
     * position d'origine à la fin (voir le correctif dans JourneyManager#finish), donc
     * le tout dernier point n'a pas besoin d'être un endroit "sûr" en soi.
     */
    private void buildDiscoveryRoute(World world, int cx, int cy, int cz) {
        RouteManager routeManager = plugin.getRouteManager();
        if (routeManager.exists(ROUTE_ID)) routeManager.deleteRoute(ROUTE_ID);
        Route route = routeManager.createRoute(ROUTE_ID);
        route.setDisplayName("Visite du lobby (HikaBrain + SpaceShip)");

        String w = world.getName();
        addPoint(route, w, cx, cy + 3, cz, 0f, 0f, 0.5);
        addPoint(route, w, cx - 10, cy + 5, cz - 8, 55f, -5f, 2.5);
        addPoint(route, w, cx - 19, cy + 7, cz, 100f, 8f, 2.5);
        addPoint(route, w, cx, cy + 9, cz - 12, 0f, 12f, 2.0);
        addPoint(route, w, cx + 19, cy + 7, cz, -100f, 8f, 2.5);
        addPoint(route, w, cx, cy + 3, cz, 0f, 0f, 0.5);

        route.setCabinVisual(false);
        route.setLockCamera(true);
        route.setParticle(org.bukkit.Particle.END_ROD);

        routeManager.save();
    }

    private void addPoint(Route route, String world, double x, double y, double z, float yaw, float pitch, double transitionSeconds) {
        route.addCameraPoint(new CameraPoint(new PointRef(world, x, y, z, yaw, pitch), transitionSeconds));
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        generated = config.getBoolean("generated", false);
        if (!generated) return;

        spawnLocation = readLocation(config, "spawn");
        hikabrainTitleLocation = readLocation(config, "hikabrain-title");
        spaceshipTitleLocation = readLocation(config, "spaceship-title");

        String hikabrainId = config.getString("hikabrain-title-entity");
        if (hikabrainId != null) {
            try {
                hikabrainTitleEntity = UUID.fromString(hikabrainId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String spaceshipId = config.getString("spaceship-title-entity");
        if (spaceshipId != null) {
            try {
                spaceshipTitleEntity = UUID.fromString(spaceshipId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        spaceshipNpcSpots.clear();
        List<?> rawSpots = config.getList("spaceship-npc-spots");
        if (rawSpots != null) {
            for (Object entry : rawSpots) {
                if (entry instanceof ConfigurationSection section) {
                    Location loc = readLocation(section);
                    if (loc != null) spaceshipNpcSpots.add(loc);
                }
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("generated", generated);
        writeLocation(config, "spawn", spawnLocation);
        writeLocation(config, "hikabrain-title", hikabrainTitleLocation);
        writeLocation(config, "spaceship-title", spaceshipTitleLocation);
        config.set("hikabrain-title-entity", hikabrainTitleEntity != null ? hikabrainTitleEntity.toString() : null);
        config.set("spaceship-title-entity", spaceshipTitleEntity != null ? spaceshipTitleEntity.toString() : null);

        List<java.util.Map<String, Object>> spots = new ArrayList<>();
        for (Location loc : spaceshipNpcSpots) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("world", loc.getWorld().getName());
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
            map.put("yaw", (double) loc.getYaw());
            map.put("pitch", (double) loc.getPitch());
            spots.add(map);
        }
        config.set("spaceship-npc-spots", spots);

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[HikaBrain] Impossible de sauvegarder lobby.yml : " + e.getMessage());
        }
    }

    private void writeLocation(YamlConfiguration config, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", (double) loc.getYaw());
        config.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location readLocation(YamlConfiguration config, String path) {
        if (!config.isConfigurationSection(path)) return null;
        return readLocation(config.getConfigurationSection(path));
    }

    private Location readLocation(ConfigurationSection section) {
        String worldName = section.getString("world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0), (float) section.getDouble("pitch", 0));
    }
}
