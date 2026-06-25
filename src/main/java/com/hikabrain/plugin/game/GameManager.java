package com.hikabrain.plugin.game;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.Collections;

/**
 * Gère l'intégralité du cycle de vie d'une partie HikaBrain pour UNE arène nommée :
 * lobby d'attente -> compte à rebours -> partie -> fin -> reset.
 *
 * Plusieurs instances de GameManager peuvent coexister (une par arène), gérées par
 * ArenaManager, ce qui permet plusieurs parties HikaBrain simultanées et indépendantes
 * dans un même monde.
 */
public class GameManager {

    private final HikaBrainPlugin plugin;
    private final String arenaName;
    private final Arena arena;
    private final ArenaSnapshot arenaSnapshot;
    private final File snapshotFile;
    private final File arenaConfigFile;
    private final org.bukkit.configuration.file.YamlConfiguration arenaConfig;

    private GameState state = GameState.NOT_CONFIGURED;

    // Joueurs en lobby/en partie, et leur équipe assignée
    private final Map<UUID, Team> playerTeams = new HashMap<>();

    // Positions des joueurs avant qu'ils rejoignent le lobby (pour restauration à la fin)
    private final Map<UUID, Location> preLobbyLocations = new HashMap<>();

    private final Map<Team, Integer> scores = new EnumMap<>(Team.class);

    private BukkitTask countdownTask;
    private BukkitTask roundResetTask;
    private BukkitTask offhandReplenishTask;
    private int countdownSecondsLeft;
    private int roundResetSecondsLeft;

    public GameManager(HikaBrainPlugin plugin, String arenaName) {
        this.plugin = plugin;
        this.arenaName = arenaName;
        this.arena = new Arena();
        this.arenaSnapshot = new ArenaSnapshot(plugin.getLogger());

        File arenasDir = new File(plugin.getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
        }
        this.snapshotFile = new File(arenasDir, arenaName + ".snapshot");
        this.arenaConfigFile = new File(arenasDir, arenaName + ".yml");
        this.arenaConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(arenaConfigFile);

        resetScores();
    }

    public String getName() {
        return arenaName;
    }

    public Arena getArena() {
        return arena;
    }

    public ArenaSnapshot getArenaSnapshot() {
        return arenaSnapshot;
    }

    /**
     * Capture l'état actuel de la zone de jeu et le sauvegarde sur disque.
     * Doit être appelé par la commande admin après avoir défini la zone de jeu.
     */
    public void captureGameZone() {
        CuboidRegion zone = arena.getGameZone();
        if (zone == null) return;
        arenaSnapshot.capture(zone);
        arenaSnapshot.saveToFile(snapshotFile);
    }

    /**
     * Charge le snapshot existant depuis le disque, s'il y en a un (appelé au démarrage du plugin).
     */
    public void loadGameZoneSnapshot() {
        arenaSnapshot.loadFromFile(snapshotFile);
    }

    /**
     * Sauvegarde la configuration de cette arène (spawns, zones, lobby) dans son propre fichier.
     */
    public void saveArenaConfig() {
        arena.saveToConfig(arenaConfig);
        try {
            arenaConfig.save(arenaConfigFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder l'arène '" + arenaName + "' : " + e.getMessage());
        }
    }

    /**
     * Charge la configuration de cette arène depuis son propre fichier (appelé au démarrage du plugin).
     */
    public void loadArenaConfig() {
        arena.loadFromConfig(arenaConfig);
    }

    public GameState getState() {
        return state;
    }

    private void resetScores() {
        scores.put(Team.RED, 0);
        scores.put(Team.BLUE, 0);
    }

    // ================= GESTION DES JOUEURS =================

    public boolean isPlaying(Player player) {
        return playerTeams.containsKey(player.getUniqueId());
    }

    public Team getTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }

    public int getPlayerCount() {
        return playerTeams.size();
    }

    /**
     * Retourne la map des équipes des joueurs.
     */
    public Map<UUID, Team> getPlayerTeams() {
        return Collections.unmodifiableMap(playerTeams);
    }

    /**
     * Retourne le nombre de joueurs dans une équipe donnée.
     */
    public int getPlayerCountForTeam(Team team) {
        return (int) playerTeams.values().stream().filter(t -> t == team).count();
    }

    /**
     * Fait rejoindre un joueur au lobby d'attente. Renvoie false si la partie n'est pas joignable.
     */
    public boolean addPlayer(Player player) {
        if (!arena.isFullyConfigured()) {
            MessageUtil.send(player, "&cLa map n'est pas encore configurée. Contacte un admin.");
            return false;
        }
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.ENDING) {
            MessageUtil.send(player, "&cUne partie est déjà en cours, réessaie plus tard.");
            return false;
        }
        int max = plugin.getConfig().getInt("max-players", 16);
        if (playerTeams.size() >= max) {
            MessageUtil.send(player, "&cLe lobby est complet.");
            return false;
        }

