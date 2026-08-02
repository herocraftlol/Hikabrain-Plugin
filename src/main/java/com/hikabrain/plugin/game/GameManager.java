package com.hikabrain.plugin.game;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.gui.TeamSelectGUI;
import com.hikabrain.plugin.levels.LevelManager;
import com.hikabrain.plugin.levels.Perk;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.SkullMeta;
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

    /**
     * Entités décoratives temporaires (armor stands invisibles portant une tête de joueur
     * pour l'avantage cosmétique {@link Perk#PARTICLE_HEAD}). Suivies ici pour garantir
     * qu'elles sont bien supprimées même si la partie s'arrête brutalement en plein effet
     * (forceStop, tout le monde quitte...), et non seulement quand leur minuteur se termine.
     */
    private final Set<org.bukkit.entity.ArmorStand> cosmeticArmorStands = new HashSet<>();

    /**
     * Horodatage (ms) de l'entrée d'un joueur dans cette arène (lobby inclus), utilisé
     * pour cumuler le temps de jeu HikaBrain à vie dans les statistiques (voir
     * {@link #flushPlaytime}), consommées ensuite par le classement du site web.
     */
    private final Map<UUID, Long> sessionStartMillis = new HashMap<>();

    // ================= SPECTATEURS =================

    /** Joueurs actuellement en mode spectateur sur cette arène. */
    private final Set<UUID> spectators = new HashSet<>();

    /** Positions des spectateurs avant qu'ils ne rejoignent le mode spectateur (pour restauration). */
    private final Map<UUID, Location> preSpectateLocations = new HashMap<>();

    // ================= SPAWNS FIXES PAR JOUEUR =================

    /**
     * Spawn attribué à chaque joueur au lancement de la partie (2v2, 3v3, 4v4...).
     * Calculé une seule fois dans {@link #assignSpawnsForGame()} au démarrage de la partie,
     * puis réutilisé pour CHAQUE téléportation de ce joueur (début de partie, reset de round,
     * respawn après une mort) afin qu'il garde toujours le même point de spawn durant toute
     * la partie, au lieu d'un tirage aléatoire à chaque fois parmi les spawns de son équipe.
     */
    private final Map<UUID, Location> assignedSpawns = new HashMap<>();

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
        // Ré-évalue l'état de l'arène maintenant que sa configuration est chargée :
        // sans ça, "state" restait bloqué sur sa valeur initiale NOT_CONFIGURED après
        // un redémarrage, ce qui faisait afficher la vitre grise dans le GUI au lieu
        // de la vitre verte, même pour une arène entièrement configurée.
        state = arena.isFullyConfigured() ? GameState.WAITING : GameState.NOT_CONFIGURED;
    }

    /**
     * Copie l'intégralité de la configuration STATIQUE de cette arène (lobby, spectateur,
     * spawns des deux équipes, zones de capture, zone de jeu protégée + son snapshot de
     * blocs, min/max joueurs) vers une autre arène — typiquement une arène fraîchement
     * créée et encore vide — afin de faciliter le déploiement rapide de plusieurs arènes
     * déjà configurées (voir {@link ArenaManager#copy}).
     *
     * IMPORTANT : les coordonnées ne sont pas copiées telles quelles. Elles sont toutes
     * translatées (spawns, zones de capture, zone de jeu, spectateur...) par le même
     * décalage, calculé entre le lobby de CETTE arène (l'ancien point de référence) et
     * {@code newAnchor} (le nouveau point de référence, typiquement la position du joueur
     * qui exécute la commande, debout à l'endroit où il a reconstruit la nouvelle map).
     * Le monde de destination est celui de {@code newAnchor}, ce qui permet aussi de
     * déployer la copie dans un monde différent.
     *
     * La zone de jeu (si définie) est ensuite RE-capturée à son nouvel emplacement (et non
     * simplement copiée telle quelle) : on suppose que la structure a déjà été reconstruite
     * à l'identique au nouvel endroit avant d'exécuter cette copie.
     *
     * Précondition : le lobby de cette arène doit être défini (c'est le point de référence
     * utilisé pour calculer le décalage) — à vérifier par l'appelant (voir ArenaManager#copy).
     *
     * Ne copie PAS l'état d'une partie en cours (scores, joueurs, etc.) : uniquement la
     * configuration de la map elle-même. L'arène cible est ensuite sauvegardée sur disque
     * immédiatement, pour qu'elle survive à un redémarrage du serveur.
     */
    public void copyConfigurationTo(GameManager target, Location newAnchor) {
        Location oldAnchor = this.arena.getLobbySpawn();
        if (oldAnchor == null || newAnchor == null) {
            return;
        }

        double dx = newAnchor.getX() - oldAnchor.getX();
        double dy = newAnchor.getY() - oldAnchor.getY();
        double dz = newAnchor.getZ() - oldAnchor.getZ();
        World newWorld = newAnchor.getWorld();

        Arena src = this.arena;
        Arena dst = target.arena;

        dst.setLobbySpawn(translateLocation(src.getLobbySpawn(), dx, dy, dz, newWorld));
        dst.setSpectatorSpawn(translateLocation(src.getSpectatorSpawn(), dx, dy, dz, newWorld));

        for (Team team : Team.values()) {
            List<Location> spawns = src.getSpawns(team);
            for (int i = 0; i < spawns.size(); i++) {
                dst.setSpawn(team, i + 1, translateLocation(spawns.get(i), dx, dy, dz, newWorld));
            }
        }

        dst.setCaptureZone(Team.RED, translateRegion(src.getCaptureZone(Team.RED), dx, dy, dz, newWorld));
        dst.setCaptureZone(Team.BLUE, translateRegion(src.getCaptureZone(Team.BLUE), dx, dy, dz, newWorld));
        dst.setGameZone(translateRegion(src.getGameZone(), dx, dy, dz, newWorld));

        dst.setMaxPlayers(src.getMaxPlayers());
        dst.setMinPlayers(src.getMinPlayers());

        target.saveArenaConfig();

        // Zone de jeu : on RE-capture les blocs à leur nouvel emplacement (la structure y a
        // déjà été reconstruite à l'identique), plutôt que de réutiliser l'ancien snapshot.
        if (dst.getGameZone() != null) {
            target.captureGameZone();
        }

        target.state = dst.isFullyConfigured() ? GameState.WAITING : GameState.NOT_CONFIGURED;
    }

    /**
     * Translate une localisation du décalage donné, en la replaçant dans le nouveau monde.
     * Conserve le yaw/pitch d'origine. Renvoie null si la localisation d'origine est null
     * (permet de translater proprement des points optionnels non définis).
     */
    private Location translateLocation(Location loc, double dx, double dy, double dz, World newWorld) {
        if (loc == null) return null;
        return new Location(newWorld, loc.getX() + dx, loc.getY() + dy, loc.getZ() + dz, loc.getYaw(), loc.getPitch());
    }

    /**
     * Translate les deux coins d'une zone cuboïde du même décalage. Renvoie null si la
     * zone d'origine est null (zone optionnelle non définie).
     */
    private CuboidRegion translateRegion(CuboidRegion region, double dx, double dy, double dz, World newWorld) {
        if (region == null) return null;
        Location c1 = translateLocation(region.getCorner1(), dx, dy, dz, newWorld);
        Location c2 = translateLocation(region.getCorner2(), dx, dy, dz, newWorld);
        return new CuboidRegion(c1, c2);
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
     * Renvoie le nombre minimum de joueurs effectif pour cette arène : la valeur spécifique
     * configurée via /hb setminplayers si elle existe, sinon le min-players global du config.yml.
     */
    public int getMinPlayers() {
        int specific = arena.getMinPlayers();
        if (specific > 0) {
            return specific;
        }
        return plugin.getConfig().getInt("min-players", 2);
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
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.ENDING
                || state == GameState.STARTING) {
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
        sessionStartMillis.put(player.getUniqueId(), System.currentTimeMillis());

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
        flushPlaytime(uuid, player.getName());

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
                && (playerTeams.size() < getMinPlayers() || !bothTeamsHavePlayers())) {
            cancelCountdown();
        }

        // Si une partie est en cours (ou en préparation) et qu'une équipe se vide totalement, on arrête.
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING) {
            checkForfeit();
        }
    }

    // ================= GESTION DES SPECTATEURS =================

    public boolean isSpectating(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public int getSpectatorCount() {
        return spectators.size();
    }

    /**
     * Renvoie une copie non modifiable des UUID actuellement en spectateur sur cette arène.
     */
    public Set<UUID> getSpectatorUuids() {
        return Collections.unmodifiableSet(new HashSet<>(spectators));
    }

    /**
     * Renvoie tous les joueurs actuellement présents dans cette arène :
     * les joueurs en jeu/lobby ET les spectateurs.
     * Utile pour restreindre la visibilité des messages (chat, morts, kills, ...)
     * aux seules personnes concernées par cette arène.
     */
    public Set<Player> getPresentPlayers() {
        Set<Player> result = new HashSet<>();
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                result.add(player);
            }
        }
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * Calcule le point de téléportation utilisé pour les spectateurs :
     * 1. Le point dédié configuré via /hb setspectatorspawn, s'il existe.
     * 2. Sinon le centre de la zone de jeu (gameZone), s'il existe.
     * 3. Sinon le lobby de l'arène.
     * Peut renvoyer null si rien de tout ça n'est configuré.
     */
    public Location getSpectatorTeleportLocation() {
        if (arena.getSpectatorSpawn() != null) {
            return arena.getSpectatorSpawn().clone();
        }
        if (arena.getGameZone() != null) {
            return arena.getGameZone().getCenter();
        }
        if (arena.getLobbySpawn() != null) {
            return arena.getLobbySpawn().clone();
        }
        return null;
    }

    /**
     * Détermine si une localisation est considérée comme "dans les limites" pour un
     * spectateur de cette arène :
     * - S'il y a une gameZone configurée, le spectateur doit y rester.
     * - Sinon, on retombe sur une limite de distance (config "spectator-max-distance")
     *   autour du point de téléportation spectateur, pour toujours garantir un minimum
     *   de confinement même si l'admin n'a pas défini de zone de jeu précise.
     * - Si on n'a même pas de point de référence, on ne peut rien vérifier : on autorise.
     */
    public boolean isWithinSpectatorBounds(Location loc) {
        CuboidRegion zone = arena.getGameZone();
        if (zone != null) {
            return zone.contains(loc);
        }
        Location center = getSpectatorTeleportLocation();
        if (center == null || center.getWorld() == null || loc.getWorld() == null
                || !center.getWorld().equals(loc.getWorld())) {
            return true;
        }
        double maxDistance = plugin.getConfig().getDouble("spectator-max-distance", 60);
        return center.distanceSquared(loc) <= (maxDistance * maxDistance);
    }

    /**
     * Fait rejoindre un joueur en mode spectateur sur cette arène. Renvoie false si
     * ce n'est pas possible (arène non configurée, joueur déjà engagé ailleurs...).
     */
    public boolean addSpectator(Player player) {
        if (!arena.isFullyConfigured()) {
            MessageUtil.send(player, "&cCette arène n'est pas encore configurée.");
            return false;
        }
        if (isPlaying(player)) {
            MessageUtil.send(player, "&cTu ne peux pas regarder cette partie en spectateur, tu y joues déjà.");
            return false;
        }
        if (isSpectating(player)) {
            MessageUtil.send(player, "&cTu regardes déjà cette partie en spectateur.");
            return false;
        }

        Location teleportTo = getSpectatorTeleportLocation();
        if (teleportTo == null) {
            MessageUtil.send(player, "&cImpossible de spectate cette arène : aucun point de téléportation configuré.");
            return false;
        }

        preSpectateLocations.put(player.getUniqueId(), player.getLocation().clone());
        spectators.add(player.getUniqueId());

        player.teleport(teleportTo);
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        player.getInventory().setItem(KitManager.SPECTATOR_LEAVE_SLOT, KitManager.createSpectatorLeaveItem());

        // Afficher au spectateur le même sidebar de score que les joueurs qu'il observe.
        plugin.getScoreboardManager().showSpectatorScoreboard(player, this);

        MessageUtil.send(player, "&7Tu observes désormais la partie sur l'arène &f" + arenaName
                + "&7. Utilise &f/hb unspectate &7(ou l'item dans ton inventaire) pour repartir.");
        return true;
    }

    /**
     * Fait sortir un joueur du mode spectateur de cette arène et le renvoie à sa
     * position d'avant, ou au lobby de l'arène si indisponible.
     */
    public void removeSpectator(Player player) {
        UUID uuid = player.getUniqueId();
        if (!spectators.remove(uuid)) {
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();

        // Retirer le scoreboard de spectateur
        plugin.getScoreboardManager().removeScoreboard(player);

        Location back = preSpectateLocations.remove(uuid);
        if (back != null) {
            player.teleport(back);
        } else if (arena.getLobbySpawn() != null) {
            player.teleport(arena.getLobbySpawn());
        }

        MessageUtil.send(player, "&7Tu as quitté le mode spectateur.");
    }

    /**
     * Fait sortir tous les spectateurs de cette arène (utilisé quand l'arène est arrêtée
     * de force ou supprimée par un admin).
     */
    private void removeAllSpectators() {
        for (UUID uuid : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player);
            } else {
                spectators.remove(uuid);
                preSpectateLocations.remove(uuid);
            }
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
        
        // Le changement d'équipe n'est permis qu'au lobby, y compris pendant le compte à
        // rebours de démarrage (COUNTDOWN) : une fois les joueurs téléportés dans l'arène
        // (STARTING/PLAYING/ROUND_RESET), il n'est plus possible de changer d'équipe.
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING) {
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

        // Si le changement d'équipe vient de vider complètement une équipe pendant le compte
        // à rebours, la partie ne peut plus démarrer dans ces conditions : on annule.
        if (state == GameState.COUNTDOWN && !bothTeamsHavePlayers()) {
            cancelCountdown();
        }
        
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
        int min = getMinPlayers();
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
        // Le changement d'équipe reste possible pendant ce compte à rebours (voir
        // changePlayerTeam) : on ne ferme donc plus le GUI de sélection ici. Il ne sera
        // fermé qu'au véritable démarrage de la partie, dans startGame().
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

    private BukkitTask startingTask;
    private int startingSecondsLeft;

    /**
     * Première phase du démarrage : téléporte tout le monde dans l'arène et donne le kit
     * complet, mais gèle immédiatement tous les joueurs (même système que
     * {@link #startRoundReset()} : impossible de bouger) le temps d'un court compte à
     * rebours "in-game", avant que la capture ne soit réellement active. Ça laisse aux
     * joueurs le temps de s'orienter une fois sur place, sans qu'un joueur plus rapide
     * puisse déjà courir vers la zone adverse pendant que d'autres finissent de charger.
     */
    private void startGame() {
        state = GameState.STARTING;
        resetScores();
        resetStats();
        arenaSnapshot.restore();
        // Ferme le GUI de sélection d'équipe pour tout joueur qui l'aurait encore ouvert :
        // la partie démarre, il n'est plus question de pouvoir changer d'équipe.
        closeTeamSelectGuiForAll();
        assignSpawnsForGame();
        teleportAllToSpawns();
        applyColoredNames();

        // Mettre à jour le scoreboard pour tous les joueurs
        plugin.getScoreboardManager().onGameStart(this);

        // Geler tout le monde pendant le compte à rebours de démarrage, exactement comme
        // lors d'un round reset après un point marqué.
        freezeAllPlayers();

        if (startingTask != null) {
            startingTask.cancel();
            startingTask = null;
        }
        startingSecondsLeft = plugin.getConfig().getInt("game-start-countdown", 5);

        startingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (startingSecondsLeft <= 0) {
                if (startingTask != null) {
                    startingTask.cancel();
                    startingTask = null;
                }
                beginPlayPhase();
                return;
            }

            broadcast("&eLa partie commence dans &6" + startingSecondsLeft + "&e...");

            // Sons du décompte : tic-tac à partir de 3, identique au round-reset.
            if (startingSecondsLeft <= 3) {
                float pitch = startingSecondsLeft == 1 ? 1.4f : 0.9f;
                playSoundToAll(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            } else {
                playSoundToAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.8f);
            }

            startingSecondsLeft--;
        }, 0L, 20L);
    }

    /**
     * Seconde phase du démarrage : dégèle les joueurs, active réellement la capture et
     * annonce le début de la partie. Appelée automatiquement à la fin du compte à rebours
     * de {@link #startGame()}.
     */
    private void beginPlayPhase() {
        if (state != GameState.STARTING) {
            return;
        }
        unfreezeAllPlayers();
        state = GameState.PLAYING;
        startOffhandReplenishTask();
        startCaptureScheduler();

        broadcast(plugin.getConfig().getString("messages.game-start", ""));
        playSoundToAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1.2f);

        // Avantage cosmétique : nuage de particules "tête" au tout début de la partie
        // (une seule fois par match, pas à chaque round reset).
        playParticleHeadPerkForAll();
    }

    /**
     * Ferme le GUI de sélection d'équipe pour tout joueur du lobby qui l'aurait
     * encore ouvert au moment où la partie démarre.
     */
    private void closeTeamSelectGuiForAll() {
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && TeamSelectGUI.GUI_TITLE.equals(player.getOpenInventory().getTitle())) {
                player.closeInventory();
            }
        }
    }

    /**
     * Attribue à chaque joueur en partie un spawn fixe parmi ceux configurés pour son équipe,
     * pour qu'il garde exactement le même point de spawn du début à la fin de la partie
     * (utile en 2v2/3v3/4v4 où plusieurs spawns sont définis par équipe). La répartition se
     * fait par équipe, dans un ordre stable basé sur l'UUID des joueurs, et boucle sur la liste
     * de spawns si l'équipe compte plus de joueurs que de spawns définis.
     */
    private void assignSpawnsForGame() {
        assignedSpawns.clear();
        for (Team team : Team.values()) {
            List<Location> spawns = arena.getSpawns(team);
            if (spawns.isEmpty()) {
                continue;
            }
            List<UUID> teamPlayers = new ArrayList<>();
            for (Map.Entry<UUID, Team> entry : playerTeams.entrySet()) {
                if (entry.getValue() == team) {
                    teamPlayers.add(entry.getKey());
                }
            }
            teamPlayers.sort(Comparator.comparing(UUID::toString));
            for (int i = 0; i < teamPlayers.size(); i++) {
                Location spawn = spawns.get(i % spawns.size());
                assignedSpawns.put(teamPlayers.get(i), spawn.clone());
            }
        }
    }

    /**
     * Renvoie le spawn fixe attribué à ce joueur pour la partie en cours (voir
     * {@link #assignSpawnsForGame()}). Si aucun spawn n'a été attribué (cas limite,
     * ne devrait pas arriver en cours de partie), retombe sur un spawn aléatoire
     * classique parmi ceux de son équipe.
     */
    public Location getAssignedSpawn(Player player) {
        Location loc = assignedSpawns.get(player.getUniqueId());
        if (loc != null) {
            return loc.clone();
        }
        Team team = playerTeams.get(player.getUniqueId());
        return team != null ? arena.getSpawn(team) : null;
    }

    /**
     * Lance une tâche périodique qui vérifie, pour chaque joueur en partie, qu'il a bien
     * 64 grès lisse en offhand et dans le slot 4 de la hotbar, et complète si besoin (le
     * joueur peut poser/casser les blocs normalement, ils sont juste toujours réapprovisionnés à 64).
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
            player.teleport(getAssignedSpawn(player));
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
            player.teleport(getAssignedSpawn(player));
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
                scorePoint(playerTeam, player);
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
    private Map<UUID, Integer> playerHits = new HashMap<>();
    private Map<UUID, Integer> playerHitsReceived = new HashMap<>();
    private Map<UUID, Integer> playerGoals = new HashMap<>();
    
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
    private void scorePoint(Team scoringTeam, Player scorer) {
        addScore(scoringTeam, 1);
        addPlayerGoal(scorer.getUniqueId());
        plugin.getStatsManager().addPlayerGoal(scorer.getUniqueId(), scorer.getName());
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
        if (state != GameState.PLAYING && state != GameState.ROUND_RESET && state != GameState.STARTING) return;
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
        if (startingTask != null) {
            startingTask.cancel();
            startingTask = null;
        }
        
        stopCaptureScheduler();

        // La partie est terminée : on fait automatiquement sortir tous les spectateurs
        // du mode spectateur (ils sont renvoyés à leur position d'avant).
        removeAllSpectators();

        // Enregistrer la victoire dans les statistiques
        int teamSize = Math.max(getPlayerCountForTeam(Team.RED), getPlayerCountForTeam(Team.BLUE));
        plugin.getStatsManager().addWin(winner, teamSize);

        // Enregistrer les confrontations directes (qui a battu qui), utilisées par le
        // classement de force (voir com.hikabrain.plugin.stats.PowerRankingCalculator) :
        // chaque joueur de l'équipe gagnante "bat" chaque joueur de l'équipe perdante.
        recordHeadToHeadResults(winner);

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

        // Mettre tous les joueurs en spectateur, en listant les membres de chaque équipe
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

        // Titre plein écran de victoire pour tout le monde
        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle(
                    MessageUtil.format(winner.getColoredName() + " &l\uD83C\uDFC6 VICTOIRE !"),
                    MessageUtil.format("&f&oF\u00e9licitations \u00e0 l'\u00e9quipe gagnante !"),
                    10, 60, 20
                );
            }
        }

        // Récapitulatif de fin de partie : vainqueurs + meilleurs performeurs + points de
        // chaque joueur, le tout tenant sur un minimum de lignes pour rester visible sans
        // avoir à remonter le chat.
        announceEndGameSummary(winner, redPlayers, bluePlayers);

        int delay = plugin.getConfig().getInt("restart-delay", 5);

        // Lancer des feux d'artifice aux spawns des joueurs de l'équipe gagnante
        launchVictoryFireworks(winner, delay);
        // Effet cosmétique "étincelles de victoire" pour les joueurs de l'équipe gagnante
        // ayant débloqué et équipé cet avantage (voir Perk.VICTORY_STARS)
        playVictoryStarsPerk(winner);

        Bukkit.getScheduler().runTaskLater(plugin, this::resetToLobby, delay * 20L);
    }

    /**
     * Enregistre, dans {@link com.hikabrain.plugin.stats.HeadToHeadManager}, le résultat
     * de CE match entre chaque joueur de l'équipe gagnante et chaque joueur de l'équipe
     * perdante (chaque gagnant "bat" chaque perdant une fois). C'est la donnée brute à
     * partir de laquelle {@link com.hikabrain.plugin.stats.PowerRankingCalculator} calcule
     * le classement de force (/hb top force, /hb force).
     */
    private void recordHeadToHeadResults(Team winner) {
        Team loser = winner == Team.RED ? Team.BLUE : Team.RED;

        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        for (Map.Entry<UUID, Team> entry : playerTeams.entrySet()) {
            if (entry.getValue() == winner) winners.add(entry.getKey());
            else if (entry.getValue() == loser) losers.add(entry.getKey());
        }
        if (winners.isEmpty() || losers.isEmpty()) return;

        com.hikabrain.plugin.stats.HeadToHeadManager h2h = plugin.getHeadToHeadManager();
        com.hikabrain.plugin.stats.MatchHistoryManager history = plugin.getMatchHistoryManager();

        for (UUID winnerUuid : winners) {
            Player winnerPlayer = Bukkit.getPlayer(winnerUuid);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "?";

            for (UUID loserUuid : losers) {
                Player loserPlayer = Bukkit.getPlayer(loserUuid);
                String loserName = loserPlayer != null ? loserPlayer.getName() : "?";

                h2h.recordResult(winnerUuid, winnerName, loserUuid, loserName);
                history.recordHeadToHead(winnerUuid, winnerName, loserUuid, loserName);
            }
        }
    }

    // ================= POINTS / NIVEAUX DE FIN DE PARTIE =================

    /**
     * Affiche le récapitulatif de fin de partie le plus concis possible :
     *  - une bannière avec l'équipe gagnante + la liste des membres de chaque équipe
     *  - le meilleur frappeur, meilleur tueur et meilleur buteur, TOUTES ÉQUIPES CONFONDUES
     *  - pour chaque joueur, une seule ligne privée avec les points gagnés ce match et son total
     * Le tout tient sur 4-5 lignes de chat maximum (visibles sans remonter), pour que
     * l'information reste lisible immédiatement après la partie.
     *
     * En plus du chat, si un joueur monte de niveau, il reçoit un titre plein écran avec un
     * son de niveau supérieur (et le nom du nouvel avantage débloqué, le cas échéant), plutôt
     * que des lignes de chat supplémentaires.
     */
    private void announceEndGameSummary(Team winner, List<String> redPlayers, List<String> bluePlayers) {
        LevelManager levelManager = plugin.getLevelManager();

        String redList = redPlayers.isEmpty() ? "&8(aucun)" : "&f" + String.join("&7, &f", redPlayers);
        String blueList = bluePlayers.isEmpty() ? "&8(aucun)" : "&f" + String.join("&7, &f", bluePlayers);

        broadcast("&8&m------------------------------------------------------");
        broadcast("&6&l\uD83C\uDFC6 Victoire des " + winner.getColoredName() + "&6&l ! &r&7- &cRouges&7: " + redList + " &8| &9Bleus&7: " + blueList);

        if (levelManager != null && !playerTeams.isEmpty()) {
            List<String> performerFragments = new ArrayList<>();
            addTopPerformerFragment(performerFragments, "&e\u2694", "coups",  this::getPlayerHits);
            addTopPerformerFragment(performerFragments, "&c\u2620", "kills",  this::getPlayerKills);
            addTopPerformerFragment(performerFragments, "&a\u26bd", "buts",   this::getPlayerGoals);
            if (!performerFragments.isEmpty()) {
                broadcast(String.join(" &8| ", performerFragments));
            }
        }
        broadcast("&8&m------------------------------------------------------");

        if (levelManager == null) return;

        for (Map.Entry<UUID, Team> entry : playerTeams.entrySet()) {
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            int hits  = getPlayerHits(uuid);
            int hitsReceived = getPlayerHitsReceived(uuid);
            int kills = getPlayerKills(uuid);
            int goals = getPlayerGoals(uuid);
            int deaths = getPlayerDeaths(uuid);
            boolean won = entry.getValue() == winner;

            int gained = levelManager.computeMatchPoints(hits, kills, goals, won);
            LevelManager.AwardResult result = levelManager.addPoints(uuid, player.getName(), gained);

            plugin.getMatchHistoryManager().recordPlayerMatch(uuid, player.getName(), hits, hitsReceived, kills, deaths, goals, won, gained);

            MessageUtil.send(player, "&d+" + result.pointsGained + " pts &7(\u2694" + hits + " \u2620" + kills + " \u26bd" + goals
                    + (won ? " \uD83C\uDFC6" : "") + ") &7\u2192 &b" + result.totalPoints + " pts &7(Niv." + result.newLevel + ")");

            if (result.leveledUp()) {
                playLevelUpEffect(player, result);
            }
        }
    }

    /**
     * Cherche le joueur ayant le meilleur score pour une statistique donnée, TOUTES ÉQUIPES
     * CONFONDUES, et ajoute un fragment formaté à la liste donnée (rien n'est ajouté si
     * personne n'a de score strictement positif).
     */
    private void addTopPerformerFragment(List<String> fragments, String icon, String unitLabel, java.util.function.Function<UUID, Integer> statGetter) {
        UUID bestUuid = null;
        int bestValue = 0;
        for (UUID uuid : playerTeams.keySet()) {
            int value = statGetter.apply(uuid);
            if (value > bestValue) {
                bestValue = value;
                bestUuid = uuid;
            }
        }
        if (bestUuid == null) return;

        Player player = Bukkit.getPlayer(bestUuid);
        String name = player != null ? player.getName() : "?";
        fragments.add(icon + " &f" + name + " &7(" + bestValue + " " + unitLabel + ")");
    }

    /**
     * Titre plein écran + son sympa affichés quand un joueur monte de niveau, mentionnant
     * au passage le nouvel avantage débloqué le cas échéant (au lieu de lignes de chat en plus).
     */
    private void playLevelUpEffect(Player player, LevelManager.AwardResult result) {
        String title = "&a&l\u25b2 NIVEAU " + result.newLevel + " !";
        String subtitle;
        if (!result.newlyUnlockedPerks.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < result.newlyUnlockedPerks.size(); i++) {
                if (i > 0) names.append("&7, ");
                names.append(result.newlyUnlockedPerks.get(i).getDisplayName());
            }
            subtitle = "&d\u2605 Débloqué: " + names;
        } else {
            subtitle = "&7Continue comme ça !";
        }

        player.sendTitle(MessageUtil.format(title), MessageUtil.format(subtitle), 5, 50, 15);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
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

    /**
     * Cumule à vie (dans les statistiques du site web) le temps passé par ce joueur dans
     * cette arène depuis son entrée (voir {@link #sessionStartMillis}), puis oublie sa
     * session en cours. Ne fait rien si le joueur n'a pas de session enregistrée.
     */
    private void flushPlaytime(UUID uuid, String fallbackName) {
        Long start = sessionStartMillis.remove(uuid);
        if (start == null) return;
        long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - start) / 1000);
        if (elapsedSeconds <= 0) return;
        Player online = Bukkit.getPlayer(uuid);
        String name = online != null ? online.getName() : fallbackName;
        plugin.getStatsManager().addPlayerPlaytime(uuid, name, elapsedSeconds);
    }

    private void resetToLobby() {
        if (offhandReplenishTask != null) {
            offhandReplenishTask.cancel();
            offhandReplenishTask = null;
        }
        // S'assurer que personne n'est encore gelé
        unfreezeAllPlayers();
        // Filet de sécurité : si une partie s'arrête pendant un effet cosmétique en cours
        // (nuage de particules "tête", étincelles de victoire...), on force la suppression
        // des entités décoratives restantes plutôt que de compter uniquement sur leur minuteur.
        clearCosmeticArmorStands();
        // Cumuler le temps de jeu de tout le monde avant de vider playerTeams (sinon la
        // session en cours de chacun serait perdue sans jamais être comptabilisée).
        for (UUID uuid : playerTeams.keySet()) {
            flushPlaytime(uuid, null);
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
        assignedSpawns.clear();
        resetScores();
        state = arena.isFullyConfigured() ? GameState.WAITING : GameState.NOT_CONFIGURED;
    }

    /**
     * Commande admin pour forcer le démarrage immédiat (utile pour les tests).
     * Renvoie false si une partie est déjà en cours ou si une équipe est vide.
     */
    public boolean forceStart() {
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING) return false;
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
        if (startingTask != null) {
            startingTask.cancel();
            startingTask = null;
        }
        unfreezeAllPlayers();
        resetToLobby();
        removeAllSpectators();
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

    // ================= AVANTAGES COSMÉTIQUES (PERKS) =================
    // Ces effets sont purement visuels : ils ne donnent strictement aucun avantage en
    // jeu et ne modifient jamais l'équité d'une partie (voir com.hikabrain.plugin.levels.Perk).

    /**
     * Déclenche, pour chaque joueur ayant débloqué ET équipé l'avantage
     * {@link Perk#PARTICLE_HEAD}, un nuage de particules affichant sa propre tête
     * flottant au-dessus de lui. Appelé une seule fois, au tout début de la partie
     * (voir {@link #beginPlayPhase()}), jamais à chaque round reset.
     */
    private void playParticleHeadPerkForAll() {
        LevelManager levelManager = plugin.getLevelManager();
        if (levelManager == null) return;

        for (UUID uuid : playerTeams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            if (levelManager.getEquippedPerk(uuid) == Perk.PARTICLE_HEAD) {
                playParticleHeadEffect(player);
            }
        }
    }

    /**
     * Fait flotter et tourner un petit nuage de mini-têtes du joueur au-dessus de lui
     * pendant quelques secondes. Purement cosmétique.
     *
     * Note technique : on utilise volontairement des ArmorStand invisibles portant une
     * tête de joueur en "casque", plutôt que la particule Particle.ITEM avec un
     * ItemStack de tête. Cette dernière ne charge pas la texture réelle du skin (le
     * client affiche une texture par défaut, qui peut ressembler à un bloc de terre) et
     * ne garantit pas non plus un arrêt propre. Un ArmorStand affiche systématiquement
     * la vraie tête du joueur, et est un minuteur strict (voir #clearCosmeticArmorStands
     * pour le filet de sécurité en cas d'arrêt brutal de la partie).
     */
    private void playParticleHeadEffect(Player player) {
        ItemStack skull = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            skull.setItemMeta(meta);
        }

        Location spawnLoc = player.getLocation().add(0, 2.3, 0);
        int orbCount = 3;
        List<org.bukkit.entity.ArmorStand> stands = new ArrayList<>(orbCount);
        for (int i = 0; i < orbCount; i++) {
            org.bukkit.entity.ArmorStand stand = spawnLoc.getWorld().spawn(spawnLoc, org.bukkit.entity.ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setSmall(true);
                as.setMarker(true);
                as.setInvulnerable(true);
                as.setBasePlate(false);
                as.setCustomNameVisible(false);
                as.setPersistent(false);
                as.getEquipment().setHelmet(skull.clone());
            });
            stands.add(stand);
            cosmeticArmorStands.add(stand);
        }

        int durationTicks = 60; // 3 secondes
        BukkitTask[] taskHolder = new BukkitTask[1];
        int[] tick = {0};
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean shouldStop = tick[0] >= durationTicks || !player.isOnline();
            if (shouldStop) {
                for (org.bukkit.entity.ArmorStand stand : stands) {
                    if (stand != null && !stand.isDead()) stand.remove();
                    cosmeticArmorStands.remove(stand);
                }
                if (taskHolder[0] != null) taskHolder[0].cancel();
                return;
            }
            double angle = tick[0] * 0.35;
            Location center = player.getLocation().add(0, 2.3, 0);
            for (int i = 0; i < stands.size(); i++) {
                org.bukkit.entity.ArmorStand stand = stands.get(i);
                if (stand == null || stand.isDead()) continue;
                double a = angle + i * (2 * Math.PI / stands.size());
                double dx = Math.cos(a) * 0.6;
                double dz = Math.sin(a) * 0.6;
                double dy = Math.sin(angle * 2) * 0.15;
                stand.teleport(center.clone().add(dx, dy, dz));
            }
            tick[0]++;
        }, 0L, 2L);
    }

    /**
     * Filet de sécurité : force la suppression de toute entité décorative cosmétique
     * encore en vie (voir {@link #playParticleHeadEffect}), utilisé quand une partie
     * s'arrête (normalement ou brutalement) avant la fin naturelle d'un effet.
     */
    private void clearCosmeticArmorStands() {
        for (org.bukkit.entity.ArmorStand stand : cosmeticArmorStands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        cosmeticArmorStands.clear();
    }

    /**
     * Déclenche, pour chaque joueur de l'équipe gagnante ayant débloqué ET équipé
     * l'avantage {@link Perk#VICTORY_STARS}, des étincelles dorées qui tourbillonnent
     * autour de lui pendant l'écran de victoire.
     */
    private void playVictoryStarsPerk(Team winner) {
        LevelManager levelManager = plugin.getLevelManager();
        if (levelManager == null) return;

        for (Map.Entry<UUID, Team> entry : playerTeams.entrySet()) {
            if (entry.getValue() != winner) continue;
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            if (levelManager.getEquippedPerk(uuid) == Perk.VICTORY_STARS) {
                playVictoryStarsEffect(player);
            }
        }
    }

    private void playVictoryStarsEffect(Player player) {
        int durationTicks = 100; // 5 secondes
        BukkitTask[] taskHolder = new BukkitTask[1];
        int[] tick = {0};
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (tick[0] >= durationTicks || !player.isOnline()) {
                if (taskHolder[0] != null) taskHolder[0].cancel();
                return;
            }
            double angle = tick[0] * 0.4;
            Location center = player.getLocation().add(0, 1.1, 0);
            for (int i = 0; i < 3; i++) {
                double a = angle + i * (2 * Math.PI / 3);
                double dx = Math.cos(a) * 0.8;
                double dz = Math.sin(a) * 0.8;
                Location particleLoc = center.clone().add(dx, 0, dz);
                center.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0.01);
            }
            tick[0]++;
        }, 0L, 2L);
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
    public int getPlayerHits(UUID uuid) { return playerHits.getOrDefault(uuid, 0); }
    public int getPlayerHitsReceived(UUID uuid) { return playerHitsReceived.getOrDefault(uuid, 0); }
    public int getPlayerGoals(UUID uuid) { return playerGoals.getOrDefault(uuid, 0); }
    
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

    /**
     * Comptabilise un coup valide porté par ce joueur à un adversaire (utilisé pour le
     * calcul des points de fin de partie, voir {@link #awardEndGamePoints}).
     */
    public void addPlayerHit(UUID uuid) {
        playerHits.put(uuid, playerHits.getOrDefault(uuid, 0) + 1);
    }

    /**
     * Comptabilise un coup valide reçu par ce joueur de la part d'un adversaire (symétrique
     * de {@link #addPlayerHit}, utilisé pour l'historique/les classements par plage de temps).
     */
    public void addPlayerHitReceived(UUID uuid) {
        playerHitsReceived.put(uuid, playerHitsReceived.getOrDefault(uuid, 0) + 1);
    }

    /**
     * Comptabilise un but marqué par ce joueur (utilisé pour le calcul des points
     * de fin de partie).
     */
    public void addPlayerGoal(UUID uuid) {
        playerGoals.put(uuid, playerGoals.getOrDefault(uuid, 0) + 1);
    }
    
    public void resetStats() {
        redKills = 0;
        redDeaths = 0;
        blueKills = 0;
        blueDeaths = 0;
        playerKills.clear();
        playerDeaths.clear();
        playerHits.clear();
        playerHitsReceived.clear();
        playerGoals.clear();
    }
}
