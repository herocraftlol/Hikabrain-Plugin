package com.hikabrain.plugin.tournament;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Représente un compétiteur inscrit à un tournoi : selon le format, ça peut être
 * un joueur seul (1v1, FFA), un duo (2v2) ou un groupe (Faction vs Faction).
 *
 * Le nom d'affichage est soit celui du premier joueur (1v1/FFA), soit un tag
 * choisi par le capitaine (2v2/Faction), ex: "&6Les Invincibles".
 */
public class TournamentTeam {

    private final String id;
    private String displayName;
    private final UUID captain;
    private final Set<UUID> members = new LinkedHashSet<>();

    /** Membres actuellement déconnectés (période de grâce avant forfait). */
    private final Set<UUID> disconnected = new LinkedHashSet<>();

    /** Kills cumulés sur l'ensemble du tournoi (utilisé pour les stats/records). */
    private int totalKills = 0;

    /** true si cette équipe a été éliminée du tournoi. */
    private boolean eliminated = false;

    /** Nombre de manches/matchs remportés dans le tournoi (pour le classement "nombre de victoires"). */
    private int matchesWon = 0;

    public TournamentTeam(String id, String displayName, UUID captain) {
        this.id = id;
        this.displayName = displayName;
        this.captain = captain;
        this.members.add(captain);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UUID getCaptain() {
        return captain;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean addMember(UUID uuid) {
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        disconnected.remove(uuid);
        return members.remove(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return members.contains(uuid);
    }

    public int size() {
        return members.size();
    }

    public Set<UUID> getDisconnected() {
        return disconnected;
    }

    public void markDisconnected(UUID uuid) {
        disconnected.add(uuid);
    }

    public void markReconnected(UUID uuid) {
        disconnected.remove(uuid);
    }

    /** true si TOUS les membres encore inscrits sont actuellement déconnectés (forfait imminent). */
    public boolean isFullyDisconnected() {
        return !members.isEmpty() && disconnected.containsAll(members);
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void addKills(int amount) {
        this.totalKills += amount;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public int getMatchesWon() {
        return matchesWon;
    }

    public void incrementMatchesWon() {
        this.matchesWon++;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TournamentTeam)) return false;
        return id.equals(((TournamentTeam) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public static TournamentTeam soloOf(UUID player, String name) {
        return new TournamentTeam(UUID.randomUUID().toString(), name, player);
    }
}
