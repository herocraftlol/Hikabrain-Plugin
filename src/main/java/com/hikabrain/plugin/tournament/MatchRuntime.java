package com.hikabrain.plugin.tournament;

import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * État "en direct" d'un match de tournoi en cours qui utilise le moteur de duel
 * interne (1v1 / 2v2 / FFA / Faction), par opposition aux matchs HikaBrain qui
 * sont entièrement pilotés par le GameManager existant.
 *
 * Un match peut comporter plusieurs manches (BO3/BO5) : entre chaque manche, tout
 * le monde revit, sauf en mode "course aux points" (bestOf == 1 + pointsToWin > 0)
 * où les joueurs respawnent directement dans la même manche jusqu'à ce qu'un
 * camp atteigne le nombre de points requis.
 */
public class MatchRuntime {

    public enum Mode {
        /** Manches à élimination (BO3/BO5) : mort = spectateur jusqu'à la fin de la manche. */
        ELIMINATION_ROUNDS,
        /** Course au score : les joueurs respawnent, premier camp à X kills gagne. */
        POINTS_RACE
    }

    private final Tournament tournament;
    private final BracketMatch match;
    private final DuelArena arena;
    private final Mode mode;

    /** Joueurs actuellement vivants dans la manche en cours, par index de slot. */
    private final Map<Integer, Set<UUID>> aliveBySlot = new HashMap<>();

    /** Points/kills cumulés sur l'ensemble du MATCH (toutes manches confondues), par index de slot. */
    private final Map<Integer, Integer> slotScore = new HashMap<>();

    private BukkitTask timeLimitTask;
    private BukkitTask actionBarTask;
    private final Map<UUID, BukkitTask> disconnectGraceTasks = new HashMap<>();

    private long currentRoundStartedAt;

    public MatchRuntime(Tournament tournament, BracketMatch match, DuelArena arena, Mode mode) {
        this.tournament = tournament;
        this.match = match;
        this.arena = arena;
        this.mode = mode;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public BracketMatch getMatch() {
        return match;
    }

    public DuelArena getArena() {
        return arena;
    }

    public Mode getMode() {
        return mode;
    }

    public Map<Integer, Set<UUID>> getAliveBySlot() {
        return aliveBySlot;
    }

    public Set<UUID> getAlive(int slotIndex) {
        return aliveBySlot.computeIfAbsent(slotIndex, k -> new HashSet<>());
    }

    public int getSlotScore(int slotIndex) {
        return slotScore.getOrDefault(slotIndex, 0);
    }

    public void addSlotScore(int slotIndex, int amount) {
        slotScore.put(slotIndex, getSlotScore(slotIndex) + amount);
    }

    public BukkitTask getTimeLimitTask() {
        return timeLimitTask;
    }

    public void setTimeLimitTask(BukkitTask timeLimitTask) {
        this.timeLimitTask = timeLimitTask;
    }

    public BukkitTask getActionBarTask() {
        return actionBarTask;
    }

    public void setActionBarTask(BukkitTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }

    public Map<UUID, BukkitTask> getDisconnectGraceTasks() {
        return disconnectGraceTasks;
    }

    public long getCurrentRoundStartedAt() {
        return currentRoundStartedAt;
    }

    public void setCurrentRoundStartedAt(long currentRoundStartedAt) {
        this.currentRoundStartedAt = currentRoundStartedAt;
    }

    /** Renvoie l'index de slot du joueur donné, ou -1 s'il n'est dans aucun slot de ce match. */
    public int slotIndexOf(UUID uuid) {
        for (int i = 0; i < match.getSlots().size(); i++) {
            TournamentTeam team = match.getSlots().get(i);
            if (team != null && team.hasMember(uuid)) return i;
        }
        return -1;
    }

    public void cancelAllTasks() {
        if (timeLimitTask != null) timeLimitTask.cancel();
        if (actionBarTask != null) actionBarTask.cancel();
        for (BukkitTask t : disconnectGraceTasks.values()) {
            if (t != null) t.cancel();
        }
        disconnectGraceTasks.clear();
    }
}
