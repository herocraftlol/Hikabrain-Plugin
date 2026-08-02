package com.hikabrain.plugin.stats;

import com.hikabrain.plugin.HikaBrainPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Journal historique, JOUR PAR JOUR, des résultats de chaque partie HikaBrain, pour
 * pouvoir calculer des classements sur une PLAGE DE TEMPS précise (aujourd'hui, cette
 * semaine, ou une plage de dates choisie) — contrairement à {@link StatsManager} et
 * {@link HeadToHeadManager} qui ne stockent que des totaux cumulés "depuis toujours".
 *
 * Deux journaux distincts, un fichier par jour dans chacun (ex: "2026-08-01.log") pour
 * qu'une requête "aujourd'hui" ou "cette semaine" n'ait jamais besoin de relire tout
 * l'historique complet du serveur, seulement les quelques fichiers concernés :
 *
 *  - history/<date>.log     : une ligne par JOUEUR par PARTIE (coups, kills, morts,
 *                              buts, victoire, points gagnés) → utilisé pour les
 *                              classements "coups", "kills", "buts", "victoires", "points"...
 *  - h2h-history/<date>.log : une ligne par CONFRONTATION (qui a battu qui) → utilisé
 *                              pour recalculer un classement de force (voir
 *                              {@link PowerRankingCalculator}) limité à une période.
 *
 * Format volontairement simple (valeurs séparées par '|', pas de JSON) : les pseudos
 * Minecraft ne peuvent pas contenir '|', donc aucun risque d'échappement à gérer, et
 * l'écriture/lecture reste rapide même avec beaucoup de parties.
 */
public class MatchHistoryManager {

    /** Résultat agrégé d'un joueur sur une plage de temps donnée. */
    public static class AggregatedStats {
        public String name;
        public int hits;
        public int hitsReceived;
        public int kills;
        public int deaths;
        public int goals;
        public int wins;
        public int gamesPlayed;
        public int pointsGained;

        public double getKD() {
            if (deaths == 0) return kills > 0 ? kills : 0.0;
            return Math.round((double) kills / deaths * 100.0) / 100.0;
        }
    }

    private final HikaBrainPlugin plugin;
    private final File matchHistoryDir;
    private final File h2hHistoryDir;

    public MatchHistoryManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.matchHistoryDir = new File(plugin.getDataFolder(), "history");
        this.h2hHistoryDir = new File(plugin.getDataFolder(), "h2h-history");
        matchHistoryDir.mkdirs();
        h2hHistoryDir.mkdirs();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    // ── Enregistrement ────────────────────────────────────────────────────────

    /**
     * Enregistre le résultat d'UN joueur pour LA partie qui vient de se terminer.
     * Appelé une fois par joueur participant, à la fin de chaque partie (voir
     * GameManager#announceEndGameSummary).
     */
    public void recordPlayerMatch(UUID uuid, String name, int hits, int hitsReceived, int kills, int deaths, int goals, boolean won, int pointsGained) {
        String line = System.currentTimeMillis() + "|" + uuid + "|" + name + "|" + hits + "|" + hitsReceived + "|" + kills + "|" + deaths + "|" + goals + "|" + won + "|" + pointsGained;
        appendLine(fileFor(matchHistoryDir, today()), line);
    }

    /**
     * Enregistre une confrontation directe (winner a battu loser) pour LA partie qui
     * vient de se terminer. Appelé une fois par paire gagnant/perdant (voir
     * GameManager#recordHeadToHeadResults).
     */
    public void recordHeadToHead(UUID winnerUuid, String winnerName, UUID loserUuid, String loserName) {
        String line = System.currentTimeMillis() + "|" + winnerUuid + "|" + winnerName + "|" + loserUuid + "|" + loserName;
        appendLine(fileFor(h2hHistoryDir, today()), line);
    }

    private File fileFor(File dir, LocalDate date) {
        return new File(dir, date.toString() + ".log"); // ex: "2026-08-01.log"
    }

