package com.hikabrain.plugin.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Calcule un classement de "force" des joueurs à partir de leurs confrontations directes
 * (voir {@link HeadToHeadManager}), en tenant compte de la force des adversaires battus
 * et pas seulement du nombre brut de victoires — exactement la logique demandée :
 *
 *   "celui qui a battu le joueur qui bat tous les autres est le plus fort"
 *
 * Battre un adversaire qui gagne presque toujours vaut donc beaucoup plus que battre un
 * adversaire qui perd presque toujours, et ça se propage en cascade (transitivité) :
 * si A bat B, et que B bat C qui bat tout le monde, alors A hérite indirectement d'une
 * partie du prestige de C, via B.
 *
 * ── Algorithme ────────────────────────────────────────────────────────────────────────
 * C'est une adaptation de PageRank (l'algorithme historique de classement des pages web
 * de Google) aux résultats de matchs : au lieu de "liens entrants entre pages", on utilise
 * les "victoires infligées entre joueurs". Un joueur B "distribue" sa force aux joueurs qui
 * l'ont battu, proportionnellement au nombre de fois où chacun l'a battu par rapport à
 * l'ensemble de ses défaites. On itère ce calcul jusqu'à stabilisation : le score d'un
 * joueur finit par refléter non seulement SES victoires, mais aussi la force de tous les
 * joueurs qu'il a battus (et donc, indirectement, la force de CEUX qu'ILS ont battus, etc.).
 *
 * Un facteur d'amortissement (damping factor, 0.85 comme dans PageRank original) assure
 * que même un joueur sans aucune confrontation garde un score de base non nul, et que
 * l'algorithme converge toujours vers un résultat stable.
 */
public final class PowerRankingCalculator {

    /** Facteur d'amortissement (voir PageRank). Valeur standard : 0.85. */
    private static final double DAMPING = 0.85;

    /** Nombre d'itérations : largement suffisant pour converger sur un nombre de joueurs raisonnable. */
    private static final int ITERATIONS = 60;

    private PowerRankingCalculator() {
    }

    /**
     * Résultat du calcul pour un joueur : son score de force (plus c'est haut, plus c'est
     * fort), son nombre total de confrontations enregistrées, et sa "meilleure victoire"
     * (l'adversaire le mieux classé qu'il a battu au moins une fois), s'il y en a une.
     */
    public static class PlayerPower {
        public final UUID uuid;
        public final String name;
        public final double score;
        public final int distinctOpponents;
        public final int totalWins;
        public final int totalLosses;
        public UUID bestWinOpponent;   // peut rester null si aucune victoire enregistrée
        public String bestWinOpponentName;

        public PlayerPower(UUID uuid, String name, double score, int distinctOpponents, int totalWins, int totalLosses) {
            this.uuid = uuid;
            this.name = name;
            this.score = score;
            this.distinctOpponents = distinctOpponents;
            this.totalWins = totalWins;
            this.totalLosses = totalLosses;
        }
    }

