package com.hikabrain.plugin.stats;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.Team;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gère les statistiques globales d'HikaBrain :
 * - Victoires par équipe
 * - Kills et Deaths par équipe
 * - Ratio K/D
 * - Autres statistiques globales
 * 
 * Les statistiques sont sauvegardées dans un fichier séparé (stats.yml)
 * pour persister entre les redémarrages du serveur.
 */
public class StatsManager {

    private final HikaBrainPlugin plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;

    // Cache en mémoire des statistiques
    private int redWins;
    private int blueWins;
    private int redKills;
    private int redDeaths;
    private int blueKills;
    private int blueDeaths;
    private int totalGames;
    private int totalCaptures;

    public StatsManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        loadStats();
    }

    /**
     * Charge les statistiques depuis le fichier stats.yml
     */
    public void loadStats() {
        if (!statsFile.exists()) {
            // Créer le fichier avec les valeurs par défaut
            try {
                statsFile.getParentFile().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer stats.yml: " + e.getMessage());
            }
        }

        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        
        // Charger les statistiques en mémoire
        redWins = statsConfig.getInt("wins.red", 0);
        blueWins = statsConfig.getInt("wins.blue", 0);
        redKills = statsConfig.getInt("kills.red", 0);
        redDeaths = statsConfig.getInt("deaths.red", 0);
        blueKills = statsConfig.getInt("kills.blue", 0);
        blueDeaths = statsConfig.getInt("deaths.blue", 0);
        totalGames = statsConfig.getInt("total-games", 0);
        totalCaptures = statsConfig.getInt("total-captures", 0);
    }

    /**
     * Sauvegarde les statistiques dans le fichier stats.yml
     */
    public void saveStats() {
        statsConfig.set("wins.red", redWins);
        statsConfig.set("wins.blue", blueWins);
        statsConfig.set("kills.red", redKills);
        statsConfig.set("deaths.red", redDeaths);
        statsConfig.set("kills.blue", blueKills);
        statsConfig.set("deaths.blue", blueDeaths);
        statsConfig.set("total-games", totalGames);
        statsConfig.set("total-captures", totalCaptures);
        
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder stats.yml: " + e.getMessage());
        }
    }

    /**
     * Ajoute une victoire à l'équipe gagnante
     */
    public void addWin(Team winner) {
        if (winner == Team.RED) {
            redWins++;
        } else {
            blueWins++;
        }
        totalGames++;
        saveStats();
    }

    /**
     * Ajoute un kill à l'équipe spécifiée
     */
    public void addKill(Team team) {
        if (team == Team.RED) {
            redKills++;
        } else {
            blueKills++;
        }
        saveStats();
    }

    /**
     * Ajoute un death à l'équipe spécifiée
     */
    public void addDeath(Team team) {
        if (team == Team.RED) {
            redDeaths++;
        } else {
            blueDeaths++;
        }
        saveStats();
    }

    /**
     * Ajoute une capture au compteur global
     */
    public void addCapture() {
        totalCaptures++;
        saveStats();
    }

    /**
     * Retourne le nombre de victoires de l'équipe rouge
     */
    public int getRedWins() {
        return redWins;
    }

    /**
     * Retourne le nombre de victoires de l'équipe bleue
     */
    public int getBlueWins() {
        return blueWins;
    }

    /**
     * Retourne le nombre de kills de l'équipe rouge
     */
    public int getRedKills() {
        return redKills;
    }

    /**
     * Retourne le nombre de kills de l'équipe bleue
     */
    public int getBlueKills() {
        return blueKills;
    }

    /**
     * Retourne le nombre de deaths de l'équipe rouge
     */
    public int getRedDeaths() {
        return redDeaths;
    }

    /**
     * Retourne le nombre de deaths de l'équipe bleue
     */
    public int getBlueDeaths() {
        return blueDeaths;
    }

    /**
     * Calcule le ratio K/D de l'équipe rouge
     */
    public double getRedKD() {
        if (redDeaths == 0) {
            return redKills > 0 ? redKills : 0.0;
        }
        return Math.round((double) redKills / redDeaths * 100.0) / 100.0;
    }

    /**
     * Calcule le ratio K/D de l'équipe bleue
     */
    public double getBlueKD() {
        if (blueDeaths == 0) {
            return blueKills > 0 ? blueKills : 0.0;
        }
        return Math.round((double) blueKills / blueDeaths * 100.0) / 100.0;
    }

    /**
     * Retourne le nombre total de parties jouées
     */
    public int getTotalGames() {
        return totalGames;
    }

    /**
     * Retourne le nombre total de captures effectuées
     */
    public int getTotalCaptures() {
        return totalCaptures;
    }

    /**
     * Réinitialise toutes les statistiques
     */
    public void resetStats() {
        redWins = 0;
        blueWins = 0;
        redKills = 0;
        redDeaths = 0;
        blueKills = 0;
        blueDeaths = 0;
        totalGames = 0;
        totalCaptures = 0;
        saveStats();
    }

    /**
     * Retourne toutes les statistiques sous forme de Map
     */
    public Map<String, Object> getAllStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("red_wins", redWins);
        stats.put("blue_wins", blueWins);
        stats.put("red_kills", redKills);
        stats.put("red_deaths", redDeaths);
        stats.put("blue_kills", blueKills);
        stats.put("blue_deaths", blueDeaths);
        stats.put("red_kd", getRedKD());
        stats.put("blue_kd", getBlueKD());
        stats.put("total_games", totalGames);
        stats.put("total_captures", totalCaptures);
        return stats;
    }
}