    private void appendLine(File file, String line) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(line);
            fw.write(System.lineSeparator());
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'écrire dans l'historique HikaBrain (" + file.getName() + ") : " + e.getMessage());
        }
    }

    // ── Lecture / agrégation ─────────────────────────────────────────────────

    /** Période prédéfinie pour les classements temporels. */
    public enum Period {
        ALL_TIME, TODAY, WEEK, CUSTOM
    }

    /**
     * Agrège les stats de match de chaque joueur sur la plage de dates [from, to] incluse.
     * Les journaux inexistants (aucune partie ce jour-là) sont simplement ignorés.
     */
    public Map<UUID, AggregatedStats> aggregatePlayerStats(LocalDate from, LocalDate to) {
        Map<UUID, AggregatedStats> result = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            File file = fileFor(matchHistoryDir, d);
            if (!file.exists()) continue;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 10) continue; // ligne corrompue/tronquée, on l'ignore

                    try {
                        UUID uuid = UUID.fromString(parts[1]);
                        String name = parts[2];
                        int hits = Integer.parseInt(parts[3]);
                        int hitsReceived = Integer.parseInt(parts[4]);
                        int kills = Integer.parseInt(parts[5]);
                        int deaths = Integer.parseInt(parts[6]);
                        int goals = Integer.parseInt(parts[7]);
                        boolean won = Boolean.parseBoolean(parts[8]);
                        int points = Integer.parseInt(parts[9]);

                        AggregatedStats stats = result.computeIfAbsent(uuid, k -> new AggregatedStats());
                        stats.name = name;
                        stats.hits += hits;
                        stats.hitsReceived += hitsReceived;
                        stats.kills += kills;
                        stats.deaths += deaths;
                        stats.goals += goals;
                        if (won) stats.wins++;
                        stats.gamesPlayed++;
                        stats.pointsGained += points;
                    } catch (IllegalArgumentException ignored) {
                        // ligne corrompue, on l'ignore et on continue la lecture
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de lire l'historique HikaBrain (" + file.getName() + ") : " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Agrège les confrontations directes sur la plage de dates [from, to] incluse, dans
     * le même format que {@link HeadToHeadManager#getAll()}, pour pouvoir calculer un
     * classement de force limité à cette période via {@link PowerRankingCalculator}.
     */
    public Map<UUID, HeadToHeadManager.PlayerHeadToHead> aggregateHeadToHead(LocalDate from, LocalDate to) {
        Map<UUID, HeadToHeadManager.PlayerHeadToHead> result = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            File file = fileFor(h2hHistoryDir, d);
            if (!file.exists()) continue;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 5) continue;

                    try {
                        UUID winnerUuid = UUID.fromString(parts[1]);
                        String winnerName = parts[2];
                        UUID loserUuid = UUID.fromString(parts[3]);
                        String loserName = parts[4];

                        HeadToHeadManager.PlayerHeadToHead winnerData = result.computeIfAbsent(winnerUuid, k -> new HeadToHeadManager.PlayerHeadToHead());
                        winnerData.name = winnerName;
                        winnerData.opponents.computeIfAbsent(loserUuid, k -> new HeadToHeadManager.Record()).wins++;

                        HeadToHeadManager.PlayerHeadToHead loserData = result.computeIfAbsent(loserUuid, k -> new HeadToHeadManager.PlayerHeadToHead());
                        loserData.name = loserName;
                        loserData.opponents.computeIfAbsent(winnerUuid, k -> new HeadToHeadManager.Record()).losses++;
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de lire l'historique de confrontations HikaBrain (" + file.getName() + ") : " + e.getMessage());
            }
        }
        return result;
    }

    /** Bornes [from, to] (incluses) pour "aujourd'hui". */
    public LocalDate[] rangeForToday() {
        LocalDate t = today();
        return new LocalDate[]{ t, t };
    }

    /** Bornes [from, to] (incluses) pour "cette semaine" (7 derniers jours glissants, aujourd'hui inclus). */
    public LocalDate[] rangeForWeek() {
        LocalDate t = today();
        return new LocalDate[]{ t.minusDays(6), t };
    }
}
