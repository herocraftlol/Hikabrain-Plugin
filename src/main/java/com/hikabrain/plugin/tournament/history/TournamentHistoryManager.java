package com.hikabrain.plugin.tournament.history;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.tournament.Tournament;
import com.hikabrain.plugin.tournament.TournamentTeam;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Historique persistant de tous les tournois terminés + statistiques par joueur
 * (nombre de victoires, meilleur joueur, record de kills, temps moyen des matchs).
 *
 * Stocké dans plugins/HikaBrain/tournaments/history.yml
 */
public class TournamentHistoryManager {

    /** Une ligne d'historique correspondant à un tournoi terminé. */
    public static class Entry {
        public String name;
        public String format;
        public long finishedAt;
        public int participants;
        public String champion;
        public String runnerUp;
        public List<String> thirdPlace = new ArrayList<>();
        public double averageMatchSeconds;
        public String bestPlayerName;
        public int bestPlayerKills;
    }

    /** Statistiques cumulées d'un joueur à travers tous les tournois. */
    public static class PlayerStat {
        public UUID uuid;
        public String name;
        public int tournamentsWon = 0;
        public int tournamentsPlayed = 0;
        public int totalKills = 0;
        public int bestKillsInAMatch = 0;
    }

    private final HikaBrainPlugin plugin;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<UUID, PlayerStat> playerStats = new HashMap<>();

    public TournamentHistoryManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "tournaments");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "history.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection historySection = config.getConfigurationSection("history");
        if (historySection != null) {
            for (String key : historySection.getKeys(false)) {
                ConfigurationSection s = historySection.getConfigurationSection(key);
                if (s == null) continue;
                Entry e = new Entry();
                e.name = s.getString("name", key);
                e.format = s.getString("format", "?");
                e.finishedAt = s.getLong("finished-at", 0);
                e.participants = s.getInt("participants", 0);
                e.champion = s.getString("champion", "?");
                e.runnerUp = s.getString("runner-up", "?");
                e.thirdPlace = s.getStringList("third-place");
                e.averageMatchSeconds = s.getDouble("average-match-seconds", 0);
                e.bestPlayerName = s.getString("best-player-name", "?");
                e.bestPlayerKills = s.getInt("best-player-kills", 0);
                entries.add(e);
            }
        }

        ConfigurationSection statsSection = config.getConfigurationSection("player-stats");
        if (statsSection != null) {
            for (String uuidStr : statsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection s = statsSection.getConfigurationSection(uuidStr);
                    if (s == null) continue;
                    PlayerStat stat = new PlayerStat();
                    stat.uuid = uuid;
                    stat.name = s.getString("name", uuidStr);
                    stat.tournamentsWon = s.getInt("tournaments-won", 0);
                    stat.tournamentsPlayed = s.getInt("tournaments-played", 0);
                    stat.totalKills = s.getInt("total-kills", 0);
                    stat.bestKillsInAMatch = s.getInt("best-kills-in-a-match", 0);
                    playerStats.put(uuid, stat);
                } catch (IllegalArgumentException ignored) {
                    // uuid invalide dans le fichier, on ignore la ligne
                }
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String path = "history." + i;
            config.set(path + ".name", e.name);
            config.set(path + ".format", e.format);
            config.set(path + ".finished-at", e.finishedAt);
            config.set(path + ".participants", e.participants);
            config.set(path + ".champion", e.champion);
            config.set(path + ".runner-up", e.runnerUp);
            config.set(path + ".third-place", e.thirdPlace);
            config.set(path + ".average-match-seconds", e.averageMatchSeconds);
            config.set(path + ".best-player-name", e.bestPlayerName);
            config.set(path + ".best-player-kills", e.bestPlayerKills);
        }
        for (PlayerStat stat : playerStats.values()) {
            String path = "player-stats." + stat.uuid;
            config.set(path + ".name", stat.name);
            config.set(path + ".tournaments-won", stat.tournamentsWon);
            config.set(path + ".tournaments-played", stat.tournamentsPlayed);
            config.set(path + ".total-kills", stat.totalKills);
            config.set(path + ".best-kills-in-a-match", stat.bestKillsInAMatch);
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder l'historique des tournois : " + ex.getMessage());
        }
    }

    /**
     * Enregistre un tournoi terminé : met à jour l'historique et les stats de tous
     * les participants (à appeler une fois le champion désigné).
     */
    public void recordFinishedTournament(Tournament tournament, double averageMatchSeconds,
                                          Map<UUID, Integer> matchKillsByPlayer, Map<UUID, String> playerNames) {
        Entry entry = new Entry();
        entry.name = tournament.getName();
        entry.format = tournament.getFormat().getLabel();
        entry.finishedAt = System.currentTimeMillis();
        entry.participants = tournament.getRegisteredPlayerCount();
        entry.champion = tournament.getChampion() != null ? tournament.getChampion().getDisplayName() : "?";
        entry.runnerUp = tournament.getRunnerUp() != null ? tournament.getRunnerUp().getDisplayName() : "?";
        for (TournamentTeam t : tournament.getThirdPlace()) {
            entry.thirdPlace.add(t.getDisplayName());
        }
        entry.averageMatchSeconds = averageMatchSeconds;

        // Détermination du meilleur joueur (record de kills) de ce tournoi
        UUID bestUuid = null;
        int bestKills = -1;
        for (Map.Entry<UUID, Integer> e : matchKillsByPlayer.entrySet()) {
            if (e.getValue() > bestKills) {
                bestKills = e.getValue();
                bestUuid = e.getKey();
            }
        }
        entry.bestPlayerName = bestUuid != null ? playerNames.getOrDefault(bestUuid, bestUuid.toString()) : "?";
        entry.bestPlayerKills = Math.max(bestKills, 0);
        entries.add(entry);

        // Mise à jour des stats de chaque participant
        for (TournamentTeam team : tournament.getRegistered()) {
            for (UUID uuid : team.getMembers()) {
                PlayerStat stat = playerStats.computeIfAbsent(uuid, u -> {
                    PlayerStat s = new PlayerStat();
                    s.uuid = u;
                    s.name = playerNames.getOrDefault(u, u.toString());
                    return s;
                });
                stat.name = playerNames.getOrDefault(uuid, stat.name);
                stat.tournamentsPlayed++;
                if (tournament.getChampion() == team) {
                    stat.tournamentsWon++;
                }
                int kills = matchKillsByPlayer.getOrDefault(uuid, 0);
                stat.totalKills += kills;
                if (kills > stat.bestKillsInAMatch) {
                    stat.bestKillsInAMatch = kills;
                }
            }
        }

        save();
    }

    public List<Entry> getHistory() {
        return entries;
    }

    public List<Entry> getRecentHistory(int limit) {
        List<Entry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparingLong((Entry e) -> e.finishedAt).reversed());
        return copy.subList(0, Math.min(limit, copy.size()));
    }

    public List<PlayerStat> getTopByWins(int limit) {
        List<PlayerStat> list = new ArrayList<>(playerStats.values());
        list.sort(Comparator.comparingInt((PlayerStat p) -> p.tournamentsWon).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    public List<PlayerStat> getTopByKills(int limit) {
        List<PlayerStat> list = new ArrayList<>(playerStats.values());
        list.sort(Comparator.comparingInt((PlayerStat p) -> p.totalKills).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    public PlayerStat getKillRecordHolder() {
        PlayerStat best = null;
        for (PlayerStat s : playerStats.values()) {
            if (best == null || s.bestKillsInAMatch > best.bestKillsInAMatch) {
                best = s;
            }
        }
        return best;
    }

    public static String formatDate(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(millis));
    }
}
