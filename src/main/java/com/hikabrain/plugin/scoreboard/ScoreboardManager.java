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
 * Utilise un scoreboard SHARED par arène (pas par joueur) pour le TAB.
 * Affiche : score des équipes, temps écoulé, nom du serveur et du jeu.
 */
public class ScoreboardManager {

    private final HikaBrainPlugin plugin;
    
    // Scoreboard SHARED par arène (une seule instance par arène, pas par joueur)
    private final Map<String, Scoreboard> arenaScoreboards = new HashMap<>();
    private final Map<String, Integer> arenaStartTimes = new HashMap<>();
    private final Set<UUID> playersWithBoard = new HashSet<>();
    
    private BukkitTask updateTask;

    private String serverName;
    private String gameName;
    private String title;

    public ScoreboardManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startUpdateTask();
    }

    /**
     * Charge la configuration du scoreboard depuis config.yml
     */
    public void loadConfig() {
        String basePath = "scoreboard.";
        serverName = plugin.getConfig().getString(basePath + "server-name", "&b&lHEROCRAFT");
        gameName = plugin.getConfig().getString(basePath + "game-name", "&6&lHikaBrain");
        title = plugin.getConfig().getString(basePath + "title", "&8[&b&lHEROCRAFT&8] &6&lHikaBrain");
    }

    /**
     * Démarre la tâche de mise à jour du scoreboard (toutes les secondes)
     */
    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateAllArenaScoreboards();
        }, 0L, 20L);
    }

    /**
     * Arrête le scoreboard pour tous les joueurs
     */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (UUID uuid : new HashSet<>(playersWithBoard)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        playersWithBoard.clear();
        arenaScoreboards.clear();
        arenaStartTimes.clear();
    }

    /**
     * Met à jour le scoreboard quand un joueur rejoint
     */
    public void showScoreboard(Player player, GameManager gm) {
        String arenaName = gm.getName();
        
        // Récupérer ou créer le scoreboard partagé pour cette arène
        Scoreboard board = arenaScoreboards.computeIfAbsent(arenaName, k -> createNewScoreboard(gm));
        
        playersWithBoard.add(player.getUniqueId());
        player.setScoreboard(board);
        
        // Enregistrer le temps de début si la partie a commencé
        if (gm.getState() == GameState.PLAYING || gm.getState() == GameState.ROUND_RESET) {
            if (!arenaStartTimes.containsKey(arenaName)) {
                arenaStartTimes.put(arenaName, (int) (System.currentTimeMillis() / 1000));
            }
        }
    }

    /**
     * Supprime le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        playersWithBoard.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Crée un nouveau scoreboard partagé pour une arène
     */
    private Scoreboard createNewScoreboard(GameManager gm) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // Créer les équipes Minecraft pour le TAB
        org.bukkit.scoreboard.Team redTeam = board.registerNewTeam("red");
        redTeam.setPrefix(ChatColor.RED + "");
        redTeam.setDisplayName("Rouge");
        
        org.bukkit.scoreboard.Team blueTeam = board.registerNewTeam("blue");
        blueTeam.setPrefix(ChatColor.BLUE + "");
        blueTeam.setDisplayName("Bleu");
        
        // Créer l'objectif pour le sidebar
        Objective objective = board.registerNewObjective("hikabrain", Criteria.DUMMY, parseColor(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Ajouter les lignes du sidebar
        addSidebarLines(board, objective, gm);
        
        return board;
    }

    /**
     * Ajoute les lignes du sidebar
     */
    private void addSidebarLines(Scoreboard board, Objective objective, GameManager gm) {
        String arenaName = gm.getName();
        int elapsed = arenaStartTimes.containsKey(arenaName) 
            ? (int) (System.currentTimeMillis() / 1000) - arenaStartTimes.get(arenaName) 
            : 0;
        
        int redScore = gm.getScore(com.hikabrain.plugin.game.Team.RED);
        int blueScore = gm.getScore(com.hikabrain.plugin.game.Team.BLUE);
        int players = gm.getPlayerCount();
        
        StatsManager stats = plugin.getStatsManager();
        int redKills = stats.getRedKills();
        int redDeaths = stats.getRedDeaths();
        int blueKills = stats.getBlueKills();
        int blueDeaths = stats.getBlueDeaths();
        double redKD = stats.getRedKD();
        double blueKD = stats.getBlueKD();
        
        // Lignes du sidebar
        String[] lines = {
            "&6&lHikaBrain",
            "&7&m-----------",
            "&c\u2764 &fRouge: &c" + redScore,
            "&9\u2764 &fBleu: &9" + blueScore,
            "&7&m-----------",
            "&fJoueurs: &b" + players,
            "&fServeur: &b" + stripColor(serverName),
            "&fJeu: &6" + stripColor(gameName),
            "&7&m-----------",
            "&cK/D: &c" + redKills + "&7/&c" + redDeaths,
            "&9K/D: &9" + blueKills + "&7/&9" + blueDeaths,
            "&7&m-----------",
            "&cWins: &c" + stats.getRedWins(),
            "&9Wins: &9" + stats.getBlueWins(),
            "&7&m-----------",
            "&fTemps: &e" + formatTime(elapsed),
            "&7&m-----------"
        };
        
        int score = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String parsed = parseColor(lines[i]);
            
            // Identifiants simples (chiffres) pour le sidebar
            String identifier = String.valueOf(i);
            
            // Créer l'équipe pour cette ligne
            org.bukkit.scoreboard.Team mcTeam;
            String teamName = "sb_team_" + i;
            if (board.getTeam(teamName) != null) {
                mcTeam = board.getTeam(teamName);
            } else {
                mcTeam = board.registerNewTeam(teamName);
            }
            
            // Tout le texte dans le prefix
            String prefix = parsed;
            String suffix = "";
            
            // Couper si trop long
            if (parsed.length() > 48) {
                prefix = parsed.substring(0, 40) + ChatColor.WHITE;
                suffix = ChatColor.RESET + parsed.substring(40);
            }
            
            mcTeam.addEntry(identifier);
            mcTeam.setPrefix(prefix);
            mcTeam.setSuffix(suffix);
            
            objective.getScore(identifier).setScore(score);
            score--;
        }
    }

    /**
     * Met à jour tous les scoreboards partagés
     */
    private void updateAllArenaScoreboards() {
        // Supprimer les arènes qui n'existent plus
        Set<String> activeArenas = new HashSet<>();
        for (com.hikabrain.plugin.game.GameManager gm : plugin.getArenaManager().getAllGameManagers()) {
            String arenaName = gm.getName();
            activeArenas.add(arenaName);
            
            // Vérifier si le scoreboard existe
            if (!arenaScoreboards.containsKey(arenaName)) {
                continue;
            }
            
            Scoreboard board = arenaScoreboards.get(arenaName);
            
            // Mettre à jour les équipes du TAB avec les joueurs actuels
            updatePlayerTeams(board, gm);
            
            // Recréer le sidebar avec les nouvelles valeurs
            updateSidebar(board, gm);
            
            // Rafraîchir le scoreboard pour tous les joueurs de cette arène
            refreshPlayersInArena(arenaName, board, gm);
        }
        
        // Nettoyer les arènes inactives
        arenaScoreboards.keySet().removeIf(name -> !activeArenas.contains(name));
        arenaStartTimes.keySet().removeIf(name -> !activeArenas.contains(name));
    }

    /**
     * Met à jour les équipes du TAB avec les joueurs actuels
     */
    private void updatePlayerTeams(Scoreboard board, GameManager gm) {
        org.bukkit.scoreboard.Team redTeam = board.getTeam("red");
        org.bukkit.scoreboard.Team blueTeam = board.getTeam("blue");
        
        if (redTeam == null || blueTeam == null) return;
        
        // Vider les équipes
        Set<String> redPlayers = new HashSet<>(redTeam.getEntries());
        Set<String> bluePlayers = new HashSet<>(blueTeam.getEntries());
        
        for (String entry : redPlayers) {
            redTeam.removeEntry(entry);
        }
        for (String entry : bluePlayers) {
            blueTeam.removeEntry(entry);
        }
        
        // Ajouter les joueurs actuels
        for (UUID uuid : gm.getPlayerTeams().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            
            com.hikabrain.plugin.game.Team team = gm.getPlayerTeams().get(uuid);
            if (team == com.hikabrain.plugin.game.Team.RED) {
                redTeam.addEntry(player.getName());
            } else if (team == com.hikabrain.plugin.game.Team.BLUE) {
                blueTeam.addEntry(player.getName());
            }
        }
    }

    /**
     * Met à jour le sidebar
     */
    private void updateSidebar(Scoreboard board, GameManager gm) {
        String arenaName = gm.getName();
        
        // Récupérer ou créer l'objectif
        Objective objective = board.getObjective("hikabrain");
        if (objective == null) {
            objective = board.registerNewObjective("hikabrain", Criteria.DUMMY, parseColor(title));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        
        // Supprimer les anciennes entrées du sidebar
        Set<org.bukkit.scoreboard.Team> oldTeams = new HashSet<>(board.getTeams());
        for (org.bukkit.scoreboard.Team team : oldTeams) {
            if (team.getName().startsWith("sb_team_")) {
                for (String entry : team.getEntries()) {
                    objective.getScore(entry).setScore(-999); // Score bidon pour标识
                    board.resetScores(entry);
                }
                team.unregister();
            }
        }
        
        // Recréer les lignes
        addSidebarLines(board, objective, gm);
    }

    /**
     * Rafraîchit le scoreboard pour tous les joueurs d'une arène
     */
    private void refreshPlayersInArena(String arenaName, Scoreboard board, GameManager gm) {
        for (UUID uuid : new HashSet<>(playersWithBoard)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            
            if (gm.isPlaying(player)) {
                player.setScoreboard(board);
            }
        }
    }

    /**
     * Called when game starts to record start time
     */
    public void onGameStart(GameManager gm) {
        String arenaName = gm.getName();
        arenaStartTimes.put(arenaName, (int) (System.currentTimeMillis() / 1000));
    }

    /**
     * Called when game resets to lobby to clear start time
     */
    public void onLobbyReset(GameManager gm) {
        String arenaName = gm.getName();
        arenaStartTimes.remove(arenaName);
    }

    /**
     * Called when round resets to refresh the scoreboard
     */
    public void onRoundReset(GameManager gm) {
        // Le scoreboard sera mis à jour automatiquement
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private String parseColor(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String stripColor(String text) {
        return ChatColor.stripColor(parseColor(text));
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }

    public String getServerName() {
        return serverName;
    }

    public String getGameName() {
        return gameName;
    }

    public String getTitle() {
        return title;
    }

    public void setServerName(String name) {
        this.serverName = name;
    }

    public void setGameName(String name) {
        this.gameName = name;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // Méthode requise pour compatibilité (non utilisée dans le nouveau système)
    public void setLines(java.util.List<String> lines) {
        // Les lignes sont maintenant codées en dur pour plus de fiabilité
    }
}
