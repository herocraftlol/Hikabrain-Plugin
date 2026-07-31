package com.hikabrain.plugin.levels;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gère le système de points et de niveaux d'HikaBrain.
 *
 * À la fin de chaque partie, chaque joueur reçoit des points calculés à partir de :
 *  - ses coups portés à un adversaire (le plus simple, donc le moins de points)
 *  - ses kills
 *  - ses buts marqués
 *  - la victoire de son équipe (le plus dur à obtenir, donc le plus de points)
 *
 * Les points cumulés font progresser un niveau par paliers de plus en plus exigeants
 * (chaque niveau demande plus de points que le précédent), et débloquent des avantages
 * purement cosmétiques (voir {@link Perk}).
 *
 * Les données sont sauvegardées dans levels.yml.
 */
public class LevelManager {

    /**
     * Résultat du calcul de points accordés à la fin d'une partie pour un joueur donné.
     */
    public static class AwardResult {
        public final int pointsGained;
        public final int totalPoints;
        public final int oldLevel;
        public final int newLevel;
        public final List<Perk> newlyUnlockedPerks;

        public AwardResult(int pointsGained, int totalPoints, int oldLevel, int newLevel, List<Perk> newlyUnlockedPerks) {
            this.pointsGained = pointsGained;
            this.totalPoints = totalPoints;
            this.oldLevel = oldLevel;
            this.newLevel = newLevel;
            this.newlyUnlockedPerks = newlyUnlockedPerks;
        }

        public boolean leveledUp() {
            return newLevel > oldLevel;
        }
    }

    public static class PlayerLevelData {
        public String name;
        public int points;
        public String equippedPerkId;

        public PlayerLevelData(String name) {
            this.name = name;
        }
    }

    private final HikaBrainPlugin plugin;
    private final File levelsFile;
    private FileConfiguration levelsConfig;

    private final Map<UUID, PlayerLevelData> playerLevels = new HashMap<>();

    // ── Barème de points (config.yml, section "levels") ────────────────────────
    private int pointsPerHit;
    private int pointsPerKill;
    private int pointsPerGoal;
    private int pointsPerWin;
    private int basePointsPerLevel;