        Team team = pickBalancedTeam();
        playerTeams.put(player.getUniqueId(), team);

        // Sauvegarder la position du joueur avant de le téléporter au lobby
        preLobbyLocations.put(player.getUniqueId(), player.getLocation().clone());

        player.teleport(arena.getLobbySpawn());
        preparePlayerForLobby(player);

        // Afficher le scoreboard au joueur
        plugin.getScoreboardManager().showScoreboard(player, this);

        broadcast(MessageUtil.format(plugin.getConfig().getString("messages.join", ""))
                .replace("%current%", String.valueOf(playerTeams.size()))
                .replace("%max%", String.valueOf(max)));
        MessageUtil.send(player, plugin.getConfig().getString("messages.team-assigned", "")
                .replace("%team%", team.getColoredName()));

        checkLobbyStart();
        return true;
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerTeams.containsKey(uuid)) {
            return;
        }
        playerTeams.remove(uuid);
        MessageUtil.send(player, plugin.getConfig().getString("messages.leave", ""));
        
        // Restaurer la position pré-lobby si disponible
        Location preLobbyLocation = preLobbyLocations.remove(uuid);
        if (preLobbyLocation != null) {
            restorePlayer(player);
            player.teleport(preLobbyLocation);
        } else {
            restorePlayer(player);
        }

        // Supprimer le scoreboard du joueur
        plugin.getScoreboardManager().removeScoreboard(player);

        // Si plus assez de joueurs ou une équipe vide pendant le countdown, on l'annule
        if (state == GameState.COUNTDOWN
                && (playerTeams.size() < plugin.getConfig().getInt("min-players", 2) || !bothTeamsHavePlayers())) {
            cancelCountdown();
        }

        // Si une partie est en cours et qu'une équipe se vide totalement, on arrête.
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET) {
            checkForfeit();
        }
    }

    private Team pickBalancedTeam() {
        long redCount = playerTeams.values().stream().filter(t -> t == Team.RED).count();
        long blueCount = playerTeams.values().stream().filter(t -> t == Team.BLUE).count();
        return redCount <= blueCount ? Team.RED : Team.BLUE;
    }

    /**
     * Change l'équipe d'un joueur et met à jour son item de sélection d'équipe.
     */
    public boolean changePlayerTeam(Player player, Team newTeam) {
        UUID uuid = player.getUniqueId();
        if (!playerTeams.containsKey(uuid)) {
            return false;
        }
        
        // Ne pas permettre le changement d'équipe pendant la partie
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.COUNTDOWN) {
            return false;
        }
        
        Team oldTeam = playerTeams.get(uuid);
        if (oldTeam == newTeam) {
            return false;
        }
        
        playerTeams.put(uuid, newTeam);
        
        // Mettre à jour l'item de sélection d'équipe
        player.getInventory().setItem(KitManager.TEAM_SELECT_SLOT, KitManager.createTeamSelectorItem(newTeam));
        
        // Mettre à jour l'armure
        KitManager.equipArmor(player, newTeam);
        
        MessageUtil.send(player, plugin.getConfig().getString("messages.team-changed", "")
                .replace("%team%", newTeam.getColoredName()));
        
        return true;
    }

    private void preparePlayerForLobby(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        
        // Donner le sel d'équipe (terracotta coloré)
        Team team = playerTeams.get(player.getUniqueId());
        player.getInventory().setItem(KitManager.TEAM_SELECT_SLOT, KitManager.createTeamSelectorItem(team));
        
        // Donner le diamant de démarrage forcé aux admins
        if (player.hasPermission("hikabrain.admin")) {
            player.getInventory().setItem(KitManager.FORCESTART_SLOT, KitManager.createForceStartItem());
        }
    }

    private void restorePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
    }

    // ================= LOBBY / COUNTDOWN =================

    private void checkLobbyStart() {
        if (state != GameState.WAITING && state != GameState.NOT_CONFIGURED) {
            return;
        }
        int min = plugin.getConfig().getInt("min-players", 2);
        if (playerTeams.size() >= min && bothTeamsHavePlayers()) {
            startCountdown();
        } else {
            state = GameState.WAITING;
        }
    }

    /**
     * Vérifie qu'aucune des deux équipes n'est vide (condition nécessaire pour démarrer une partie).
     */
    private boolean bothTeamsHavePlayers() {
        long redCount = playerTeams.values().stream().filter(t -> t == Team.RED).count();
        long blueCount = playerTeams.values().stream().filter(t -> t == Team.BLUE).count();
        return redCount > 0 && blueCount > 0;
    }

    private void startCountdown() {
        state = GameState.COUNTDOWN;
        int max = plugin.getConfig().getInt("max-players", 16);
        boolean isFull = playerTeams.size() >= max;
        countdownSecondsLeft = isFull
                ? plugin.getConfig().getInt("lobby-countdown-fast", 10)
                : plugin.getConfig().getInt("lobby-countdown", 30);

        broadcast(plugin.getConfig().getString("messages.countdown-start", "")
                .replace("%time%", String.valueOf(countdownSecondsLeft)));

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdownSecondsLeft <= 0) {
                countdownTask.cancel();
                countdownTask = null;
                startGame();
                return;
            }
            if (countdownSecondsLeft <= 5 || countdownSecondsLeft % 10 == 0) {
                broadcast("&e" + countdownSecondsLeft + "...");
            }
            countdownSecondsLeft--;
        }, 0L, 20L);
    }

    /**
     * Annule un compte à rebours en cours parce que les conditions de démarrage ne sont
     * plus remplies (joueur parti, équipe vidée). Ne doit pas être appelé lors d'une
     * transition normale vers le démarrage de la partie (voir startCountdown ci-dessus).
     */
    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (state == GameState.COUNTDOWN) {
            state = GameState.WAITING;
            broadcast("&cPas assez de joueurs, le compte à rebours est annulé.");
        }
    }

    // ================= PARTIE =================

    private void startGame() {
        state = GameState.PLAYING;
        resetScores();
        arenaSnapshot.restore();
        teleportAllToSpawns();
        applyColoredNames();
        startOffhandReplenishTask();

        // Mettre à jour le scoreboard pour tous les joueurs
        plugin.getScoreboardManager().onGameStart(this);

        broadcast(plugin.getConfig().getString("messages.game-start", ""));
    }

    /**
     * Lance une tâche périodique qui vérifie, pour chaque joueur en partie, qu'il a bien
     * 64 grès lisse en offhand, et complète si besoin (le joueur peut poser/casser le bloc
     * normalement, il est juste toujours réapprovisionné à 64).
     */
    private void startOffhandReplenishTask() {
        if (offhandReplenishTask != null) {
            offhandReplenishTask.cancel();
        }
        offhandReplenishTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : playerTeams.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    KitManager.replenishOffhandBlocks(player);
                }
            }
        }, 40L, 40L); // toutes les 2 secondes, pas besoin de plus fréquent
    }

    /**
     * Applique le préfixe/couleur d'équipe au nom affiché (tab list, au-dessus de la tête)
     * pour chaque joueur en partie.
     */
    private void applyColoredNames() {
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            Team team = playerTeams.get(uuid);
            String coloredName = team.getColor() + player.getName();
            player.setPlayerListName(coloredName);
            player.setDisplayName(coloredName);
        }
    }

    /**
     * Retire la coloration de nom appliquée pendant la partie.
     */
    private void clearColoredNames(Iterable<UUID> uuids) {
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.setPlayerListName(player.getName());
            player.setDisplayName(player.getName());
        }
    }

    private void teleportAllToSpawns() {
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            Team team = playerTeams.get(uuid);
            player.teleport(arena.getSpawn(team));
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20);
            player.setFoodLevel(20);
            KitManager.giveFullKit(player, team);
        }
    }

    /**
     * Téléporte tout le monde à son spawn lors d'un round reset, sans redonner le kit complet
     * (le joueur garde son épée/pioche/armure tels qu'ils sont) — seule la pomme dorée est regivée.
     */
    private void teleportAllForRoundReset() {
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            Team team = playerTeams.get(uuid);
            player.teleport(arena.getSpawn(team));
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20);
            player.setFoodLevel(20);
            KitManager.regiveGoldenApple(player);
        }
    }

    /**
     * Appelée par le listener de mouvement à chaque fois qu'un joueur en partie se déplace.
     * Si le joueur entre dans la zone de capture adverse, marque le point instantanément.
     */
    public void handlePlayerMove(Player player) {
        if (state != GameState.PLAYING) return;
        if (!playerTeams.containsKey(player.getUniqueId())) return;

        Team playerTeam = playerTeams.get(player.getUniqueId());
        Team enemyTeam = playerTeam.opponent();

        if (arena.isInCaptureZone(enemyTeam, player.getLocation())) {
            scorePoint(playerTeam);
        }
    }

    /**
     * Marque un point pour l'équipe donnée, annonce le score, et soit termine la partie,
     * soit lance le compte à rebours du round suivant.
     */
    private void scorePoint(Team scoringTeam) {
        addScore(scoringTeam, 1);
        int winScore = plugin.getConfig().getInt("points-to-win", 5);
        int currentScore = scores.get(scoringTeam);

        broadcast(plugin.getConfig().getString("messages.point-scored", "")
                .replace("%points%", "1")
                .replace("%team%", scoringTeam.getColoredName())
                .replace("%score%", String.valueOf(currentScore))
                .replace("%win%", String.valueOf(winScore)));

        if (currentScore >= winScore) {
            endGame(scoringTeam);
        } else {
            startRoundReset();
        }
    }

    /**
     * Téléporte tout le monde à son spawn et lance un court compte à rebours
     * avant que la capture ne soit de nouveau active.
     */
    private void startRoundReset() {
        state = GameState.ROUND_RESET;
        teleportAllForRoundReset();
        arenaSnapshot.restore();

        roundResetSecondsLeft = plugin.getConfig().getInt("round-reset-countdown", 5);

        roundResetTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (roundResetSecondsLeft <= 0) {
                if (roundResetTask != null) {
                    roundResetTask.cancel();
                    roundResetTask = null;
                }
                state = GameState.PLAYING;
                broadcast("&a&lÀ vous de jouer !");
                return;
            }
            broadcast("&eProchain round dans &6" + roundResetSecondsLeft + "&e...");
            roundResetSecondsLeft--;
        }, 0L, 20L);
    }

    private void addScore(Team team, int amount) {
        scores.put(team, scores.get(team) + amount);
    }

    public int getScore(Team team) {
        return scores.getOrDefault(team, 0);
    }

    private void checkForfeit() {
        long redCount = playerTeams.values().stream().filter(t -> t == Team.RED).count();
        long blueCount = playerTeams.values().stream().filter(t -> t == Team.BLUE).count();

        if (redCount == 0 && blueCount > 0) {
            endGame(Team.BLUE);
        } else if (blueCount == 0 && redCount > 0) {
            endGame(Team.RED);
        } else if (redCount == 0 && blueCount == 0) {
            // Plus personne, on annule simplement sans vainqueur
            forceStopToLobby();
        }
    }

    private void endGame(Team winner) {
        if (state != GameState.PLAYING && state != GameState.ROUND_RESET) return;
        state = GameState.ENDING;

        if (roundResetTask != null) {
            roundResetTask.cancel();
            roundResetTask = null;
        }

        // Enregistrer la victoire dans les statistiques
        plugin.getStatsManager().addWin(winner);

        broadcast(plugin.getConfig().getString("messages.game-end", "")
                .replace("%team%", winner.getColoredName()));

        int delay = plugin.getConfig().getInt("restart-delay", 5);
        Bukkit.getScheduler().runTaskLater(plugin, this::resetToLobby, delay * 20L);
    }

    /**
     * Arrêt forcé sans vainqueur (ex: tout le monde a quitté).
     */
    private void forceStopToLobby() {
        if (roundResetTask != null) {
            roundResetTask.cancel();
            roundResetTask = null;
        }
        resetToLobby();
    }

    private void resetToLobby() {
        if (offhandReplenishTask != null) {
            offhandReplenishTask.cancel();
            offhandReplenishTask = null;
        }
        clearColoredNames(playerTeams.keySet());
        for (UUID uuid : new ArrayList<>(playerTeams.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restorePlayer(player);
                // Supprimer le scoreboard du joueur
                plugin.getScoreboardManager().removeScoreboard(player);
                // Restaurer à la position pré-lobby si configuré, sinon au lobby
                Location preLobbyLocation = preLobbyLocations.remove(uuid);
                if (plugin.getConfig().getBoolean("restore-pre-lobby-location", true) && preLobbyLocation != null) {
                    player.teleport(preLobbyLocation);
                } else if (plugin.getConfig().getBoolean("teleport-to-lobby-on-end", true) && arena.getLobbySpawn() != null) {
                    player.teleport(arena.getLobbySpawn());
                }
            }
        }
        playerTeams.clear();
        preLobbyLocations.clear();
        resetScores();
        state = arena.isFullyConfigured() ? GameState.WAITING : GameState.NOT_CONFIGURED;
    }

    /**
     * Commande admin pour forcer le démarrage immédiat (utile pour les tests).
     * Renvoie false si une partie est déjà en cours ou si une équipe est vide.
     */
    public boolean forceStart() {
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET) return false;
        if (!bothTeamsHavePlayers()) return false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        startGame();
        return true;
    }

    /**
     * Commande admin pour stopper une partie en cours et revenir au lobby.
     */
    public void forceStop() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (roundResetTask != null) {
            roundResetTask.cancel();
            roundResetTask = null;
        }
        resetToLobby();
    }

    // ================= UTILITAIRE =================

    private void broadcast(String rawMessage) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String message = MessageUtil.format(prefix + rawMessage);
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
