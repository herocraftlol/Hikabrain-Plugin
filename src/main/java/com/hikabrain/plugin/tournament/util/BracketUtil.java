package com.hikabrain.plugin.tournament.util;

import com.hikabrain.plugin.tournament.BracketMatch;
import com.hikabrain.plugin.tournament.TournamentTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilitaires de génération de bracket à élimination directe.
 *
 * Le nombre d'inscrits n'a pas besoin d'être une puissance de deux : les places
 * manquantes sont comblées par des "BYE" (qualification automatique au tour 1)
 * pour toujours retomber sur une puissance de deux au tour suivant.
 */
public final class BracketUtil {

    private BracketUtil() {
    }

    /** Prochaine puissance de deux supérieure ou égale à n (minimum 2). */
    public static int nextPowerOfTwo(int n) {
        int power = 2;
        while (power < n) {
            power *= 2;
        }
        return power;
    }

    /**
     * Génère le premier tour du bracket : mélange les équipes puis les répartit en
     * groupes de "slotsPerMatch" (2 pour la plupart des formats, potentiellement plus en FFA).
     * Les places manquantes pour atteindre une puissance de deux sont des BYE (null).
     */
    public static List<BracketMatch> generateFirstRound(List<TournamentTeam> registered, int slotsPerMatch, int qualifiersPerMatch) {
        List<TournamentTeam> shuffled = new ArrayList<>(registered);
        Collections.shuffle(shuffled);

        int bracketSize = nextPowerOfTwo(shuffled.size());
        // Compléter avec des BYE (null) jusqu'à la taille du bracket
        while (shuffled.size() < bracketSize) {
            shuffled.add(null);
        }

        List<BracketMatch> matches = new ArrayList<>();
        int matchCount = bracketSize / slotsPerMatch;
        int index = 0;
        for (int m = 0; m < matchCount; m++) {
            List<TournamentTeam> slots = new ArrayList<>();
            for (int s = 0; s < slotsPerMatch; s++) {
                slots.add(index < shuffled.size() ? shuffled.get(index) : null);
                index++;
            }
            matches.add(new BracketMatch(1, m, slots, qualifiersPerMatch));
        }
        return matches;
    }

    /**
     * Génère le tour suivant à partir des qualifiés du tour précédent, en regroupant
     * les vainqueurs par paquets de "slotsPerMatch".
     */
    public static List<BracketMatch> generateNextRound(List<TournamentTeam> qualifiedFromPreviousRound, int round, int slotsPerMatch, int qualifiersPerMatch) {
        List<BracketMatch> matches = new ArrayList<>();
        int matchCount = Math.max(1, qualifiedFromPreviousRound.size() / slotsPerMatch);
        int index = 0;
        for (int m = 0; m < matchCount; m++) {
            List<TournamentTeam> slots = new ArrayList<>();
            for (int s = 0; s < slotsPerMatch; s++) {
                slots.add(index < qualifiedFromPreviousRound.size() ? qualifiedFromPreviousRound.get(index) : null);
                index++;
            }
            matches.add(new BracketMatch(round, m, slots, qualifiersPerMatch));
        }
        return matches;
    }

    /**
     * Nom d'affichage d'un tour en fonction du nombre de tours restants
     * (ex: "Finale", "Demi-finales", "Quarts de finale", "Huitièmes de finale").
     */
    public static String roundName(int matchesInRound, boolean isLastRound) {
        if (isLastRound || matchesInRound == 1) return "Finale";
        if (matchesInRound == 2) return "Demi-finales";
        if (matchesInRound == 4) return "Quarts de finale";
        if (matchesInRound == 8) return "Huitièmes de finale";
        if (matchesInRound == 16) return "Seizièmes de finale";
        return "Tour à " + matchesInRound + " matchs";
    }
}
