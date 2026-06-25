package com.hikabrain.plugin.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Stocke tous les points importants de la map configurés par un admin :
 * - le point de lobby
 * - les spawns de chaque équipe
 * - les zones de capture de chaque équipe (cuboïdes définies par 2 coins)
 */
public class Arena {

    private Location lobbySpawn;
    private Location redSpawn;
    private Location blueSpawn;

    private CuboidRegion redCaptureZone;
    private CuboidRegion blueCaptureZone;
    private CuboidRegion gameZone;

    /**
     * La zone de jeu globale (gameZone) n'est PAS incluse ici volontairement : c'est une
     * fonctionnalité optionnelle de protection/restauration des blocs, le jeu reste jouable
     * sans elle (juste sans protection de map).
     */
    public boolean isFullyConfigured() {
        return lobbySpawn != null && redSpawn != null && blueSpawn != null
                && redCaptureZone != null && blueCaptureZone != null;
    }

    public CuboidRegion getGameZone() {
        return gameZone;
    }

    public void setGameZone(CuboidRegion gameZone) {
        this.gameZone = gameZone;
    }

    /**
     * Détermine si une localisation se trouve dans la zone de jeu globale.
     * Si aucune zone de jeu n'a été configurée, renvoie false (protection désactivée).
     */
    public boolean isInGameZone(Location loc) {
        return gameZone != null && gameZone.contains(loc);
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public Location getSpawn(Team team) {
        return team == Team.RED ? redSpawn : blueSpawn;
    }

    public void setSpawn(Team team, Location loc) {
        if (team == Team.RED) {
            this.redSpawn = loc;
        } else {
            this.blueSpawn = loc;
        }
    }

    /**
     * Renvoie la zone de capture appartenant à l'équipe donnée.
     * Pour marquer un point, c'est l'équipe ADVERSE qui doit entrer dans cette zone.
     */
    public CuboidRegion getCaptureZone(Team team) {
        return team == Team.RED ? redCaptureZone : blueCaptureZone;
    }

    public void setCaptureZone(Team team, CuboidRegion region) {
        if (team == Team.RED) {
            this.redCaptureZone = region;
        } else {
            this.blueCaptureZone = region;
        }
    }

    /**
     * Détermine si une localisation se trouve dans la zone de capture de l'équipe donnée.
     */
    public boolean isInCaptureZone(Team zoneOwner, Location loc) {
        CuboidRegion region = getCaptureZone(zoneOwner);
        return region != null && region.contains(loc);
    }

    // ---- Sauvegarde / chargement dans un fichier de config ----

    public void saveToConfig(FileConfiguration config) {
        saveLocation(config, "arena.lobby", lobbySpawn);
        saveLocation(config, "arena.spawns.red", redSpawn);
        saveLocation(config, "arena.spawns.blue", blueSpawn);
        saveRegion(config, "arena.captures.red", redCaptureZone);
        saveRegion(config, "arena.captures.blue", blueCaptureZone);
        saveRegion(config, "arena.gamezone", gameZone);
    }

    public void loadFromConfig(FileConfiguration config) {
        this.lobbySpawn = loadLocation(config, "arena.lobby");
        this.redSpawn = loadLocation(config, "arena.spawns.red");
        this.blueSpawn = loadLocation(config, "arena.spawns.blue");
        this.redCaptureZone = loadRegion(config, "arena.captures.red");
        this.blueCaptureZone = loadRegion(config, "arena.captures.blue");
        this.gameZone = loadRegion(config, "arena.gamezone");
    }

    private void saveLocation(FileConfiguration config, String path, Location loc) {
        if (loc == null) return;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }

    private Location loadLocation(FileConfiguration config, String path) {
        if (!config.isSet(path + ".world")) return null;
        World world = org.bukkit.Bukkit.getWorld(config.getString(path + ".world"));
        if (world == null) return null;
        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch")
        );
    }

    private void saveRegion(FileConfiguration config, String path, CuboidRegion region) {
        if (region == null) return;
        saveLocation(config, path + ".corner1", region.getCorner1());
        saveLocation(config, path + ".corner2", region.getCorner2());
    }

    private CuboidRegion loadRegion(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return null;
        Location c1 = loadLocation(config, path + ".corner1");
        Location c2 = loadLocation(config, path + ".corner2");
        if (c1 == null || c2 == null) return null;
        return new CuboidRegion(c1, c2);
    }
}
