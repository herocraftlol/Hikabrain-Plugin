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
import java.nio.charset.StandardCharsets;
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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String providedKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
            if (apiKey == null || !apiKey.equals(providedKey)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            StatsManager statsManager = plugin.getStatsManager();
            LevelManager levelManager = plugin.getLevelManager();
            com.hikabrain.plugin.stats.HeadToHeadManager h2hManager = plugin.getHeadToHeadManager();

            // Classement de force ("qui bat qui", voir PowerRankingCalculator) : calculé une
            // fois par requête et indexé par UUID pour un accès O(1) ci-dessous.
            List<com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower> powerRanking =
                    com.hikabrain.plugin.stats.PowerRankingCalculator.compute(h2hManager);
            Map<UUID, com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower> powerByUuid = new HashMap<>();
            Map<UUID, Integer> powerRank = new HashMap<>();
            for (int i = 0; i < powerRanking.size(); i++) {
                com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower pp = powerRanking.get(i);
                powerByUuid.put(pp.uuid, pp);
                powerRank.put(pp.uuid, i + 1);
            }

            // Union des joueurs connus par les gestionnaires (en pratique quasi toujours
            // identiques, chaque partie mettant à jour tout le monde en même temps, mais on
            // reste robuste si l'un des fichiers a été édité/reset à part).
            Set<UUID> allUuids = new HashSet<>();
            allUuids.addAll(statsManager.getAllPlayerStats().keySet());
            allUuids.addAll(levelManager.getAllPlayerLevels().keySet());

            StringBuilder json = new StringBuilder();
            json.append('[');
            boolean first = true;
            for (UUID uuid : allUuids) {
                StatsManager.PlayerStats stats = statsManager.getAllPlayerStats().get(uuid);
                LevelManager.PlayerLevelData levelData = levelManager.getAllPlayerLevels().get(uuid);

                String username = stats != null ? stats.name : (levelData != null ? levelData.name : "?");
                if (username == null || username.isBlank() || "?".equals(username)) continue; // entrée corrompue, on l'ignore

                int kills          = stats != null ? stats.kills : 0;
                int deaths         = stats != null ? stats.deaths : 0;
                int gamesPlayed    = stats != null ? stats.gamesPlayed : 0;
                int gamesWon       = stats != null ? stats.gamesWon : 0;
                int hitsGiven      = stats != null ? stats.hitsGiven : 0;
                int hitsReceived   = stats != null ? stats.hitsReceived : 0;
                int goalsScored    = stats != null ? stats.goalsScored : 0;
                long playtimeSeconds = stats != null ? stats.playtimeSeconds : 0L;
                double kd          = stats != null ? stats.getKD() : 0.0;

                int points = levelData != null ? levelData.points : 0;
                int level  = levelManager.getLevelForPoints(points);

                boolean online = Bukkit.getPlayer(uuid) != null;
                com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower power = powerByUuid.get(uuid);
                double force = power != null ? power.score * 1000 : 0.0;
                Integer forceRank = powerRank.get(uuid); // null si aucune confrontation enregistrée
                int forceWins = power != null ? power.totalWins : 0;
                int forceLosses = power != null ? power.totalLosses : 0;
                int forceOpponents = power != null ? power.distinctOpponents : 0;
                String bestWinName = power != null ? power.bestWinOpponentName : null;

                if (!first) json.append(',');
                first = false;

                json.append('{')
                        .append("\"uuid\":\"").append(uuid).append("\",")
                        .append("\"username\":\"").append(escape(username)).append("\",")
                        .append("\"online\":").append(online).append(',')
                        .append("\"kills\":").append(kills).append(',')
                        .append("\"deaths\":").append(deaths).append(',')
                        .append("\"kd\":").append(kd).append(',')
                        .append("\"wins\":").append(gamesWon).append(',')
                        .append("\"gamesPlayed\":").append(gamesPlayed).append(',')
                        .append("\"hitsGiven\":").append(hitsGiven).append(',')
                        .append("\"hitsReceived\":").append(hitsReceived).append(',')
                        .append("\"goalsScored\":").append(goalsScored).append(',')
                        .append("\"playtimeSeconds\":").append(playtimeSeconds).append(',')
                        .append("\"level\":").append(level).append(',')
                        .append("\"points\":").append(points).append(',')
                        .append("\"force\":").append(force).append(',')
                        .append("\"forceRank\":").append(forceRank != null ? forceRank : "null").append(',')
                        .append("\"forceWins\":").append(forceWins).append(',')
                        .append("\"forceLosses\":").append(forceLosses).append(',')
                        .append("\"forceOpponents\":").append(forceOpponents).append(',')
                        .append("\"bestWinOpponent\":").append(bestWinName != null ? ("\"" + escape(bestWinName) + "\"") : "null")
                        .append('}');
            }
            json.append(']');

            sendJson(exchange, 200, json.toString());
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
