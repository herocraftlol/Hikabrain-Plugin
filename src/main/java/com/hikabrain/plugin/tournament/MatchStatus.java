package com.hikabrain.plugin.tournament;

/**
 * États d'un match individuel dans le bracket.
 */
public enum MatchStatus {
    /** Un ou plusieurs adversaires ne sont pas encore connus (dépendent d'un match précédent). */
    WAITING_FOR_TEAMS,
    /** Les deux/N adversaires sont connus, le match attend d'être lancé (arène/joueurs). */
    PENDING,
    /** Joueurs téléportés, compte à rebours ou combat en cours. */
    ONGOING,
    /** Match terminé, vainqueur(s) désigné(s). */
    FINISHED,
    /** Match sans objet (un "BYE" : un seul adversaire présent, qualifié automatiquement). */
    BYE
}