    public LevelManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.levelsFile = new File(plugin.getDataFolder(), "levels.yml");
        loadConfigValues();
        load();
    }

    public void loadConfigValues() {
        pointsPerHit       = plugin.getConfig().getInt("levels.points-per-hit", 1);
        pointsPerKill      = plugin.getConfig().getInt("levels.points-per-kill", 5);
        pointsPerGoal      = plugin.getConfig().getInt("levels.points-per-goal", 8);
        pointsPerWin       = plugin.getConfig().getInt("levels.points-per-win", 15);
        basePointsPerLevel = plugin.getConfig().getInt("levels.base-points", 100);
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    public void load() {
        if (!levelsFile.exists()) {
            try {
                levelsFile.getParentFile().mkdirs();
                levelsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer levels.yml: " + e.getMessage());
            }
        }
        levelsConfig = YamlConfiguration.loadConfiguration(levelsFile);

        playerLevels.clear();
        ConfigurationSection section = levelsConfig.getConfigurationSection("players");
        if (section != null) {
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection playerSection = section.getConfigurationSection(uuidStr);
                    if (playerSection == null) continue;

                    PlayerLevelData data = new PlayerLevelData(playerSection.getString("name", "?"));
                    data.points = playerSection.getInt("points", 0);
                    data.equippedPerkId = playerSection.getString("equipped-perk", null);
                    playerLevels.put(uuid, data);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        levelsConfig.set("players", null);
        for (Map.Entry<UUID, PlayerLevelData> entry : playerLevels.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerLevelData data = entry.getValue();
            levelsConfig.set(path + ".name", data.name);
            levelsConfig.set(path + ".points", data.points);
            if (data.equippedPerkId != null) {
                levelsConfig.set(path + ".equipped-perk", data.equippedPerkId);
            }
        }
        try {
            levelsConfig.save(levelsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder levels.yml: " + e.getMessage());
        }
    }

    private PlayerLevelData getOrCreate(UUID uuid, String name) {
        PlayerLevelData data = playerLevels.get(uuid);
        if (data == null) {
            data = new PlayerLevelData(name);
            playerLevels.put(uuid, data);
        } else if (name != null) {
            data.name = name;
        }
        return data;
    }

    // ── Barème de points ────────────────────────────────────────────────────────

    public int getPointsPerHit()  { return pointsPerHit; }
    public int getPointsPerKill() { return pointsPerKill; }
    public int getPointsPerGoal() { return pointsPerGoal; }
    public int getPointsPerWin()  { return pointsPerWin; }

    /**
     * Calcule les points gagnés pour une partie à partir des statistiques brutes du joueur.
     * Barème du plus simple au plus dur à obtenir : coup < kill < but < victoire.
     */
    public int computeMatchPoints(int hits, int kills, int goals, boolean won) {
        int total = hits * pointsPerHit + kills * pointsPerKill + goals * pointsPerGoal;
        if (won) total += pointsPerWin;
        return total;
    }

    // ── Niveaux ────────────────────────────────────────────────────────────────

    /**
     * Points cumulés nécessaires pour ATTEINDRE le niveau donné (paliers progressifs :
     * l'écart entre deux niveaux consécutifs grandit à chaque niveau, donc c'est de plus
     * en plus dur de monter). Niveau 0 = 0 point.
     */
    public int getPointsRequiredForLevel(int level) {
        if (level <= 0) return 0;
        return basePointsPerLevel * level * (level + 1) / 2;
    }

    /**
     * Détermine le niveau atteint pour un total de points donné.
     */
    public int getLevelForPoints(int points) {
        int level = 0;
        while (getPointsRequiredForLevel(level + 1) <= points) {
            level++;
        }
        return level;
    }

    public int getPoints(UUID uuid) {
        PlayerLevelData data = playerLevels.get(uuid);
        return data != null ? data.points : 0;
    }

    public int getLevel(UUID uuid) {
        return getLevelForPoints(getPoints(uuid));
    }

    /**
     * Points restants pour atteindre le niveau suivant.
     */
    public int getPointsToNextLevel(UUID uuid) {
        int points = getPoints(uuid);
        int nextLevel = getLevelForPoints(points) + 1;
        return Math.max(0, getPointsRequiredForLevel(nextLevel) - points);
    }

    /**
     * Ajoute des points à un joueur (fin de partie), et renvoie le détail du gain,
     * y compris un éventuel changement de niveau et les avantages nouvellement débloqués.
     */
    public AwardResult addPoints(UUID uuid, String name, int pointsGained) {
        PlayerLevelData data = getOrCreate(uuid, name);
        int oldLevel = getLevelForPoints(data.points);
        data.points += pointsGained;
        int newLevel = getLevelForPoints(data.points);

        List<Perk> newlyUnlocked = new ArrayList<>();
        if (newLevel > oldLevel) {
            for (Perk perk : Perk.values()) {
                if (perk.getUnlockLevel() > oldLevel && perk.getUnlockLevel() <= newLevel) {
                    newlyUnlocked.add(perk);
                }
            }
        }

        save();
        return new AwardResult(pointsGained, data.points, oldLevel, newLevel, newlyUnlocked);
    }

    // ── Classement ─────────────────────────────────────────────────────────────

    public List<Map.Entry<UUID, PlayerLevelData>> getTopPlayers(int limit) {
        List<Map.Entry<UUID, PlayerLevelData>> entries = new ArrayList<>(playerLevels.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<UUID, PlayerLevelData> e) -> e.getValue().points).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    // ── Avantages cosmétiques ───────────────────────────────────────────────────

    public boolean isPerkUnlocked(UUID uuid, Perk perk) {
        return getLevel(uuid) >= perk.getUnlockLevel();
    }

    public List<Perk> getUnlockedPerks(UUID uuid) {
        List<Perk> unlocked = new ArrayList<>();
        int level = getLevel(uuid);
        for (Perk perk : Perk.values()) {
            if (perk.getUnlockLevel() <= level) unlocked.add(perk);
        }
        return unlocked;
    }

    /**
     * Renvoie l'avantage actuellement équipé par le joueur, ou null s'il n'en a pas
     * (ou si celui enregistré n'est plus valide / plus débloqué).
     */
    public Perk getEquippedPerk(UUID uuid) {
        PlayerLevelData data = playerLevels.get(uuid);
        if (data == null || data.equippedPerkId == null) return null;
        Perk perk = Perk.fromId(data.equippedPerkId);
        if (perk == null || !isPerkUnlocked(uuid, perk)) return null;
        return perk;
    }

    /**
     * Équipe un avantage débloqué (ou le retire si perk == null).
     * Renvoie false si le joueur n'a pas débloqué cet avantage.
     */
    public boolean equipPerk(UUID uuid, String name, Perk perk) {
        PlayerLevelData data = getOrCreate(uuid, name);
        if (perk == null) {
            data.equippedPerkId = null;
            save();
            return true;
        }
        if (!isPerkUnlocked(uuid, perk)) {
            return false;
        }
        data.equippedPerkId = perk.getId();
        save();
        return true;
    }

    public void resetAll() {
        playerLevels.clear();
        save();
    }
}
