package com.hikabrain.plugin.tournament;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un match du bracket : un groupe de compétiteurs ("slots") qui s'affrontent
 * pour désigner un ou plusieurs qualifiés pour le tour suivant.
 *
 * - 1v1 / 2v2 / Faction / HikaBrain : exactement 2 slots, 1 qualifié.
 * - FFA : 2 slots ou plus (selon la config), 1 qualifié (le survivant / meilleur score).
 */
public class BracketMatch {

    private final int round;
    private final int indexInRound;
    private final List<TournamentTeam> slots;
    private final int qualifiersCount;

    private MatchStatus status = MatchStatus.WAITING_FOR_TEAMS;
    private final List<TournamentTeam> qualified = new ArrayList<>();

    /** Nom de l'arène assignée (DuelArena ou arène HikaBrain selon le format). */
    private String arenaName;

    /** Manches gagnées par slot, pour le BO3/BO5 (index = index du slot dans "slots"). */
    private final Map<Integer, Integer> roundsWon = new HashMap<>();

    /** Kills comptabilisés pendant le match en cours, par joueur (utilisé pour FFA / record de kills / MVP). */
    private final Map<UUID, Integer> liveKills = new HashMap<>();

    private long startedAt;
    private long finishedAt;

    public BracketMatch(int round, int indexInRound, List<TournamentTeam> slots, int qualifiersCount) {
        this.round = round;
        this.indexInRound = indexInRound;
        this.slots = slots;
        this.qualifiersCount = qualifiersCount;
    }

    public int getRound() {
        return round;
    }

    public int getIndexInRound() {
        return indexInRound;
    }

    public List<TournamentTeam> getSlots() {
        return slots;
    }

    public int getQualifiersCount() {
        return qualifiersCount;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public List<TournamentTeam> getQualified() {
        return qualified;
    }

    public String getArenaName() {
        return arenaName;
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName;
    }

    public int getRoundsWon(int slotIndex) {
        return roundsWon.getOrDefault(slotIndex, 0);
    }

    public void addRoundWin(int slotIndex) {
        roundsWon.put(slotIndex, getRoundsWon(slotIndex) + 1);
    }

    public Map<UUID, Integer> getLiveKills() {
        return liveKills;
    }

    public void addLiveKill(UUID uuid) {
        liveKills.put(uuid, liveKills.getOrDefault(uuid, 0) + 1);
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

    /** Nombre de compétiteurs réellement présents (hors slots vides = "BYE"). */
    public long getPresentSlotCount() {
        return slots.stream().filter(t -> t != null).count();
    }

    public boolean isBye() {
        return getPresentSlotCount() <= 1;
    }

    /** Renvoie l'unique compétiteur présent si c'est un BYE, sinon null. */
    public TournamentTeam getSoleTeam() {
        for (TournamentTeam t : slots) {
            if (t != null) return t;
        }
        return null;
    }

    /** true si tous les slots de ce match sont désormais connus (aucun "en attente du match précédent"). */
    public boolean allSlotsResolved() {
        // Un slot "null" représente une place encore inconnue (le match précédent qui l'alimente
        // n'est pas terminé). Un slot représentant un "BYE" définitif est simplement absent de la liste
        // au moment de la génération, donc ici on considère juste que la liste ne doit plus grossir.
        return slots.stream().noneMatch(t -> t == PLACEHOLDER_UNRESOLVED);
    }

    /** Marqueur spécial utilisé pour un slot pas encore déterminé (dépend du vainqueur d'un match précédent). */
    public static final TournamentTeam PLACEHOLDER_UNRESOLVED = null;

    public String getDisplayVersus() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) sb.append(" vs ");
            TournamentTeam t = slots.get(i);
            sb.append(t == null ? "?" : t.getDisplayName());
        }
        return sb.toString();
    }
}