    /**
     * Calcule et renvoie le classement complet, du plus fort au plus faible.
     */
    public static List<PlayerPower> compute(HeadToHeadManager manager) {
        Map<UUID, HeadToHeadManager.PlayerHeadToHead> all = manager.getAll();
        Set<UUID> players = all.keySet();
        int n = players.size();

        if (n == 0) return new ArrayList<>();

        // ── Score initial : réparti équitablement (somme = 1, comme PageRank) ──
        Map<UUID, Double> score = new HashMap<>();
        for (UUID p : players) score.put(p, 1.0 / n);

        // ── "Poids sortant" de chaque joueur = son nombre TOTAL de défaites, toutes
        // confondues (c'est par rapport à CE total que sa force se répartit entre ceux
        // qui l'ont battu, proportionnellement au nombre de fois où chacun l'a battu). ──
        Map<UUID, Integer> totalLossesOf = new HashMap<>();
        for (UUID p : players) {
            int totalLosses = 0;
            for (HeadToHeadManager.Record r : all.get(p).opponents.values()) totalLosses += r.losses;
            totalLossesOf.put(p, totalLosses);
        }

        for (int iter = 0; iter < ITERATIONS; iter++) {
            Map<UUID, Double> next = new HashMap<>();
            double base = (1 - DAMPING) / n;
            for (UUID p : players) next.put(p, base);

            // Un joueur sans aucune défaite enregistrée ("dangling node" en langage PageRank)
            // ne peut distribuer sa force à personne par la formule normale : on redistribue
            // sa masse uniformément entre tout le monde plutôt que de la perdre (sinon le
            // score total fuirait au fil des itérations et fausserait le classement relatif).
            double danglingMass = 0.0;

            for (UUID loser : players) {
                int totalLosses = totalLossesOf.get(loser);
                double loserScore = score.get(loser);

                if (totalLosses == 0) {
                    danglingMass += loserScore;
                    continue;
                }

                for (Map.Entry<UUID, HeadToHeadManager.Record> entry : all.get(loser).opponents.entrySet()) {
                    UUID winner = entry.getKey();
                    int lossesAgainstThisWinner = entry.getValue().losses; // = nb de fois où "winner" a battu "loser"
                    if (lossesAgainstThisWinner == 0 || !players.contains(winner)) continue;

                    double contribution = loserScore * ((double) lossesAgainstThisWinner / totalLosses);
                    next.merge(winner, DAMPING * contribution, Double::sum);
                }
            }

            if (danglingMass > 0) {
                double share = DAMPING * danglingMass / n;
                for (UUID p : players) next.merge(p, share, Double::sum);
            }

            score = next;
        }

        // ── Construction du résultat final, avec la "meilleure victoire" de chacun ──
        List<PlayerPower> results = new ArrayList<>();
        for (UUID uuid : players) {
            HeadToHeadManager.PlayerHeadToHead phh = all.get(uuid);
            int wins = 0, losses = 0;
            UUID bestOpponent = null;
            double bestOpponentScore = -1;

            for (Map.Entry<UUID, HeadToHeadManager.Record> entry : phh.opponents.entrySet()) {
                HeadToHeadManager.Record r = entry.getValue();
                wins += r.wins;
                losses += r.losses;
                if (r.wins > 0) {
                    double oppScore = score.getOrDefault(entry.getKey(), 0.0);
                    if (oppScore > bestOpponentScore) {
                        bestOpponentScore = oppScore;
                        bestOpponent = entry.getKey();
                    }
                }
            }

            PlayerPower power = new PlayerPower(uuid, phh.name, score.getOrDefault(uuid, 0.0),
                    phh.opponents.size(), wins, losses);
            if (bestOpponent != null) {
                power.bestWinOpponent = bestOpponent;
                HeadToHeadManager.PlayerHeadToHead bestOppData = all.get(bestOpponent);
                power.bestWinOpponentName = bestOppData != null ? bestOppData.name : "?";
            }
            results.add(power);
        }

        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results;
    }

    /**
     * Calcule uniquement le rang (1-based) et le score d'un joueur précis, ou renvoie
     * null s'il n'a aucune confrontation enregistrée. Pratique pour /hb force <joueur>
     * sans avoir à parcourir le classement entier soi-même côté appelant.
     */
    public static PlayerPowerWithRank computeForPlayer(HeadToHeadManager manager, UUID uuid) {
        List<PlayerPower> ranking = compute(manager);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).uuid.equals(uuid)) {
                return new PlayerPowerWithRank(ranking.get(i), i + 1, ranking.size());
            }
        }
        return null;
    }

    public static class PlayerPowerWithRank {
        public final PlayerPower power;
        public final int rank;
        public final int totalRanked;

        public PlayerPowerWithRank(PlayerPower power, int rank, int totalRanked) {
            this.power = power;
            this.rank = rank;
            this.totalRanked = totalRanked;
        }
    }
}
