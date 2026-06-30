package com.hikabrain.plugin.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stocke tous les points importants de la map configurés par un admin :
 * - le point de lobby
 * - les spawns de chaque équipe (une ou plusieurs positions par équipe, utile quand
 *   il y a plusieurs joueurs par équipe afin d'éviter qu'ils n'apparaissent tous au
 *   même endroit)
 * - les zones de capture de chaque équipe (cuboïdes définies par 2 coins)
 */
public class Arena {

    private Location lobbySpawn;

    // Liste de spawns par équipe. L'index 0 correspond au spawn "1" vu de l'admin
    // (les commandes utilisent un index 1-based, converti en 0-based ici).
    private final Map<Team, List<Location>> teamSpawns = new EnumMap<>(Team.class);

    private CuboidRegion redCaptureZone;
    private CuboidRegion blueCaptureZone;
    private CuboidRegion gameZone;

    /**
     * Nombre maximum de joueurs pour CETTE arène. -1 signifie "non défini" : on retombe
     * alors sur le max-players global du config.yml (voir GameManager#getMaxPlayers).
     */
    private int maxPlayers = -1;

    public Arena() {
        teamSpawns.put(Team.RED, new ArrayList<>());
        teamSpawns.put(Team.BLUE, new ArrayList<>());
    }

    /**
     * Renvoie le nombre maximum de joueurs configuré spécifiquement pour cette arène,
     * ou -1 si aucune valeur spécifique n'a été définie (on doit alors utiliser le
     * max-players global).
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Définit le nombre maximum de joueurs pour cette arène. Une valeur <= 0 réinitialise
     * la configuration spécifique (retour au max-players global).
     */
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers <= 0 ? -1 : maxPlayers;
    }

    /**
     * La zone de jeu globale (gameZone) n'est PAS incluse ici volontairement : c'est une
     * fonctionnalité optionnelle de protection/restauration des blocs, le jeu reste jouable
     * sans elle (juste sans protection de map).
     */
    public boolean isFullyConfigured() {
        return lobbySpawn != null && !teamSpawns.get(Team.RED).isEmpty() && !teamSpawns.get(Team.BLUE).isEmpty()
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

    /**
     * Renvoie un spawn choisi aléatoirement parmi tous ceux configurés pour cette équipe.
     * Utile quand il y a plusieurs joueurs par équipe : ça évite qu'ils apparaissent tous
     * exactement au même endroit. Renvoie null si aucun spawn n'est configuré pour l'équipe.
     */
    public Location getSpawn(Team team) {
        List<Location> spawns = teamSpawns.get(team);
        if (spawns == null || spawns.isEmpty()) {
            return null;
        }
        if (spawns.size() == 1) {
            return spawns.get(0);
        }
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
    }

    /**
     * Renvoie la liste complète (non modifiable) des spawns configurés pour une équipe,
     * dans l'ordre de leur index (le premier élément correspond au spawn "1").
     */
    public List<Location> getSpawns(Team team) {
        return Collections.unmodifiableList(teamSpawns.get(team));
    }

    /**
     * Nombre de spawns actuellement configurés pour une équipe.
     */
    public int getSpawnCount(Team team) {
        return teamSpawns.get(team).size();
    }

    /**
     * Définit (ou remplace) le spawn d'une équipe à l'index donné (1-based, comme vu par
     * l'admin en commande). Si l'index correspond au prochain spawn disponible (liste.size() + 1),
     * il est ajouté à la suite. Les "trous" ne sont pas autorisés : un admin doit définir le
     * spawn 1 avant de pouvoir définir le spawn 2, etc.
     *
     * Renvoie false si l'index demandé est invalide (trop grand, ou inférieur à 1).
     */
    public boolean setSpawn(Team team, int index, Location loc) {
        if (index < 1) {
            return false;
        }
        List<Location> spawns = teamSpawns.get(team);
        if (index <= spawns.size()) {
            spawns.set(index - 1, loc);
            return true;
        }
        if (index == spawns.size() + 1) {
            spawns.add(loc);
            return true;
        }
        return false;
    }

    /**
     * Supprime le spawn d'une équipe à l'index donné (1-based). Renvoie false si l'index
     * est invalide.
     */
    public boolean removeSpawn(Team team, int index) {
        List<Location> spawns = teamSpawns.get(team);
        if (index < 1 || index > spawns.size()) {
            return false;
        }
        spawns.remove(index - 1);
        return true;
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
        config.set("arena.spawns.red", null);
        config.set("arena.spawns.blue", null);
        saveSpawnList(config, "arena.spawns.red", teamSpawns.get(Team.RED));
        saveSpawnList(config, "arena.spawns.blue", teamSpawns.get(Team.BLUE));
        saveRegion(config, "arena.captures.red", redCaptureZone);
        saveRegion(config, "arena.captures.blue", blueCaptureZone);
        saveRegion(config, "arena.gamezone", gameZone);
        if (maxPlayers > 0) {
            config.set("arena.max-players", maxPlayers);
        } else {
            config.set("arena.max-players", null);
        }
    }

    public void loadFromConfig(FileConfiguration config) {
        this.lobbySpawn = loadLocation(config, "arena.lobby");
        teamSpawns.get(Team.RED).clear();
        teamSpawns.get(Team.RED).addAll(loadSpawnList(config, "arena.spawns.red"));
        teamSpawns.get(Team.BLUE).clear();
        teamSpawns.get(Team.BLUE).addAll(loadSpawnList(config, "arena.spawns.blue"));
        this.redCaptureZone = loadRegion(config, "arena.captures.red");
        this.blueCaptureZone = loadRegion(config, "arena.captures.blue");
        this.gameZone = loadRegion(config, "arena.gamezone");
        this.maxPlayers = config.isSet("arena.max-players") ? config.getInt("arena.max-players") : -1;
    }

    /**
     * Sauvegarde la liste de spawns d'une équipe sous "<path>.1", "<path>.2", etc.
     */
    private void saveSpawnList(FileConfiguration config, String path, List<Location> spawns) {
        for (int i = 0; i < spawns.size(); i++) {
            saveLocation(config, path + "." + (i + 1), spawns.get(i));
        }
    }

    /**
     * Charge la liste de spawns d'une équipe. Supporte aussi l'ancien format (une seule
     * localisation directement sous "<path>", sans sous-clé numérique) pour rester compatible
     * avec les arènes configurées avant l'ajout des spawns multiples.
     */
    private List<Location> loadSpawnList(FileConfiguration config, String path) {
        List<Location> result = new ArrayList<>();

        // Ancien format : "<path>.world" existe directement (un seul spawn, pas de liste).
        if (config.isSet(path + ".world")) {
            Location legacy = loadLocation(config, path);
            if (legacy != null) {
                result.add(legacy);
            }
            return result;
        }

        // Nouveau format : "<path>.1", "<path>.2", ... dans l'ordre.
        int index = 1;
        while (config.isSet(path + "." + index + ".world")) {
            Location loc = loadLocation(config, path + "." + index);
            if (loc != null) {
                result.add(loc);
            }
            index++;
        }
        return result;
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
