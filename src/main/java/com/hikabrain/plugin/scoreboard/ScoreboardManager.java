package com.hikabrain.plugin.scoreboard;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
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
    
    // Scoreboard PAR JOUEUR (pour afficher leur K/D personnel)
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    
    private BukkitTask updateTask;

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
        title = plugin.getConfig().getString("scoreboard.title", "&8[&b&lHEROCRAFT&8] &6&lHikaBrain");
    }

    /**
     * Démarre la tâche de mise à jour du scoreboard (toutes les secondes)
     */
    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateAllPlayerScoreboards();
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
        for (UUID uuid : new HashSet<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        playerScoreboards.clear();
    }

    /**
     * Crée et affiche le scoreboard pour un joueur
     */
    public void showScoreboard(Player player, GameManager gm) {
        Scoreboard board = createPlayerScoreboard(player, gm);
        playerScoreboards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    /**
     * Supprime le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Crée un scoreboard pour un joueur spécifique
     */
    private Scoreboard createPlayerScoreboard(Player player, GameManager gm) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // Créer l'objectif pour le sidebar
        Objective objective = board.registerNewObjective("hikabrain", Criteria.DUMMY, parseColor(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Ajouter les lignes du sidebar avec le K/D du joueur
        addPlayerSidebarLines(board, objective, player, gm);
        
        return board;
    }

    /**
     * Ajoute les lignes du sidebar pour un joueur spécifique
     */
    private void addPlayerSidebarLines(Scoreboard board, Objective objective, Player player, GameManager gm) {
        int redScore = gm.getScore(com.hikabrain.plugin.game.Team.RED);
        int blueScore = gm.getScore(com.hikabrain.plugin.game.Team.BLUE);
        int players = gm.getPlayerCount();
        
        // K/D personnel du joueur
        int kills = gm.getPlayerKills(player.getUniqueId());
        int deaths = gm.getPlayerDeaths(player.getUniqueId());
        double kd = deaths > 0 ? (double) kills / deaths : kills;
        String kdStr = String.format("%.1f", kd);
        
        // Couleur selon l'équipe
        com.hikabrain.plugin.game.Team team = gm.getTeam(player);
        String teamColor = team == com.hikabrain.plugin.game.Team.RED ? "&c" : "&9";
        String teamName = team == com.hikabrain.plugin.game.Team.RED ? "Rouge" : "Bleu";
        
        // Lignes du sidebar
        int teamScore = team == com.hikabrain.plugin.game.Team.RED ? redScore : blueScore;
        String[] lines = {
            "&6&lHikaBrain",
            "&7" + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500,
            teamColor + "\u2764 " + teamName + ": " + teamColor + teamScore + "&7/&f5",
            "&7" + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500,
            "&fTon K/D: &a" + kills + "&7/&c" + deaths + " &8(&a" + kdStr + "&8)",
            "&7" + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500,
            "&fJoueurs: &b" + players
        };
        
        // Score à 0 pour toutes les lignes
        for (int i = 0; i < lines.length; i++) {
            String parsed = parseColor(lines[i]);
            String identifier = String.valueOf(i);
            
            org.bukkit.scoreboard.Team mcTeam;
            String teamName2 = "sb_" + i;
            if (board.getTeam(teamName2) != null) {
                mcTeam = board.getTeam(teamName2);
            } else {
                mcTeam = board.registerNewTeam(teamName2);
            }
            
            mcTeam.setPrefix(parsed);
            mcTeam.setSuffix("");
            mcTeam.addEntry(identifier);
            objective.getScore(identifier).setScore(0);
        }
    }

    /**
     * Met à jour tous les scoreboards des joueurs
     */
    private void updateAllPlayerScoreboards() {
        // Supprimer les joueurs qui ne jouent plus
        playerScoreboards.keySet().removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return true;
            GameManager gm = plugin.getArenaManager().findArenaOf(player);
            return gm == null || !gm.isPlaying(player);
        });
        
        // Mettre à jour chaque scoreboard
        for (UUID uuid : new HashSet<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            
            GameManager gm = plugin.getArenaManager().findArenaOf(player);
            if (gm == null || !gm.isPlaying(player)) continue;
            
            Scoreboard board = playerScoreboards.get(uuid);
            updatePlayerSidebar(board, player, gm);
            player.setScoreboard(board);
        }
    }

    /**
     * Met à jour le sidebar pour un joueur
     */
    private void updatePlayerSidebar(Scoreboard board, Player player, GameManager gm) {
        // Récupérer ou créer l'objectif
        Objective objective = board.getObjective("hikabrain");
        if (objective == null) {
            objective = board.registerNewObjective("hikabrain", Criteria.DUMMY, parseColor(title));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        
        // Supprimer les anciennes entrées
        Set<org.bukkit.scoreboard.Team> oldTeams = new HashSet<>(board.getTeams());
        for (org.bukkit.scoreboard.Team team : oldTeams) {
            if (team.getName().startsWith("sb_")) {
                for (String entry : team.getEntries()) {
                    board.resetScores(entry);
                }
                team.unregister();
            }
        }
        
        // Recréer les lignes
        addPlayerSidebarLines(board, objective, player, gm);
    }

    public void onGameStart(GameManager gm) {
        // Pas besoin pour le système par joueur
    }

    public void onLobbyReset(GameManager gm) {
        // Pas besoin pour le système par joueur
    }

    public void onRoundReset(GameManager gm) {
        // Pas besoin pour le système par joueur
    }

    private String parseColor(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    
    // Méthodes de compatibilité (non utilisées dans le nouveau système)
    public void setServerName(String name) {}
    public void setGameName(String name) {}
    public String getServerName() { return "HEROCRAFT"; }
    public String getGameName() { return "HikaBrain"; }
    public void setLines(java.util.List<String> lines) {}
}
