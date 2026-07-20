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
 * Gère le scoreboard affiché aux joueurs pendant la partie HikaBrain, ainsi qu'aux
 * spectateurs qui observent une partie en cours.
 * Utilise un scoreboard SHARED par arène (pas par joueur) pour le TAB.
 * Affiche : score des équipes, temps écoulé, nom du serveur et du jeu.
 */
public class ScoreboardManager {

    private final HikaBrainPlugin plugin;
    
    // Scoreboard PAR JOUEUR (pour afficher leur K/D personnel, ou le statut spectateur)
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    // Mémorise, pour chaque joueur affiché, s'il s'agit d'un spectateur (sidebar sans K/D
    // personnel) ou d'un joueur en partie (sidebar avec K/D personnel).
    private final Map<UUID, Boolean> spectatorFlags = new HashMap<>();
    
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
        spectatorFlags.clear();
    }

    /**
     * Crée et affiche le scoreboard pour un joueur en partie (avec son K/D personnel).
     */
    public void showScoreboard(Player player, GameManager gm) {
        Scoreboard board = createPlayerScoreboard(player, gm, false);
        playerScoreboards.put(player.getUniqueId(), board);
        spectatorFlags.put(player.getUniqueId(), false);
        player.setScoreboard(board);
    }

    /**
     * Crée et affiche le scoreboard pour un spectateur : mêmes lignes de score que les
     * joueurs qu'il observe (score des deux équipes, effectifs), sans K/D personnel
     * puisqu'il ne joue pas.
     */
    public void showSpectatorScoreboard(Player player, GameManager gm) {
        Scoreboard board = createPlayerScoreboard(player, gm, true);
        playerScoreboards.put(player.getUniqueId(), board);
        spectatorFlags.put(player.getUniqueId(), true);
        player.setScoreboard(board);
    }

    /**
     * Supprime le scoreboard d'un joueur (qu'il soit joueur ou spectateur)
     */
    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        spectatorFlags.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Crée un scoreboard pour un joueur ou un spectateur spécifique
     */
    private Scoreboard createPlayerScoreboard(Player player, GameManager gm, boolean isSpectator) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // Créer l'objectif pour le sidebar
        Objective objective = board.registerNewObjective("hikabrain", Criteria.DUMMY, parseColor(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Ajouter les lignes du sidebar (score des équipes + K/D du joueur, ou statut spectateur)
        addPlayerSidebarLines(board, objective, player, gm, isSpectator);
        
        return board;
    }

    /**
     * Codes couleur Minecraft utilisés comme "entrées" invisibles pour chaque ligne du sidebar.
     * Une entrée différente est nécessaire par ligne, mais elle ne doit jamais s'afficher :
     * on utilise donc une suite de codes couleur (invisibles, car ChatColor ne produit aucun
     * caractère visible) plutôt qu'un simple chiffre "0", "1", "2"... qui apparaissait à
     * l'écran à droite de chaque ligne.
     */
    private static final char[] INVISIBLE_CODES = {
        '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'
    };

    /**
     * Construit une entrée unique et invisible pour la ligne d'index donné.
     * Chaque ligne a besoin d'une entrée distincte dans le scoreboard, mais le texte de
     * cette entrée ne doit produire aucun caractère affiché.
     */
    private String invisibleEntry(int index) {
        StringBuilder sb = new StringBuilder();
        // On combine les codes pour garantir l'unicité même au-delà de 16 lignes
        sb.append(ChatColor.COLOR_CHAR).append(INVISIBLE_CODES[index % INVISIBLE_CODES.length]);
        sb.append(ChatColor.COLOR_CHAR).append(INVISIBLE_CODES[(index / INVISIBLE_CODES.length) % INVISIBLE_CODES.length]);
        sb.append(ChatColor.RESET);
        return sb.toString();
    }

    /**
     * Ajoute les lignes du sidebar.
     * Affiche toujours : le score des deux équipes et le nombre de joueurs par équipe.
     * Pour un joueur en partie, ajoute en plus son K/D personnel.
     * Pour un spectateur, remplace cette ligne par un simple rappel qu'il observe la partie
     * (il voit exactement le même score que les joueurs qu'il regarde).
     */
    private void addPlayerSidebarLines(Scoreboard board, Objective objective, Player player, GameManager gm, boolean isSpectator) {
        com.hikabrain.plugin.game.Team RED = com.hikabrain.plugin.game.Team.RED;
        com.hikabrain.plugin.game.Team BLUE = com.hikabrain.plugin.game.Team.BLUE;

        int redScore = gm.getScore(RED);
        int blueScore = gm.getScore(BLUE);
        int redPlayers = gm.getPlayerCountForTeam(RED);
        int bluePlayers = gm.getPlayerCountForTeam(BLUE);

        String lastLine;
        if (isSpectator) {
            lastLine = "&e\uD83D\uDC41 Mode spectateur";
        } else {
            // K/D personnel du joueur (kills / deaths, ou kills si aucune mort -> ratio standard)
            int kills = gm.getPlayerKills(player.getUniqueId());
            int deaths = gm.getPlayerDeaths(player.getUniqueId());
            double kd = deaths > 0 ? (double) kills / deaths : kills;
            String kdStr = String.format("%.1f", kd);
            lastLine = "&fTon K/D: &a" + kills + "&7/&c" + deaths + " &8(&a" + kdStr + "&8)";
        }

        // Lignes du sidebar : scores des deux équipes + effectifs + dernière ligne (K/D ou spectateur)
        String[] lines = {
            "&6&lHikaBrain",
            "&7" + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500,
            "&c\u2764 Rouge: &c" + redScore + "&7/&f5 &7(&c" + redPlayers + " joueur" + (redPlayers == 1 ? "" : "s") + "&7)",
            "&9\u2764 Bleu: &9" + blueScore + "&7/&f5 &7(&9" + bluePlayers + " joueur" + (bluePlayers == 1 ? "" : "s") + "&7)",
            "&7" + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500 + (char) 0x2500,
            lastLine
        };

        // Une entrée invisible distincte par ligne : le prefix de l'équipe scoreboard porte
        // tout le texte affiché, et comme l'entrée elle-même est invisible, aucun chiffre
        // parasite n'apparaît plus à droite de la ligne.
        for (int i = 0; i < lines.length; i++) {
            String parsed = parseColor(lines[i]);
            String identifier = invisibleEntry(i);

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
     * Met à jour tous les scoreboards (joueurs en partie ET spectateurs).
     */
    private void updateAllPlayerScoreboards() {
        // Supprimer les entrées qui ne correspondent plus à un joueur en partie ni à un spectateur
        playerScoreboards.keySet().removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                spectatorFlags.remove(uuid);
                return true;
            }
            GameManager gm = plugin.getArenaManager().findArenaOf(player);
            if (gm != null && gm.isPlaying(player)) {
                return false;
            }
            GameManager specGm = plugin.getArenaManager().findSpectatorArenaOf(player);
            if (specGm != null && specGm.isSpectating(player)) {
                return false;
            }
            spectatorFlags.remove(uuid);
            return true;
        });
        
        // Mettre à jour chaque scoreboard
        for (UUID uuid : new HashSet<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            
            GameManager gm = plugin.getArenaManager().findArenaOf(player);
            boolean isSpectator = false;
            if (gm == null || !gm.isPlaying(player)) {
                gm = plugin.getArenaManager().findSpectatorArenaOf(player);
                isSpectator = true;
            }
            if (gm == null) continue;
            
            Scoreboard board = playerScoreboards.get(uuid);
            updatePlayerSidebar(board, player, gm, isSpectator);
            player.setScoreboard(board);
        }
    }

    /**
     * Met à jour le sidebar pour un joueur ou un spectateur
     */
    private void updatePlayerSidebar(Scoreboard board, Player player, GameManager gm, boolean isSpectator) {
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
        addPlayerSidebarLines(board, objective, player, gm, isSpectator);
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
