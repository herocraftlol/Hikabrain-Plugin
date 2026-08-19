package com.hikabrain.plugin.web;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.levels.LevelManager;
import com.hikabrain.plugin.stats.StatsManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Expose un petit serveur HTTP JSON (endpoint /hikabrain/leaderboard) avec les
 * statistiques de TOUS les joueurs HikaBrain connus : kills, morts, K/D, victoires,
 * parties jouées, coups donnés/reçus, buts marqués, temps de jeu, niveau et points.
 *
 * Le site vitrine (voir dossier du site, onglet "Classements") interroge cet endpoint
 * pour construire son classement, exactement sur le même principe que le plugin Velocity
 * "WebStatusVelocity" déjà utilisé pour le statut du réseau (même pattern : petit serveur
 * HTTP maison avec com.sun.net.httpserver, authentification par clé API dans l'en-tête
 * X-Api-Key, écoute sur 127.0.0.1 par défaut).
 *
 * Désactivé par défaut (voir config.yml, section "web-export") : n'importe qui pouvant
 * atteindre ce port pourrait sinon lire les statistiques de tous les joueurs.
 */
public class LeaderboardExportServer {

    private final HikaBrainPlugin plugin;
    private HttpServer httpServer;

    public LeaderboardExportServer(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("web-export.enabled", false)) {
            return;
        }

        String bindAddress = plugin.getConfig().getString("web-export.bind-address", "127.0.0.1");
        int port = plugin.getConfig().getInt("web-export.port", 8182);
        String apiKey = plugin.getConfig().getString("web-export.api-key", "change-moi");

        if ("change-moi".equals(apiKey)) {
            plugin.getLogger().warning("[HikaBrain] Pense à changer 'web-export.api-key' dans config.yml avant d'exposer le classement au site web !");
        }

