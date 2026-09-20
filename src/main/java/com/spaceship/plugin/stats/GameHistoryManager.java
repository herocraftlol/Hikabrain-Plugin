package com.spaceship.plugin.stats;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.spaceship.plugin.game.Team;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Archive les parties SpaceShip terminées et tient à jour le classement des parties les
 * plus longues (toutes arènes confondues), "façon anthologie" : pour chaque partie
 * conservée, on garde les joueurs présents, la date, l'équipe gagnante, ainsi que le
 * nombre total de kills et de points (zones capturées) marqués pendant la partie.
 *
 * Alimente à la fois /ss longestgames (affichage chat) et LongestGamesLeaderboardManager
 * (hologramme).
 */
public class GameHistoryManager {

    private static final String FILE_NAME = "game-history.yml";
    /** Nombre maximum de parties conservées en mémoire/disque (au-delà, les plus courtes sont oubliées). */
    private static final int MAX_STORED = 50;

    private final HikaBrainPlugin plugin;
    private final File historyFile;
    private FileConfiguration cfg;

    private final List<GameRecord> records = new ArrayList<>();

    public GameHistoryManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.historyFile = new File(plugin.getDataFolder(), FILE_NAME);
        loadHistory();
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private void loadHistory() {
        if (!historyFile.exists()) return;
        cfg = YamlConfiguration.loadConfiguration(historyFile);

        records.clear();
        ConfigurationSection root = cfg.getConfigurationSection("games");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Team winner = Team.valueOf(s.getString("winner", "BLACK"));
                GameRecord record = new GameRecord(
                        s.getString("arena", "?"),
                        s.getLong("duration-seconds", 0L),
                        s.getLong("timestamp", 0L),
                        winner,
                        s.getStringList("black-players"),
                        s.getStringList("white-players"),
                        s.getInt("total-kills", 0),
                        s.getInt("total-points", 0)
                );
                records.add(record);
            } catch (IllegalArgumentException ignored) {
                // Entrée corrompue (équipe invalide) : on l'ignore plutôt que de faire planter le chargement.
            }
        }
        sortByDurationDesc();
    }

    private void saveHistory() {
        if (cfg == null) cfg = new YamlConfiguration();
        cfg.set("games", null);
        for (int i = 0; i < records.size(); i++) {
            GameRecord r = records.get(i);
            String path = "games." + i;
            cfg.set(path + ".arena", r.getArenaName());
            cfg.set(path + ".duration-seconds", r.getDurationSeconds());
            cfg.set(path + ".timestamp", r.getTimestampMillis());
            cfg.set(path + ".winner", r.getWinner().name());
            cfg.set(path + ".black-players", r.getBlackPlayers());
            cfg.set(path + ".white-players", r.getWhitePlayers());
            cfg.set(path + ".total-kills", r.getTotalKills());
            cfg.set(path + ".total-points", r.getTotalPoints());
        }
        try {
            historyFile.getParentFile().mkdirs();
            cfg.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[SpaceShip] Impossible de sauvegarder " + FILE_NAME + " : " + e.getMessage());
        }
    }

    // ── API publique ───────────────────────────────────────────────────────────

    /** Enregistre une partie terminée et met à jour le classement (persisté immédiatement). */
    public void recordGame(GameRecord record) {
        records.add(record);
        sortByDurationDesc();
        while (records.size() > MAX_STORED) {
            records.remove(records.size() - 1);
        }
        saveHistory();
    }

    /** Renvoie le top {@code limit} des parties les plus longues, les plus longues en premier. */
    public List<GameRecord> getLongestGames(int limit) {
        return records.size() > limit ? new ArrayList<>(records.subList(0, limit)) : new ArrayList<>(records);
    }

    public void resetHistory() {
        records.clear();
        saveHistory();
    }

    private void sortByDurationDesc() {
        records.sort(Comparator.comparingLong(GameRecord::getDurationSeconds).reversed());
    }
}
