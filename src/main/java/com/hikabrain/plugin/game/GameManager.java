package com.hikabrain.plugin.game;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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

    // Joueurs actuellement gelés (après un point marqué)
    private final Set<UUID> frozenPlayers = new HashSet<>();

    // ================= INTÉGRATION TOURNOI =================
    // Permet au système de tournoi (com.hikabrain.plugin.tournament) de réserver
    // temporairement cette arène pour un match précis, d'y forcer des joueurs dans
    // une équipe donnée (sans passer par l'équilibrage automatique), et d'être
    // notifié du vainqueur une fois la partie terminée.

    /** true si cette arène est actuellement réservée pour un match de tournoi (bloque les jointures publiques). */
    private boolean reservedForTournament = false;

    /** Callback appelé avec l'équipe gagnante dès qu'un match réservé se termine. */
    private java.util.function.Consumer<Team> tournamentEndCallback = null;

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
     * Renvoie le nombre maximum de joueurs effectif pour cette arène : la valeur spécifique
     * configurée via /hb setmaxplayers si elle existe, sinon le max-players global du config.yml.
     */
    public int getMaxPlayers() {
        int specific = arena.getMaxPlayers();
        if (specific > 0) {
            return specific;
        }
        return plugin.getConfig().getInt("max-players", 16);
    }

    /**
     * Fait rejoindre un joueur au lobby d'attente. Renvoie false si la partie n'est pas joignable.
     */
    public boolean addPlayer(Player player) {
        if (reservedForTournament) {
            MessageUtil.send(player, "&cCette arène est réservée pour un match de tournoi, réessaie plus tard.");
            return false;
        }
        if (!arena.isFullyConfigured()) {
            MessageUtil.send(player, "&cLa map n'est pas encore configurée. Contacte un admin.");
            return false;
        }
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.ENDING) {
            MessageUtil.send(player, "&cUne partie est déjà en cours, réessaie plus tard.");
            return false;
        }
        int max = getMaxPlayers();
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

        // Dégeler si besoin
        if (frozenPlayers.remove(uuid)) {
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        }
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

        // Donner l'item "quitter la partie" en slot 8 (pour tous les joueurs du lobby)
        player.getInventory().setItem(KitManager.LEAVE_SLOT, KitManager.createLeaveItem());
    }

    private void restorePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
    }

    // ================= INTÉGRATION TOURNOI (API PUBLIQUE) =================

    /** Réserve cette arène : bloque les jointures publiques normales (utilisé pendant un match de tournoi). */
    public void reserveForTournament() {
        this.reservedForTournament = true;
    }

    /** Libère la réservation : l'arène redevient joignable normalement. */
    public void releaseTournamentReservation() {
        this.reservedForTournament = false;
        this.tournamentEndCallback = null;
    }

    public boolean isReservedForTournament() {
        return reservedForTournament;
    }

    /**
     * Enregistre un callback appelé (avec l'équipe gagnante) dès que la partie en cours
     * se termine. Utilisé par le TournamentManager pour savoir qui a gagné un match.
     */
    public void setTournamentEndCallback(java.util.function.Consumer<Team> callback) {
        this.tournamentEndCallback = callback;
    }

    /**
     * Ajoute un joueur directement dans l'équipe donnée, sans passer par l'équilibrage
     * automatique ni les vérifications habituelles de jointure publique (lobby plein, partie
     * en cours...). Réservé au système de tournoi : à utiliser uniquement sur une arène
     * préalablement réservée via {@link #reserveForTournament()}.
     */
    public boolean addPlayerToTeam(Player player, Team team) {
        if (!arena.isFullyConfigured()) {
            return false;
        }
        playerTeams.put(player.getUniqueId(), team);
        preLobbyLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.teleport(arena.getLobbySpawn());
        preparePlayerForLobby(player);
        plugin.getScoreboardManager().showScoreboard(player, this);
        return true;
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
        // Dès que le minimum de joueurs nécessaire pour lancer la partie est atteint,
        // on réduit l'attente à 10 secondes plutôt que d'utiliser le long countdown par
        // défaut : pas besoin de faire attendre tout le monde si la partie peut déjà commencer.
        countdownSecondsLeft = isFull
                ? plugin.getConfig().getInt("lobby-countdown-fast", 10)
                : plugin.getConfig().getInt("lobby-countdown-min-reached", 10);

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
        resetStats();
        arenaSnapshot.restore();
        teleportAllToSpawns();
        applyColoredNames();
        startOffhandReplenishTask();
        startCaptureScheduler();

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

    /**
     * Vérifie si un joueur est actuellement gelé.
     */
    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    /**
     * Gèle tous les joueurs en partie : vitesse à 0 + vélocité nulle.
     */
    private void freezeAllPlayers() {
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                frozenPlayers.add(uuid);
                player.setWalkSpeed(0f);
                player.setFlySpeed(0f);
                player.setVelocity(new Vector(0, 0, 0));
                // Bloquer aussi les inputs via GameMode Adventure pour éviter les interactions
                player.setAllowFlight(false);
            }
        }
    }

    /**
     * Dégèle tous les joueurs et restaure leur vitesse normale.
     */
    private void unfreezeAllPlayers() {
        for (UUID uuid : frozenPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
                player.setVelocity(new Vector(0, 0, 0));
            }
        }
        frozenPlayers.clear();
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
     * Vérifie la capture pour tous les joueurs (appelé chaque tick).
     */
    public void checkCaptureZone() {
        if (state != GameState.PLAYING) return;
        
        for (UUID playerId : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            
            Team playerTeam = playerTeams.get(playerId);
            Team enemyTeam = playerTeam.opponent();
            
            // Vérifier la capture
            Location loc = player.getLocation();
            
            if (arena.isInCaptureZone(enemyTeam, player.getLocation())) {
                scorePoint(playerTeam);
                break; // Un seul point à la fois
            }
        }
    }
    
    private BukkitTask captureSchedulerTask;
    
    // Stats de la partie en cours (par équipe)
    private int redKills = 0;
    private int redDeaths = 0;
    private int blueKills = 0;
    private int blueDeaths = 0;
    
    // Stats de la partie en cours (par joueur)
    private Map<UUID, Integer> playerKills = new HashMap<>();
    private Map<UUID, Integer> playerDeaths = new HashMap<>();
    
    /**
     * Démarre le scheduler qui vérifie la capture à chaque tick.
     */
    public void startCaptureScheduler() {
        if (captureSchedulerTask != null) {
            captureSchedulerTask.cancel();
        }
        captureSchedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkCaptureZone();
        }, 1L, 1L); // Toutes les 50ms (1 tick)
    }
    
    /**
     * Arrête le scheduler de capture.
     */
    public void stopCaptureScheduler() {
        if (captureSchedulerTask != null) {
            captureSchedulerTask.cancel();
            captureSchedulerTask = null;
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

        // Affiche un titre à l'écran de tous les joueurs en partie pour bien marquer le point.
        announceCaptureTitle(scoringTeam, currentScore, winScore);

        if (currentScore >= winScore) {
            endGame(scoringTeam);
        } else {
            startRoundReset();
        }
    }

    /**
     * Affiche un titre/sous-titre à tous les joueurs en partie quand un point est marqué,
     * pour que la capture soit visible immédiatement même si le chat n'est pas regardé.
     */
    private void announceCaptureTitle(Team scoringTeam, int currentScore, int winScore) {
        String title = scoringTeam.getColoredName() + " \u00a7lA MARQUÉ !";
        String subtitle = "\u00a7f" + currentScore + " \u00a77- " + winScore;
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle(title, subtitle, 5, 40, 10);
            }
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

        // Geler les joueurs immédiatement après téléportation
        freezeAllPlayers();

        roundResetSecondsLeft = plugin.getConfig().getInt("round-reset-countdown", 5);

        roundResetTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (roundResetSecondsLeft <= 0) {
                if (roundResetTask != null) {
                    roundResetTask.cancel();
                    roundResetTask = null;
                }
                // Dégeler avant de reprendre
                unfreezeAllPlayers();
                state = GameState.PLAYING;
                broadcast("&a&lÀ vous de jouer !");
                // Son de départ
                playSoundToAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1.2f);
                return;
            }

            broadcast("&eProchain round dans &6" + roundResetSecondsLeft + "&e...");

            // Sons du décompte : tic-tac à partir de 3
            if (roundResetSecondsLeft <= 3) {
                float pitch = roundResetSecondsLeft == 1 ? 1.4f : 0.9f;
                playSoundToAll(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            } else {
                // Tic discret pour les secondes au-delà de 3
                playSoundToAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.8f);
            }

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

        // Notifier le système de tournoi si ce match était un match de tournoi réservé.
        // On retire le callback immédiatement pour éviter un double appel.
        if (tournamentEndCallback != null) {
            java.util.function.Consumer<Team> callback = tournamentEndCallback;
            tournamentEndCallback = null;
            callback.accept(winner);
        }

        if (roundResetTask != null) {
            roundResetTask.cancel();
            roundResetTask = null;
        }
        
        stopCaptureScheduler();

        // Enregistrer la victoire dans les statistiques
        int teamSize = Math.max(getPlayerCountForTeam(Team.RED), getPlayerCountForTeam(Team.BLUE));
        plugin.getStatsManager().addWin(winner, teamSize);

        // Enregistrer le résultat individuel de chaque joueur
        for (Map.Entry<UUID, Team> entry : playerTeams.entrySet()) {
            UUID uuid = entry.getKey();
            Team team = entry.getValue();
            Player p  = Bukkit.getPlayer(uuid);
            String pName = p != null ? p.getName() : uuid.toString();
            boolean won = (team == winner);
            plugin.getStatsManager().addPlayerGameResult(uuid, pName, won, teamSize);
        }

        // Rafraîchir les leaderboards si actifs
        plugin.getLeaderboardManager().refreshAll();

        // Mettre tous les joueurs en spectateur et afficher l'écran de victoire
        List<String> redPlayers = new ArrayList<>();
        List<String> bluePlayers = new ArrayList<>();
        
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Dégeler (au cas où un point venait d'être marqué)
                frozenPlayers.remove(uuid);
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
                // Mettre en spectateur
                player.setGameMode(GameMode.SPECTATOR);
                
                // Ajouter à la liste de son équipe
                if (playerTeams.get(uuid) == Team.RED) {
                    redPlayers.add(player.getName());
                } else {
                    bluePlayers.add(player.getName());
                }
            }
        }
        
        // Afficher l'écran de victoire à tous les joueurs
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Titre principal
                player.sendTitle(
                    winner.getColoredName() + " \u00a7lVICTOIRE!",
                    "\u00a7f\u00a7oF\u00e9licitations \u00e0 l'\u00e9quipe gagnante!",
                    10, 70, 20
                );
                
                // Message de chat avec les membres
                StringBuilder message = new StringBuilder();
                message.append("\n");
                message.append(ChatColor.GOLD).append("\u00a7\u00a7------------------------------\n");
                message.append(ChatColor.WHITE).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                message.append(ChatColor.GOLD).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7 VICTOIRE DES ").append(winner.getColoredName()).append(" \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                message.append(ChatColor.GOLD).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                message.append("\n");
                
                if (winner == Team.RED) {
                    message.append(ChatColor.RED).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                    message.append(ChatColor.RED).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  ").append(ChatColor.BLUE).append("BLEUS\n");
                    message.append(ChatColor.RED).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  ").append(ChatColor.BLUE).append(bluePlayers.isEmpty() ? "(aucun)" : String.join(", ", bluePlayers)).append("\n");
                    message.append(ChatColor.RED).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  ").append(ChatColor.GRAY).append("\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                    message.append(ChatColor.RED).append("  ROUGES\n");
                    message.append(ChatColor.RED).append("  ").append(redPlayers.isEmpty() ? "(aucun)" : String.join(", ", redPlayers)).append("\n");
                } else {
                    message.append(ChatColor.BLUE).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  ").append(ChatColor.RED).append("ROUGES\n");
                    message.append(ChatColor.BLUE).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  ").append(ChatColor.RED).append(redPlayers.isEmpty() ? "(aucun)" : String.join(", ", redPlayers)).append("\n");
                    message.append(ChatColor.BLUE).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  ").append(ChatColor.GRAY).append("\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                    message.append(ChatColor.BLUE).append("  BLEUS\n");
                    message.append(ChatColor.BLUE).append("  ").append(bluePlayers.isEmpty() ? "(aucun)" : String.join(", ", bluePlayers)).append("\n");
                }
                
                message.append(ChatColor.GOLD).append("\n  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                message.append(ChatColor.WHITE).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                message.append(ChatColor.GRAY).append("  \u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\u00a7\n");
                
                player.sendMessage(message.toString());
            }
        }

        int delay = plugin.getConfig().getInt("restart-delay", 5);

        // Lancer des feux d'artifice aux spawns des joueurs de l'équipe gagnante
        launchVictoryFireworks(winner, delay);

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
        unfreezeAllPlayers();
        resetToLobby();
    }

    private void resetToLobby() {
        if (offhandReplenishTask != null) {
            offhandReplenishTask.cancel();
            offhandReplenishTask = null;
        }
        // S'assurer que personne n'est encore gelé
        unfreezeAllPlayers();
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
        unfreezeAllPlayers();
        resetToLobby();
    }

    // ================= SONS & EFFETS =================

    /**
     * Joue un son à tous les joueurs en partie, depuis leur propre position.
     */
    private void playSoundToAll(Sound sound, float volume, float pitch) {
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    /**
     * Lance des feux d'artifice aux couleurs de l'équipe gagnante pendant toute la durée
     * de l'écran de victoire.
     */
    private void launchVictoryFireworks(Team winner, int durationSeconds) {
        Color primary = (winner == Team.RED) ? Color.RED : Color.BLUE;
        Color secondary = (winner == Team.RED) ? Color.ORANGE : Color.AQUA;

        // Lancer 2 salves séparées par 1.5 secondes pendant la durée du délai
        int salves = Math.max(1, durationSeconds - 1);
        for (int i = 0; i < salves; i++) {
            long delayTicks = i * 30L; // toutes les 1.5s
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID uuid : playerTeams.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) continue;
                    spawnFirework(player.getLocation().add(0, 1, 0), primary, secondary);
                }
            }, delayTicks);
        }
    }

    /**
     * Fait apparaître un feu d'artifice à une location donnée.
     */
    private void spawnFirework(Location location, Color primary, Color secondary) {
        Firework fw = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST)
                .withColor(primary)
                .withFade(secondary)
                .withFlicker()
                .withTrail()
                .build());
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.STAR)
                .withColor(secondary)
                .withFade(primary)
                .build());
        fw.setFireworkMeta(meta);
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
    
    // ================= STATS PARTIE =================
    
    public int getRedKills() { return redKills; }
    public int getRedDeaths() { return redDeaths; }
    public int getBlueKills() { return blueKills; }
    public int getBlueDeaths() { return blueDeaths; }
    
    public int getPlayerKills(UUID uuid) { return playerKills.getOrDefault(uuid, 0); }
    public int getPlayerDeaths(UUID uuid) { return playerDeaths.getOrDefault(uuid, 0); }
    
    public void addKill(Team team) {
        if (team == Team.RED) redKills++;
        else blueKills++;
    }
    
    public void addPlayerKill(UUID uuid) {
        playerKills.put(uuid, playerKills.getOrDefault(uuid, 0) + 1);
    }
    
    public void addDeath(Team team) {
        if (team == Team.RED) redDeaths++;
        else blueDeaths++;
    }
    
    public void addPlayerDeath(UUID uuid) {
        playerDeaths.put(uuid, playerDeaths.getOrDefault(uuid, 0) + 1);
    }
    
    public void resetStats() {
        redKills = 0;
        redDeaths = 0;
        blueKills = 0;
        blueDeaths = 0;
        playerKills.clear();
        playerDeaths.clear();
    }
}
