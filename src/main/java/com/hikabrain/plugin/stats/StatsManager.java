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
 * Gère les statistiques globales d'HikaBrain par mode de jeu (1v1, 2v2, 3v3, 4v4).
 * Les statistiques sont sauvegardées dans stats.yml, séparées par catégorie.
 */
public class StatsManager {

    /** Les 4 modes supportés. */
    public enum GameMode {
        V1("1v1"), V2("2v2"), V3("3v3"), V4("4v4");

        private final String label;
        GameMode(String label) { this.label = label; }
        public String getLabel() { return label; }

        /** Résout le mode depuis le nombre de joueurs par équipe. */
        public static GameMode fromTeamSize(int size) {
            return switch (size) {
                case 1  -> V1;
                case 2  -> V2;
                case 3  -> V3;
                default -> V4;
            };
        }
    }

    private static String key(GameMode m, String stat) { return m.getLabel() + "." + stat; }

    private final Map<GameMode, ModeStats> modes = new HashMap<>();
    private int totalGames;
    private int totalCaptures;

    private final HikaBrainPlugin plugin;
    private final File             statsFile;
    private FileConfiguration      statsConfig;

    public StatsManager(HikaBrainPlugin plugin) {
        this.plugin    = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        for (GameMode m : GameMode.values()) modes.put(m, new ModeStats());
        loadStats();
    }

    public void loadStats() {
        if (!statsFile.exists()) {
            try { statsFile.getParentFile().mkdirs(); statsFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Impossible de créer stats.yml: " + e.getMessage()); }
        }
        statsConfig   = YamlConfiguration.loadConfiguration(statsFile);
        totalGames    = statsConfig.getInt("total-games",    0);
        totalCaptures = statsConfig.getInt("total-captures", 0);
        for (GameMode m : GameMode.values()) {
            ModeStats s  = modes.get(m);
            s.redWins    = statsConfig.getInt(key(m, "wins.red"),    0);
            s.blueWins   = statsConfig.getInt(key(m, "wins.blue"),   0);
            s.redKills   = statsConfig.getInt(key(m, "kills.red"),   0);
            s.redDeaths  = statsConfig.getInt(key(m, "deaths.red"),  0);
            s.blueKills  = statsConfig.getInt(key(m, "kills.blue"),  0);
            s.blueDeaths = statsConfig.getInt(key(m, "deaths.blue"), 0);
        }
    }

    public void saveStats() {
        statsConfig.set("total-games",    totalGames);
        statsConfig.set("total-captures", totalCaptures);
        for (GameMode m : GameMode.values()) {
            ModeStats s = modes.get(m);
            statsConfig.set(key(m, "wins.red"),    s.redWins);
            statsConfig.set(key(m, "wins.blue"),   s.blueWins);
            statsConfig.set(key(m, "kills.red"),   s.redKills);
            statsConfig.set(key(m, "deaths.red"),  s.redDeaths);
            statsConfig.set(key(m, "kills.blue"),  s.blueKills);
            statsConfig.set(key(m, "deaths.blue"), s.blueDeaths);
        }
        try { statsConfig.save(statsFile); }
        catch (IOException e) { plugin.getLogger().severe("Impossible de sauvegarder stats.yml: " + e.getMessage()); }
    }

    // ── Mutateurs ──────────────────────────────────────────────────────────────

    public void addWin(Team winner, int playersPerTeam) {
        GameMode m  = GameMode.fromTeamSize(playersPerTeam);
        ModeStats s = modes.get(m);
        if (winner == Team.RED) s.redWins++; else s.blueWins++;
        totalGames++;
        saveStats();
    }

    /** Rétro-compat : suppose 1v1. */
    public void addWin(Team winner) { addWin(winner, 1); }

    public void addKill(Team team, int playersPerTeam) {
        ModeStats s = modes.get(GameMode.fromTeamSize(playersPerTeam));
        if (team == Team.RED) s.redKills++; else s.blueKills++;
        saveStats();
    }
    public void addKill(Team team) { addKill(team, 1); }

    public void addDeath(Team team, int playersPerTeam) {
        ModeStats s = modes.get(GameMode.fromTeamSize(playersPerTeam));
        if (team == Team.RED) s.redDeaths++; else s.blueDeaths++;
        saveStats();
    }
    public void addDeath(Team team) { addDeath(team, 1); }

    public void addCapture() { totalCaptures++; saveStats(); }

    // ── Accesseurs globaux ────────────────────────────────────────────────────

    public int getRedWins()    { return modes.values().stream().mapToInt(s -> s.redWins).sum(); }
    public int getBlueWins()   { return modes.values().stream().mapToInt(s -> s.blueWins).sum(); }
    public int getRedKills()   { return modes.values().stream().mapToInt(s -> s.redKills).sum(); }
    public int getBlueKills()  { return modes.values().stream().mapToInt(s -> s.blueKills).sum(); }
    public int getRedDeaths()  { return modes.values().stream().mapToInt(s -> s.redDeaths).sum(); }
    public int getBlueDeaths() { return modes.values().stream().mapToInt(s -> s.blueDeaths).sum(); }
    public int getTotalGames()    { return totalGames; }
    public int getTotalCaptures() { return totalCaptures; }

    public double getRedKD()  { int d=getRedDeaths(),k=getRedKills();   return d==0?k:Math.round((double)k/d*100)/100.0; }
    public double getBlueKD() { int d=getBlueDeaths(),k=getBlueKills(); return d==0?k:Math.round((double)k/d*100)/100.0; }

    // ── Accesseurs par mode ────────────────────────────────────────────────────

    public int getRedWins(GameMode m)    { return modes.get(m).redWins; }
    public int getBlueWins(GameMode m)   { return modes.get(m).blueWins; }
    public int getRedKills(GameMode m)   { return modes.get(m).redKills; }
    public int getBlueKills(GameMode m)  { return modes.get(m).blueKills; }
    public int getRedDeaths(GameMode m)  { return modes.get(m).redDeaths; }
    public int getBlueDeaths(GameMode m) { return modes.get(m).blueDeaths; }
    public int getTotalWins(GameMode m)  { return modes.get(m).redWins + modes.get(m).blueWins; }

    public double getRedKD(GameMode m) {
        int d=modes.get(m).redDeaths, k=modes.get(m).redKills;
        return d==0?k:Math.round((double)k/d*100)/100.0;
    }
    public double getBlueKD(GameMode m) {
        int d=modes.get(m).blueDeaths, k=modes.get(m).blueKills;
        return d==0?k:Math.round((double)k/d*100)/100.0;
    }

    // ── Reset ──────────────────────────────────────────────────────────────────

    public void resetStats() {
        modes.values().forEach(ModeStats::reset);
        totalGames = totalCaptures = 0;
        saveStats();
    }

    public Map<String, Object> getAllStats() {
        Map<String, Object> s = new HashMap<>();
        s.put("red_wins", getRedWins()); s.put("blue_wins", getBlueWins());
        s.put("red_kills", getRedKills()); s.put("red_deaths", getRedDeaths());
        s.put("blue_kills", getBlueKills()); s.put("blue_deaths", getBlueDeaths());
        s.put("red_kd", getRedKD()); s.put("blue_kd", getBlueKD());
        s.put("total_games", totalGames); s.put("total_captures", totalCaptures);
        return s;
    }

    private static class ModeStats {
        int redWins, blueWins, redKills, redDeaths, blueKills, blueDeaths;
        void reset() { redWins=blueWins=redKills=redDeaths=blueKills=blueDeaths=0; }
    }
}
