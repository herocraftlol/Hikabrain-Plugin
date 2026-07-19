package com.hikabrain.plugin.tournament;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Représente un tournoi : sa configuration (format, règles), ses inscrits, son
 * bracket (liste de tours, chaque tour étant une liste de {@link BracketMatch}),
 * et son résultat final.
 */
public class Tournament {

    private final String name;
    private final TournamentFormat format;
    private final int teamSize;
    private final int maxSlots;
    private final int bestOf;
    private final int pointsToWin;
    private final int timeLimitSeconds;
    private final List<String> rules;
    private final UUID creator;
    /** Nom de l'arène HikaBrain à utiliser si format == HIKABRAIN (facultatif : sinon une libre est choisie). */
    private final String linkedHikaBrainArena;

    private TournamentState state = TournamentState.REGISTRATION;
    private final List<TournamentTeam> registered = new ArrayList<>();
    private final List<List<BracketMatch>> rounds = new ArrayList<>();
    private int currentRoundIndex = 0;

    private TournamentTeam champion;
    private TournamentTeam runnerUp;
    private final List<TournamentTeam> thirdPlace = new ArrayList<>();

    private final Set<UUID> spectators = new LinkedHashSet<>();

    private long createdAt = System.currentTimeMillis();
    private long startedAt;
    private long finishedAt;

    public Tournament(String name, TournamentFormat format, int teamSize, int maxSlots, int bestOf,
                       int pointsToWin, int timeLimitSeconds, List<String> rules, UUID creator,
                       String linkedHikaBrainArena) {
        this.name = name;
        this.format = format;
        this.teamSize = teamSize;
        this.maxSlots = maxSlots;
        this.bestOf = bestOf;
        this.pointsToWin = pointsToWin;
        this.timeLimitSeconds = timeLimitSeconds;
        this.rules = rules;
        this.creator = creator;
        this.linkedHikaBrainArena = linkedHikaBrainArena;
    }

    public String getName() {
        return name;
    }

    public TournamentFormat getFormat() {
        return format;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public int getBestOf() {
        return bestOf;
    }

    /** Nombre de manches à remporter pour gagner un match (BO3 -> 2, BO5 -> 3). */
    public int getRoundsNeededToWinMatch() {
        return (bestOf / 2) + 1;
    }

    public int getPointsToWin() {
        return pointsToWin;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public List<String> getRules() {
        return rules;
    }

    public UUID getCreator() {
        return creator;
    }

    public String getLinkedHikaBrainArena() {
        return linkedHikaBrainArena;
    }

    public TournamentState getState() {
        return state;
    }

    public void setState(TournamentState state) {
        this.state = state;
    }

    public List<TournamentTeam> getRegistered() {
        return registered;
    }

    public boolean isFull() {
        return registered.size() >= maxSlots;
    }

    public int getRegisteredPlayerCount() {
        return registered.stream().mapToInt(TournamentTeam::size).sum();
    }

    public List<List<BracketMatch>> getRounds() {
        return rounds;
    }

    public int getCurrentRoundIndex() {
        return currentRoundIndex;
    }

    public void setCurrentRoundIndex(int currentRoundIndex) {
        this.currentRoundIndex = currentRoundIndex;
    }

    public List<BracketMatch> getCurrentRound() {
        if (currentRoundIndex < 0 || currentRoundIndex >= rounds.size()) return null;
        return rounds.get(currentRoundIndex);
    }

    public TournamentTeam getChampion() {
        return champion;
    }

    public void setChampion(TournamentTeam champion) {
        this.champion = champion;
    }

    public TournamentTeam getRunnerUp() {
        return runnerUp;
    }

    public void setRunnerUp(TournamentTeam runnerUp) {
        this.runnerUp = runnerUp;
    }

    public List<TournamentTeam> getThirdPlace() {
        return thirdPlace;
    }

    public Set<UUID> getSpectators() {
        return spectators;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    /** Recherche l'équipe d'un joueur, quel que soit le tour (utile pour /tournament leave, déconnexion, etc.). */
    public TournamentTeam findTeamOf(UUID uuid) {
        for (TournamentTeam team : registered) {
            if (team.hasMember(uuid)) return team;
        }
        return null;
    }

    /** Recherche le match en cours impliquant ce joueur (tour courant uniquement). */
    public BracketMatch findOngoingMatchOf(UUID uuid) {
        List<BracketMatch> current = getCurrentRound();
        if (current == null) return null;
        TournamentTeam team = findTeamOf(uuid);
        if (team == null) return null;
        for (BracketMatch match : current) {
            if (match.getSlots().contains(team) && match.getStatus() == MatchStatus.ONGOING) {
                return match;
            }
        }
        return null;
    }

    public boolean isPlayerRegistered(UUID uuid) {
        return findTeamOf(uuid) != null;
    }
}
