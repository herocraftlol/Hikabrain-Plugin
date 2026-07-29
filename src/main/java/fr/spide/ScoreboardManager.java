package fr.spide;

import fr.spide.model.SpideMap;
import fr.spide.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gère un tableau de score (sidebar) par map en cours de partie :
 * une ligne par équipe avec le nombre de joueurs vivants et le nombre de points.
 * Le scoreboard est retiré (retour au scoreboard principal du serveur) en fin de partie.
 */
public class ScoreboardManager {

    private final Map<String, Scoreboard> boards = new HashMap<>();

    public void createAndAssign(SpideMap map) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("spide", "dummy", "§6§lSPIDE");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(map.getName(), board);
        refresh(map);
        assignToAll(map, board);
    }

    public void refresh(SpideMap map) {
        Scoreboard board = boards.get(map.getName());
        if (board == null) return;
        Objective obj = board.getObjective("spide");
        if (obj == null) return;

        // On efface les anciennes lignes avant de reconstruire (les entrées ne sont pas éditables).
        for (String entry : new java.util.ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }

        int line = map.getTeams().size();
        for (Team t : map.getTeams()) {
            String text = "§f" + t.getColor() + " §7- §a" + t.getAlive().size() + "/" + t.getMembers().size()
                    + " vivants §7- §e" + t.getScore() + " pts";
            if (text.length() > 40) {
                text = text.substring(0, 40);
            }
            obj.getScore(text).setScore(line);
            line--;
        }
        obj.getScore("§7Objectif: §f" + map.getPointsToWin() + " pts").setScore(0);
    }

    public void assignToAll(SpideMap map, Scoreboard board) {
        for (Team t : map.getTeams()) {
            for (UUID uuid : t.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.setScoreboard(board);
            }
        }
        for (UUID uuid : map.getSpectators()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setScoreboard(board);
        }
    }

    public void assignPlayer(SpideMap map, Player player) {
        Scoreboard board = boards.get(map.getName());
        if (board != null) {
            player.setScoreboard(board);
        }
    }

    public void remove(SpideMap map) {
        Scoreboard board = boards.remove(map.getName());
        if (board == null) return;
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();

        for (Team t : map.getTeams()) {
            for (UUID uuid : t.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.setScoreboard(main);
            }
        }
        for (UUID uuid : map.getSpectators()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setScoreboard(main);
        }
    }
}
