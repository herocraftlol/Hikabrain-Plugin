package com.spaceship.plugin.stats;

import com.spaceship.plugin.game.Team;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Fiche archive" d'une partie SpaceShip terminée : de quoi la rejouer dans sa tête des
 * années plus tard (les joueurs présents, la date, l'équipe gagnante, le nombre de kills
 * et de points marqués) et surtout combien de temps elle a duré.
 *
 * Utilisé par {@link GameHistoryManager} pour alimenter le classement des parties les
 * plus longues (hologramme + commande /ss longestgames).
 */
public class GameRecord {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH).withZone(ZoneId.systemDefault());

    private final String arenaName;
    private final long durationSeconds;
    private final long timestampMillis;
    private final Team winner;
    private final List<String> blackPlayers;
    private final List<String> whitePlayers;
    private final int totalKills;
    private final int totalPoints;

    public GameRecord(String arenaName, long durationSeconds, long timestampMillis, Team winner,
                       List<String> blackPlayers, List<String> whitePlayers, int totalKills, int totalPoints) {
        this.arenaName = arenaName;
        this.durationSeconds = durationSeconds;
        this.timestampMillis = timestampMillis;
        this.winner = winner;
        this.blackPlayers = new ArrayList<>(blackPlayers);
        this.whitePlayers = new ArrayList<>(whitePlayers);
        this.totalKills = totalKills;
        this.totalPoints = totalPoints;
    }

    public String getArenaName() { return arenaName; }
    public long getDurationSeconds() { return durationSeconds; }
    public long getTimestampMillis() { return timestampMillis; }
    public Team getWinner() { return winner; }
    public List<String> getBlackPlayers() { return blackPlayers; }
    public List<String> getWhitePlayers() { return whitePlayers; }
    public int getTotalKills() { return totalKills; }
    public int getTotalPoints() { return totalPoints; }

    /** Tous les joueurs de la partie (noirs puis blancs), pour affichage compact. */
    public List<String> getAllPlayers() {
        List<String> all = new ArrayList<>(blackPlayers.size() + whitePlayers.size());
        all.addAll(blackPlayers);
        all.addAll(whitePlayers);
        return all;
    }

    /** Date lisible (dd/MM/yyyy HH:mm), fuseau horaire du serveur. */
    public String getFormattedDate() {
        return DATE_FORMAT.format(Instant.ofEpochMilli(timestampMillis));
    }

    /** Durée lisible, format "12m34s" (ou "1h05m12s" au-delà d'une heure). */
    public String getFormattedDuration() {
        long h = durationSeconds / 3600;
        long m = (durationSeconds % 3600) / 60;
        long s = durationSeconds % 60;
        if (h > 0) {
            return String.format(Locale.FRENCH, "%dh%02dm%02ds", h, m, s);
        }
        return String.format(Locale.FRENCH, "%dm%02ds", m, s);
    }
}
