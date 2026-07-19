package com.hikabrain.plugin.tournament;

/**
 * Formats de tournoi supportés.
 *
 * - ONE_VS_ONE / TWO_VS_TWO / FACTION : duels par élimination directe joués sur une
 *   arène de duel dédiée (voir {@link DuelArena}), résolus au nombre de kills
 *   (BO3/BO5, premier à X points, ou meilleur score au temps limite).
 * - FFA : plusieurs joueurs (par défaut : tous les inscrits restants d'un groupe)
 *   s'affrontent en même temps sur l'arène de duel ; un nombre configurable de
 *   qualifiés se répartissent au tour suivant.
 * - HIKABRAIN : chaque match du tournoi est une vraie partie HikaBrain, jouée sur
 *   une arène HikaBrain existante (capture de zone), pilotée par le GameManager
 *   habituel du plugin.
 */
public enum TournamentFormat {

    ONE_VS_ONE("1v1", 1, 2, false),
    TWO_VS_TWO("2v2", 2, 2, false),
    FFA("FFA", 1, 4, true),
    FACTION_VS_FACTION("Faction vs Faction", 3, 2, false),
    HIKABRAIN("HikaBrain", 1, 2, false);

    private final String label;
    private final int defaultTeamSize;
    private final int slotsPerMatch;
    private final boolean freeForAll;

    TournamentFormat(String label, int defaultTeamSize, int slotsPerMatch, boolean freeForAll) {
        this.label = label;
        this.defaultTeamSize = defaultTeamSize;
        this.slotsPerMatch = slotsPerMatch;
        this.freeForAll = freeForAll;
    }

    public String getLabel() {
        return label;
    }

    /** Taille par défaut d'une équipe inscrite pour ce format (1 pour 1v1/FFA, 2 pour 2v2, etc.). */
    public int getDefaultTeamSize() {
        return defaultTeamSize;
    }

    /** Nombre de compétiteurs (équipes) qui s'affrontent en même temps dans un match de ce format. */
    public int getSlotsPerMatch() {
        return slotsPerMatch;
    }

    /** true si ce format regroupe plusieurs équipes dans un seul match "à la mort" (FFA). */
    public boolean isFreeForAll() {
        return freeForAll;
    }

    /** true si ce format utilise le moteur de jeu HikaBrain existant (capture de zone). */
    public boolean isHikaBrainEngine() {
        return this == HIKABRAIN;
    }

    public static TournamentFormat fromString(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        switch (s) {
            case "1V1":
            case "1VS1":
                return ONE_VS_ONE;
            case "2V2":
            case "2VS2":
                return TWO_VS_TWO;
            case "FFA":
            case "FREEFORALL":
                return FFA;
            case "FACTION":
            case "FACTIONS":
            case "FACTION_VS_FACTION":
            case "FVF":
                return FACTION_VS_FACTION;
            case "HIKABRAIN":
            case "HB":
                return HIKABRAIN;
            default:
                return null;
        }
    }
}