        try {
            httpServer = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            httpServer.createContext("/hikabrain/leaderboard", new LeaderboardHandler(apiKey));
            httpServer.setExecutor(null);
            httpServer.start();
            plugin.getLogger().info("[HikaBrain] Export web du classement démarré sur http://" + bindAddress + ":" + port + "/hikabrain/leaderboard");
        } catch (IOException e) {
            plugin.getLogger().severe("[HikaBrain] Impossible de démarrer l'export web du classement : " + e.getMessage());
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    private class LeaderboardHandler implements HttpHandler {

        private final String apiKey;

        private LeaderboardHandler(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * Une ligne du classement, quelle que soit son origine (stats à vie ou agrégées
         * sur une plage de temps) — voir {@link #buildAllTimeRows} et {@link #buildPeriodRows}.
         * playtimeSeconds et forceRank sont nullables : le temps de jeu n'est pas suivi par
         * plage de temps (seulement à vie), et forceRank est absent tant qu'aucune
         * confrontation n'est enregistrée sur la période choisie.
         */
        private record Row(
                UUID uuid, String username, boolean online,
                int kills, int deaths, double kd, int wins, int gamesPlayed,
                int hitsGiven, int hitsReceived, int goalsScored,
                Long playtimeSeconds, int level, int points,
                double force, Integer forceRank, int forceWins, int forceLosses, int forceOpponents, String bestWinOpponent,
                String formatsJson
        ) {
        }

        /**
         * Fragment JSON (objet déjà valide, pas besoin d'échappement) avec le détail par
         * FORMAT D'ÉQUIPE (1v1/2v2/3v3/4v4) : victoires, kills, parties jouées et K/D
         * dans ce format précis — voir StatsManager.GameMode. Uniquement disponible pour
         * le classement "depuis toujours" (pas de suivi par format sur une plage de temps
         * précise, contrairement aux stats globales) : renvoie "{}" pour une plage de dates.
         */
        private String buildFormatsJson(StatsManager.PlayerStats stats) {
            if (stats == null) return "{}";
            List<String> parts = new ArrayList<>();
            for (StatsManager.GameMode m : StatsManager.GameMode.values()) {
                parts.add("\"" + m.getLabel() + "\":{"
                        + "\"wins\":" + stats.getWins(m) + ","
                        + "\"kills\":" + stats.getKills(m) + ","
                        + "\"gamesPlayed\":" + stats.getGamesPlayed(m) + ","
                        + "\"kd\":" + stats.getKD(m)
                        + "}");
            }
            return "{" + String.join(",", parts) + "}";
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String providedKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
            if (apiKey == null || !apiKey.equals(providedKey)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            LocalDate[] range = resolveRange(query); // null = classement "depuis toujours"

            LevelManager levelManager = plugin.getLevelManager();
            List<Row> rows = range == null ? buildAllTimeRows(levelManager) : buildPeriodRows(levelManager, range[0], range[1]);

            StringBuilder json = new StringBuilder();
            json.append('[');
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) json.append(',');
                json.append(toJson(rows.get(i)));
            }
            json.append(']');

            sendJson(exchange, 200, json.toString());
        }

        /**
         * Classement "depuis toujours" : comportement historique, inchangé — lit
         * directement les totaux cumulés de StatsManager/LevelManager/HeadToHeadManager.
         */
        private List<Row> buildAllTimeRows(LevelManager levelManager) {
            StatsManager statsManager = plugin.getStatsManager();
            com.hikabrain.plugin.stats.HeadToHeadManager h2hManager = plugin.getHeadToHeadManager();

            List<com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower> powerRanking =
                    com.hikabrain.plugin.stats.PowerRankingCalculator.compute(h2hManager);
            Map<UUID, com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower> powerByUuid = new HashMap<>();
            Map<UUID, Integer> powerRank = new HashMap<>();
            for (int i = 0; i < powerRanking.size(); i++) {
                var pp = powerRanking.get(i);
                powerByUuid.put(pp.uuid, pp);
                powerRank.put(pp.uuid, i + 1);
            }

            // Union des joueurs connus par les gestionnaires (en pratique quasi toujours
            // identiques, chaque partie mettant à jour tout le monde en même temps, mais on
            // reste robuste si l'un des fichiers a été édité/reset à part).
            Set<UUID> allUuids = new HashSet<>();
            allUuids.addAll(statsManager.getAllPlayerStats().keySet());
            allUuids.addAll(levelManager.getAllPlayerLevels().keySet());

            List<Row> rows = new ArrayList<>();
            for (UUID uuid : allUuids) {
                StatsManager.PlayerStats stats = statsManager.getAllPlayerStats().get(uuid);
                LevelManager.PlayerLevelData levelData = levelManager.getAllPlayerLevels().get(uuid);

                String username = stats != null ? stats.name : (levelData != null ? levelData.name : "?");
                if (username == null || username.isBlank() || "?".equals(username)) continue; // entrée corrompue

                int points = levelData != null ? levelData.points : 0;
                var power = powerByUuid.get(uuid);

                rows.add(new Row(
                        uuid, username, Bukkit.getPlayer(uuid) != null,
                        stats != null ? stats.kills : 0,
                        stats != null ? stats.deaths : 0,
                        stats != null ? stats.getKD() : 0.0,
                        stats != null ? stats.gamesWon : 0,
                        stats != null ? stats.gamesPlayed : 0,
                        stats != null ? stats.hitsGiven : 0,
                        stats != null ? stats.hitsReceived : 0,
                        stats != null ? stats.goalsScored : 0,
                        stats != null ? stats.playtimeSeconds : 0L,
                        levelManager.getLevelForPoints(points),
                        points,
                        power != null ? power.score * 1000 : 0.0,
                        powerRank.get(uuid),
                        power != null ? power.totalWins : 0,
                        power != null ? power.totalLosses : 0,
                        power != null ? power.distinctOpponents : 0,
                        power != null ? power.bestWinOpponentName : null,
                        buildFormatsJson(stats)
                ));
            }
            return rows;
        }

        /**
         * Classement limité à une plage de dates [from, to] incluse : lit l'historique
         * journalier (voir MatchHistoryManager) plutôt que les totaux à vie. "points"
         * représente alors les points GAGNÉS pendant la période (pas le total à vie), et
         * "playtimeSeconds" est absent (null) car le temps de jeu n'est suivi qu'à vie.
         * "level" reste le niveau ACTUEL du joueur (il n'y a pas de "niveau pour une période").
         */
        private List<Row> buildPeriodRows(LevelManager levelManager, LocalDate from, LocalDate to) {
            com.hikabrain.plugin.stats.MatchHistoryManager history = plugin.getMatchHistoryManager();
            Map<UUID, com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats> agg = history.aggregatePlayerStats(from, to);
            Map<UUID, com.hikabrain.plugin.stats.HeadToHeadManager.PlayerHeadToHead> h2h = history.aggregateHeadToHead(from, to);

            List<com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower> powerRanking =
                    com.hikabrain.plugin.stats.PowerRankingCalculator.compute(h2h);
            Map<UUID, com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower> powerByUuid = new HashMap<>();
            Map<UUID, Integer> powerRank = new HashMap<>();
            for (int i = 0; i < powerRanking.size(); i++) {
                var pp = powerRanking.get(i);
                powerByUuid.put(pp.uuid, pp);
                powerRank.put(pp.uuid, i + 1);
            }

            List<Row> rows = new ArrayList<>();
            for (Map.Entry<UUID, com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats> entry : agg.entrySet()) {
                UUID uuid = entry.getKey();
                var s = entry.getValue();
                if (s.name == null || s.name.isBlank()) continue;

                var power = powerByUuid.get(uuid);

                rows.add(new Row(
                        uuid, s.name, Bukkit.getPlayer(uuid) != null,
                        s.kills, s.deaths, s.getKD(), s.wins, s.gamesPlayed,
                        s.hits, s.hitsReceived, s.goals,
                        null, // playtimeSeconds non suivi par plage de temps
                        levelManager.getLevel(uuid), s.pointsGained,
                        power != null ? power.score * 1000 : 0.0,
                        powerRank.get(uuid),
                        power != null ? power.totalWins : 0,
                        power != null ? power.totalLosses : 0,
                        power != null ? power.distinctOpponents : 0,
                        power != null ? power.bestWinOpponentName : null,
                        "{}" // détail par format non suivi sur une plage de temps précise, seulement à vie
                ));
            }
            return rows;
        }

        private String toJson(Row r) {
            return "{"
                    + "\"uuid\":\"" + r.uuid() + "\","
                    + "\"username\":\"" + escape(r.username()) + "\","
                    + "\"online\":" + r.online() + ","
                    + "\"kills\":" + r.kills() + ","
                    + "\"deaths\":" + r.deaths() + ","
                    + "\"kd\":" + r.kd() + ","
                    + "\"wins\":" + r.wins() + ","
                    + "\"gamesPlayed\":" + r.gamesPlayed() + ","
                    + "\"hitsGiven\":" + r.hitsGiven() + ","
                    + "\"hitsReceived\":" + r.hitsReceived() + ","
                    + "\"goalsScored\":" + r.goalsScored() + ","
                    + "\"playtimeSeconds\":" + (r.playtimeSeconds() != null ? r.playtimeSeconds() : "null") + ","
                    + "\"level\":" + r.level() + ","
                    + "\"points\":" + r.points() + ","
                    + "\"force\":" + r.force() + ","
                    + "\"forceRank\":" + (r.forceRank() != null ? r.forceRank() : "null") + ","
                    + "\"forceWins\":" + r.forceWins() + ","
                    + "\"forceLosses\":" + r.forceLosses() + ","
                    + "\"forceOpponents\":" + r.forceOpponents() + ","
                    + "\"bestWinOpponent\":" + (r.bestWinOpponent() != null ? ("\"" + escape(r.bestWinOpponent()) + "\"") : "null") + ","
                    + "\"formats\":" + r.formatsJson()
                    + "}";
        }

        /**
         * Détermine la plage de dates demandée par la requête :
         *  - ?from=AAAA-MM-JJ&to=AAAA-MM-JJ  → plage précise (prioritaire si présente)
         *  - ?range=today                    → aujourd'hui uniquement
         *  - ?range=week                      → 7 derniers jours glissants
         *  - ?range=alltime (ou absent)       → null = classement "depuis toujours"
         * Renvoie null si absent/invalide (fallback silencieux vers "depuis toujours").
         */
        private LocalDate[] resolveRange(Map<String, String> query) {
            String from = query.get("from");
            String to = query.get("to");
            if (from != null && to != null) {
                try {
                    LocalDate f = LocalDate.parse(from);
                    LocalDate t = LocalDate.parse(to);
                    return f.isAfter(t) ? new LocalDate[]{ t, f } : new LocalDate[]{ f, t };
                } catch (DateTimeParseException ignored) {
                    // dates invalides : on retombe sur "depuis toujours" plutôt que planter
                }
            }

            String rangeParam = query.getOrDefault("range", "alltime").toLowerCase();
            return switch (rangeParam) {
                case "today" -> plugin.getMatchHistoryManager().rangeForToday();
                case "week" -> plugin.getMatchHistoryManager().rangeForWeek();
                default -> null;
            };
        }

        /** Parse une query string brute ("a=1&b=2") en map, sans dépendance externe. */
        private Map<String, String> parseQuery(String rawQuery) {
            Map<String, String> params = new HashMap<>();
            if (rawQuery == null || rawQuery.isBlank()) return params;
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
            return params;
        }

        private String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
