package com.hikabrain.plugin.tournament;

/**
 * États du cycle de vie d'un tournoi.
 */
public enum TournamentState {
    /** Inscriptions ouvertes, en attente du nombre de places ou d'un /tournament start. */
    REGISTRATION,
    /** Bracket généré, matchs en cours de déroulement. */
    IN_PROGRESS,
    /** Tournoi terminé, un champion a été désigné. */
    FINISHED,
    /** Tournoi annulé par un admin avant la fin. */
    CANCELLED
}
