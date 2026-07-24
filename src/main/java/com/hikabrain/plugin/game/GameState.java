package com.hikabrain.plugin.game;

/**
 * États possibles du cycle de vie d'une partie HikaBrain.
 */
public enum GameState {
    /** Le plugin attend que la map soit configurée (points manquants). */
    NOT_CONFIGURED,
    /** En attente de joueurs dans le lobby. */
    WAITING,
    /** Compte à rebours en cours avant le début de la partie (joueurs encore au lobby). */
    COUNTDOWN,
    /**
     * Joueurs déjà téléportés dans l'arène, gelés, en attente de la fin du compte à
     * rebours de démarrage (même système que ROUND_RESET : ne peuvent pas bouger).
     */
    STARTING,
    /** Partie en cours, les joueurs peuvent capturer la zone adverse. */
    PLAYING,
    /** Un point vient d'être marqué : compte à rebours avant le round suivant, capture désactivée. */
    ROUND_RESET,
    /** Partie terminée, affichage des résultats avant reset. */
    ENDING
}
