package com.hikabrain.plugin.scoreboard;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Gère le scoreboard affiché aux joueurs pendant la partie HikaBrain.
 * Affiche : score des équipes, temps écoulé, nom du serveur et du jeu.
 */
public class ScoreboardManager {

    private final HikaBrainPlugin plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Integer> playerGameStartTimes = new HashMap<>();
    private BukkitTask updateTask;

    private String serverName;
    private String gameName;
    private String title;
    private List<String> objectiveLines;

    public ScoreboardManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startUpdateTask();
    }

    /**
     * Charge la configuration du scoreboard depuis config.yml
     */
    public void loadConfig() {
        // Utiliser les valeurs par défaut si scoreboard n'est pas configuré
        String basePath = "scoreboard.";
        serverName = plugin.getConfig().getString(basePath + "server-name", "&b&lHEROCRAFT");
        gameName = plugin.getConfig().getString(basePath + "game-name", "&6&lHikaBrain");
        title = plugin.getConfig().getString(basePath + "title", "&8[&b&lHEROCRAFT&8] &6&lHikaBrain");
        
        // Charger les lignes du scoreboard
        objectiveLines = plugin.getConfig().getStringList(basePath + "lines");
        
        if (objectiveLines.isEmpty()) {
            objectiveLines = Arrays.asList(
                "&7&m--------------------",
                "&fServeur: &b" + stripColor(serverName),
                "&fJeu: &6" + stripColor(gameName),
                "&7&m--------------------",
                "&f&l▸ &c❤ &fRouge: &c%red_score%",
                "&f&l▸ &9❤ &9Bleu: &9%blue_score%",
                "&7&m--------------------",
                "&f&l▸ &cKills: &c%red_kills% &7/ &cDeaths: &c%red_deaths%",
                "&f&l▸ &9Kills: &9%blue_kills% &7/ &9Deaths: &9%blue_deaths%",
                "&7&m--------------------",
                "&f&l▸ &cVictoires: &c%red_wins%",
                "&f&l▸ &9Victoires: &9%blue_wins%",
                "&7&m--------------------",
                "&f&l▸ &cK/D: &c%red_kd%",
                "&f&l▸ &9K/D: &9%blue_kd%",
                "&7&m--------------------",
                "&fTemps: &e%elapsed_time%",
                "&7&m--------------------"
            );
        }
    }

    /**
     * Démarre la tâche de mise à jour du scoreboard (toutes les secondes)
     */
    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateAllScoreboards();
        }, 0L, 20L);
    }

    /**
     * Arrête le scoreboard pour un joueur
     */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (UUID uuid : new HashSet<>(playerScoreboards.keySet())) {
            removeScoreboard(Bukkit.getPlayer(uuid));
        }
        playerScoreboards.clear();
        playerGameStartTimes.clear();
    }

    /**
     * Ajoute le scoreboard à un joueur quand il rejoint une partie
     */
    public void showScoreboard(Player player, GameManager gm) {
        if (gm.getState() == GameState.WAITING || gm.getState() == GameState.COUNTDOWN) {
            showLobbyScoreboard(player, gm);
        } else if (gm.getState() == GameState.PLAYING || gm.getState() == GameState.ROUND_RESET) {
            showGameScoreboard(player, gm);
        }
    }

    /**
     * Affiche le scoreboard du lobby
     */
    private void showLobbyScoreboard(Player player, GameManager gm) {
        Scoreboard board = createScoreboard(
            parseColor(title),
            "lobby",
            gm.getPlayerCount(),
            gm.getScore(com.hikabrain.plugin.game.Team.RED),
            gm.getScore(com.hikabrain.plugin.game.Team.BLUE),
            0,
            plugin.getStatsManager().getRedWins(),
            plugin.getStatsManager().getBlueWins(),
            plugin.getStatsManager().getRedKills(),
            plugin.getStatsManager().getRedDeaths(),
            plugin.getStatsManager().getBlueKills(),
            plugin.getStatsManager().getBlueDeaths(),
            plugin.getStatsManager().getRedKD(),
            plugin.getStatsManager().getBlueKD()
        );
        
        playerScoreboards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    /**
     * Affiche le scoreboard de jeu et enregistre le temps de début
     */
    private void showGameScoreboard(Player player, GameManager gm) {
        playerGameStartTimes.put(player.getUniqueId(), (int) (System.currentTimeMillis() / 1000));
        
        Scoreboard board = createScoreboard(
            parseColor(title),
            "game",
            gm.getPlayerCount(),
            gm.getScore(com.hikabrain.plugin.game.Team.RED),
            gm.getScore(com.hikabrain.plugin.game.Team.BLUE),
            0,
            plugin.getStatsManager().getRedWins(),
            plugin.getStatsManager().getBlueWins(),
            plugin.getStatsManager().getRedKills(),
            plugin.getStatsManager().getRedDeaths(),
            plugin.getStatsManager().getBlueKills(),
            plugin.getStatsManager().getBlueDeaths(),
            plugin.getStatsManager().getRedKD(),
            plugin.getStatsManager().getBlueKD()
        );
        
        playerScoreboards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    /**
     * Crée un nouveau scoreboard avec les informations données
     */
    private Scoreboard createScoreboard(String title, String objectiveName, int players, int redScore, int blueScore, int elapsedSeconds, int redWins, int blueWins, int redKills, int redDeaths, int blueKills, int blueDeaths, double redKD, double blueKD) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // Créer l'objectif avec le titre
        Objective objective = board.registerNewObjective(objectiveName, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Ajouter les lignes
        int score = objectiveLines.size();
        for (String line : objectiveLines) {
            String parsed = parseLine(line, players, redScore, blueScore, elapsedSeconds, redWins, blueWins, redKills, redDeaths, blueKills, blueDeaths, redKD, blueKD);
            org.bukkit.scoreboard.Team mcTeam = board.registerNewTeam("line_" + score);
            String identifier = ChatColor.stripColor(parsed);
            if (identifier.isEmpty()) identifier = "blank_" + score;
            
            // Diviser la ligne si elle est trop longue (limite de 48 caractères par équipe)
            String prefix;
            String suffix = "";
            
            if (parsed.length() > 48) {
                // Couper la ligne en prefix/suffix
                String firstHalf = parsed.substring(0, 40);
                String secondHalf = parsed.substring(40);
                // Ajouter des codes couleurs invisibles pour maintenir la séparation
                prefix = firstHalf + ChatColor.WHITE;
                suffix = ChatColor.RESET + secondHalf;
            } else {
                prefix = parsed;
            }
            
            mcTeam.addEntry(identifier);
            mcTeam.setPrefix(prefix);
            mcTeam.setSuffix(suffix);
            
            objective.getScore(identifier).setScore(score);
            score--;
        }

        return board;
    }

    /**
     * Parse une ligne du scoreboard et remplace les variables
     */
    private String parseLine(String line, int players, int redScore, int blueScore, int elapsedSeconds, int redWins, int blueWins, int redKills, int redDeaths, int blueKills, int blueDeaths, double redKD, double blueKD) {
        String result = line;
        
        // Remplacer les variables
        result = result.replace("%server%", stripColor(serverName));
        result = result.replace("%game%", stripColor(gameName));
        result = result.replace("%red_score%", String.valueOf(redScore));
        result = result.replace("%blue_score%", String.valueOf(blueScore));
        result = result.replace("%red_wins%", String.valueOf(redWins));
        result = result.replace("%blue_wins%", String.valueOf(blueWins));
        result = result.replace("%red_kills%", String.valueOf(redKills));
        result = result.replace("%red_deaths%", String.valueOf(redDeaths));
        result = result.replace("%blue_kills%", String.valueOf(blueKills));
        result = result.replace("%blue_deaths%", String.valueOf(blueDeaths));
        result = result.replace("%red_kd%", String.valueOf(redKD));
        result = result.replace("%blue_kd%", String.valueOf(blueKD));
        result = result.replace("%players%", String.valueOf(players));
        result = result.replace("%elapsed_time%", formatTime(elapsedSeconds));
        
        return parseColor(result);
    }

    /**
     * Met à jour tous les scoreboards
     */
    private void updateAllScoreboards() {
        for (UUID uuid : new HashSet<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                playerScoreboards.remove(uuid);
                playerGameStartTimes.remove(uuid);
                continue;
            }

            GameManager gm = plugin.getArenaManager().findArenaOf(player);
            if (gm == null) {
                removeScoreboard(player);
                continue;
            }

            // Calculer le temps écoulé
            Integer startTime = playerGameStartTimes.get(uuid);
            int elapsed = 0;
            if (startTime != null) {
                elapsed = (int) (System.currentTimeMillis() / 1000) - startTime;
            }

            // Récupérer les statistiques
            int redWins = plugin.getStatsManager().getRedWins();
            int blueWins = plugin.getStatsManager().getBlueWins();
            int redKills = plugin.getStatsManager().getRedKills();
            int redDeaths = plugin.getStatsManager().getRedDeaths();
            int blueKills = plugin.getStatsManager().getBlueKills();
            int blueDeaths = plugin.getStatsManager().getBlueDeaths();
            double redKD = plugin.getStatsManager().getRedKD();
            double blueKD = plugin.getStatsManager().getBlueKD();

            // Recréer le scoreboard avec les nouvelles valeurs
            Scoreboard newBoard = createScoreboard(
                parseColor(title),
                gm.getState() == GameState.PLAYING || gm.getState() == GameState.ROUND_RESET ? "game" : "lobby",
                gm.getPlayerCount(),
                gm.getScore(com.hikabrain.plugin.game.Team.RED),
                gm.getScore(com.hikabrain.plugin.game.Team.BLUE),
                elapsed,
                redWins,
                blueWins,
                redKills,
                redDeaths,
                blueKills,
                blueDeaths,
                redKD,
                blueKD
            );

            playerScoreboards.put(uuid, newBoard);
            player.setScoreboard(newBoard);
        }
    }

    /**
     * Supprime le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        playerScoreboards.remove(uuid);
        playerGameStartTimes.remove(uuid);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Met à jour le scoreboard quand un point est marqué
     */
    public void onScoreChange(Player player) {
        // Le scoreboard sera mis à jour automatiquement par la tâche périodique
    }

    /**
     * Met à jour le scoreboard quand la partie démarre
     */
    public void onGameStart(Player player) {
        playerGameStartTimes.put(player.getUniqueId(), (int) (System.currentTimeMillis() / 1000));
    }

    /**
     * Formate le temps en minutes:secondes
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /**
     * Parse les codes couleur (&a, &l, etc.) en couleurs Bukkit
     */
    private String parseColor(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Supprime les codes couleur d'un texte
     */
    private String stripColor(String text) {
        return ChatColor.stripColor(parseColor(text));
    }

    /**
     * Recharge la configuration du scoreboard
     */
    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }

    /**
     * Définit le nom du serveur (via commande)
     */
    public void setServerName(String name) {
        this.serverName = name;
        plugin.getConfig().set("scoreboard.server-name", name);
        plugin.saveConfig();
    }

    /**
     * Définit le nom du jeu (via commande)
     */
    public void setGameName(String name) {
        this.gameName = name;
        plugin.getConfig().set("scoreboard.game-name", name);
        plugin.saveConfig();
    }

    /**
     * Définit le titre du scoreboard (via commande)
     */
    public void setTitle(String title) {
        this.title = title;
        plugin.getConfig().set("scoreboard.title", title);
        plugin.saveConfig();
    }

    /**
     * Définit les lignes du scoreboard (via commande)
     */
    public void setLines(List<String> lines) {
        this.objectiveLines = lines;
        plugin.getConfig().set("scoreboard.lines", lines);
        plugin.saveConfig();
    }

    /**
     * Retourne le nom du serveur actuel
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Retourne le nom du jeu actuel
     */
    public String getGameName() {
        return gameName;
    }

    /**
     * Retourne le titre actuel
     */
    public String getTitle() {
        return title;
    }
}
