package com.hikabrain.plugin.tournament;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.Team;
import com.hikabrain.plugin.tournament.history.TournamentHistoryManager;
import com.hikabrain.plugin.tournament.hologram.TournamentHologramManager;
import com.hikabrain.plugin.tournament.util.BracketUtil;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moteur central du système de tournoi automatisé : création/inscription, génération
 * du bracket, lancement des matchs (HikaBrain ou duel interne), élimination automatique,
 * gestion des déconnexions, classement final et distribution des récompenses.
 */
public class TournamentManager {

    private final HikaBrainPlugin plugin;
    private final DuelArenaManager duelArenaManager;
    private final TournamentHistoryManager historyManager;
    private final TournamentHologramManager hologramManager;

    private final Map<String, Tournament> tournaments = new LinkedHashMap<>();

    // Arènes actuellement occupées par un match de tournoi (partagées entre tous les tournois).
    private final Set<String> busyDuelArenas = new HashSet<>();
    private final Set<String> busyHikaBrainArenas = new HashSet<>();

    // Suivi "en direct" des matchs duel (1v1/2v2/FFA/Faction) en cours.
    private final Map<UUID, MatchRuntime> runtimeByPlayer = new HashMap<>();
    private final List<MatchRuntime> activeRuntimes = new ArrayList<>();

    // Pour les matchs HikaBrain : quel match/tournoi correspond à quelle arène réservée.
    private final Map<String, Tournament> hikaBrainMatchTournament = new HashMap<>();
    private final Map<String, BracketMatch> hikaBrainMatchByArena = new HashMap<>();

    private BukkitTask retryTask;

    public TournamentManager(HikaBrainPlugin plugin, DuelArenaManager duelArenaManager,
                              TournamentHistoryManager historyManager, TournamentHologramManager hologramManager) {
        this.plugin = plugin;
        this.duelArenaManager = duelArenaManager;
        this.historyManager = historyManager;
        this.hologramManager = hologramManager;

        // Tâche périodique : relance les matchs en attente d'une arène libre.
        this.retryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::retryPendingMatches, 100L, 100L);
    }

    public void shutdown() {
        if (retryTask != null) retryTask.cancel();
        for (MatchRuntime rt : new ArrayList<>(activeRuntimes)) {
            rt.cancelAllTasks();
        }
    }

    public Collection<Tournament> getAll() {
        return tournaments.values();
    }

    public Tournament get(String name) {
        return tournaments.get(name.toLowerCase(Locale.ROOT));
    }

    // ================= CRÉATION / INSCRIPTION =================

    public enum CreateResult {
        OK, ALREADY_EXISTS, INVALID_SIZE, INVALID_ARENA
    }

    public CreateResult create(String name, TournamentFormat format, int teamSize, int maxSlots, int bestOf,
                                int pointsToWin, int timeLimitSeconds, List<String> rules, UUID creator,
                                String linkedHikaBrainArena) {
        String key = name.toLowerCase(Locale.ROOT);
        if (tournaments.containsKey(key)) {
            return CreateResult.ALREADY_EXISTS;
        }
        if (maxSlots < 2 || teamSize < 1) {
            return CreateResult.INVALID_SIZE;
        }
        if (format.isHikaBrainEngine() && linkedHikaBrainArena != null
                && plugin.getArenaManager().get(linkedHikaBrainArena) == null) {
            return CreateResult.INVALID_ARENA;
        }
        Tournament tournament = new Tournament(name, format, teamSize, maxSlots, Math.max(1, bestOf | 1),
                pointsToWin, timeLimitSeconds, rules, creator, linkedHikaBrainArena);
        tournaments.put(key, tournament);
        return CreateResult.OK;
    }

    public enum JoinResult {
        OK, NOT_FOUND, NOT_OPEN, FULL, ALREADY_REGISTERED, TEAM_FULL, TEAM_NAME_REQUIRED
    }

    public JoinResult join(String tournamentName, Player player, String teamTag) {
        Tournament tournament = get(tournamentName);
        if (tournament == null) return JoinResult.NOT_FOUND;
        if (tournament.getState() != TournamentState.REGISTRATION) return JoinResult.NOT_OPEN;
        if (tournament.isPlayerRegistered(player.getUniqueId())) return JoinResult.ALREADY_REGISTERED;

        if (tournament.getTeamSize() == 1) {
            if (tournament.isFull()) return JoinResult.FULL;
            TournamentTeam team = TournamentTeam.soloOf(player.getUniqueId(), player.getName());
            tournament.getRegistered().add(team);
            broadcastToTournament(tournament, "&a" + player.getName() + " &7a rejoint le tournoi &f(" +
                    tournament.getRegistered().size() + "/" + tournament.getMaxSlots() + ")");
            return JoinResult.OK;
        }

        if (teamTag == null || teamTag.isBlank()) {
            return JoinResult.TEAM_NAME_REQUIRED;
        }
        for (TournamentTeam team : tournament.getRegistered()) {
            if (team.getDisplayName().equalsIgnoreCase(teamTag)) {
                if (team.size() >= tournament.getTeamSize()) return JoinResult.TEAM_FULL;
                team.addMember(player.getUniqueId());
                broadcastToTournament(tournament, "&a" + player.getName() + " &7a rejoint l'équipe &f" + team.getDisplayName());
                return JoinResult.OK;
            }
        }
        if (tournament.isFull()) return JoinResult.FULL;
        TournamentTeam newTeam = new TournamentTeam(UUID.randomUUID().toString(), teamTag, player.getUniqueId());
        tournament.getRegistered().add(newTeam);
        broadcastToTournament(tournament, "&a" + player.getName() + " &7a inscrit l'équipe &f" + teamTag + " &7(" +
                tournament.getRegistered().size() + "/" + tournament.getMaxSlots() + ")");
        return JoinResult.OK;
    }

    public boolean leave(String tournamentName, Player player) {
        Tournament tournament = get(tournamentName);
        if (tournament == null) return false;
        if (tournament.getState() != TournamentState.REGISTRATION) return false;
        TournamentTeam team = tournament.findTeamOf(player.getUniqueId());
        if (team == null) return false;
        team.removeMember(player.getUniqueId());
        if (team.size() == 0) {
            tournament.getRegistered().remove(team);
        }
        MessageUtil.send(player, "&cTu as quitté le tournoi &f" + tournament.getName() + "&c.");
        return true;
    }

    public boolean cancel(String tournamentName) {
        Tournament tournament = get(tournamentName);
        if (tournament == null) return false;
        tournament.setState(TournamentState.CANCELLED);
        cleanupTournament(tournament);
        broadcastToTournament(tournament, "&cLe tournoi &f" + tournament.getName() + " &ca été annulé.");
        return true;
    }

    public boolean delete(String tournamentName) {
        Tournament tournament = get(tournamentName);
        if (tournament == null) return false;
        cleanupTournament(tournament);
        tournaments.remove(tournamentName.toLowerCase(Locale.ROOT));
        return true;
    }

    private void cleanupTournament(Tournament tournament) {
        for (MatchRuntime rt : new ArrayList<>(activeRuntimes)) {
            if (rt.getTournament() == tournament) {
                terminateRuntime(rt);
            }
        }
        for (Map.Entry<String, Tournament> e : new HashMap<>(hikaBrainMatchTournament).entrySet()) {
            if (e.getValue() == tournament) {
                GameManager gm = plugin.getArenaManager().get(e.getKey());
                if (gm != null) {
                    gm.forceStop();
                    gm.releaseTournamentReservation();
                }
                busyHikaBrainArenas.remove(e.getKey());
                hikaBrainMatchTournament.remove(e.getKey());
                hikaBrainMatchByArena.remove(e.getKey());
            }
        }
    }

    // ================= DÉMARRAGE / BRACKET =================

    public enum StartResult {
        OK, NOT_FOUND, NOT_ENOUGH_TEAMS, ALREADY_STARTED
    }

    public StartResult start(String tournamentName) {
        Tournament tournament = get(tournamentName);
        if (tournament == null) return StartResult.NOT_FOUND;
        if (tournament.getState() != TournamentState.REGISTRATION) return StartResult.ALREADY_STARTED;
        if (tournament.getRegistered().size() < 2) return StartResult.NOT_ENOUGH_TEAMS;

        tournament.setState(TournamentState.IN_PROGRESS);
        tournament.setStartedAt(System.currentTimeMillis());

        List<BracketMatch> firstRound = BracketUtil.generateFirstRound(
                tournament.getRegistered(), tournament.getFormat().getSlotsPerMatch(), 1);
        tournament.getRounds().add(firstRound);
        tournament.setCurrentRoundIndex(0);

        broadcastToTournament(tournament, "&6&l" + tournament.getName() + " &e- Le bracket a été généré !");
        broadcastToTournament(tournament, "&7Format : &f" + tournament.getFormat().getLabel()
                + " &7| Participants : &f" + tournament.getRegistered().size());

        resolveByesAndLaunch(tournament);
        return StartResult.OK;
    }

    /** Traite tous les BYE du tour courant (qualification automatique), puis lance les vrais matchs. */
    private void resolveByesAndLaunch(Tournament tournament) {
        List<BracketMatch> round = tournament.getCurrentRound();
        if (round == null) return;
        boolean anyRealMatch = false;
        for (BracketMatch match : round) {
            if (match.isBye()) {
                TournamentTeam sole = match.getSoleTeam();
                match.setStatus(MatchStatus.BYE);
                if (sole != null) {
                    match.getQualified().add(sole);
                    broadcastToTournament(tournament, "&7" + sole.getDisplayName() + " &aqualifié(e) automatiquement (bye).");
                }
            } else {
                match.setStatus(MatchStatus.PENDING);
                anyRealMatch = true;
            }
        }
        if (!anyRealMatch) {
            checkRoundComplete(tournament);
            return;
        }
        tryLaunchPendingMatches(tournament);
    }

    private void retryPendingMatches() {
        for (Tournament tournament : tournaments.values()) {
            if (tournament.getState() == TournamentState.IN_PROGRESS) {
                tryLaunchPendingMatches(tournament);
            }
        }
    }

    private void tryLaunchPendingMatches(Tournament tournament) {
        List<BracketMatch> round = tournament.getCurrentRound();
        if (round == null) return;
        for (BracketMatch match : round) {
            if (match.getStatus() == MatchStatus.PENDING) {
                launchMatch(tournament, match);
            }
        }
    }

    // ================= LANCEMENT D'UN MATCH =================

    private void launchMatch(Tournament tournament, BracketMatch match) {
        if (tournament.getFormat().isHikaBrainEngine()) {
            launchHikaBrainMatch(tournament, match);
        } else {
            launchDuelMatch(tournament, match);
        }
    }

    private void launchHikaBrainMatch(Tournament tournament, BracketMatch match) {
        GameManager gm = null;
        if (tournament.getLinkedHikaBrainArena() != null) {
            GameManager candidate = plugin.getArenaManager().get(tournament.getLinkedHikaBrainArena());
            if (candidate != null && !busyHikaBrainArenas.contains(candidate.getName())
                    && candidate.getArena().isFullyConfigured() && candidate.getState() == GameState.WAITING) {
                gm = candidate;
            }
        } else {
            for (GameManager candidate : plugin.getArenaManager().getAll()) {
                if (!busyHikaBrainArenas.contains(candidate.getName())
                        && candidate.getArena().isFullyConfigured()
                        && candidate.getState() == GameState.WAITING) {
                    gm = candidate;
                    break;
                }
            }
        }
        if (gm == null) {
            return; // Aucune arène HikaBrain libre pour l'instant, sera retenté par retryPendingMatches().
        }

        final GameManager arenaGm = gm;
        busyHikaBrainArenas.add(arenaGm.getName());
        arenaGm.reserveForTournament();
        match.setArenaName(arenaGm.getName());
        match.setStatus(MatchStatus.ONGOING);
        match.setStartedAt(System.currentTimeMillis());
        hikaBrainMatchTournament.put(arenaGm.getName(), tournament);
        hikaBrainMatchByArena.put(arenaGm.getName(), match);

        TournamentTeam teamA = match.getSlots().get(0);
        TournamentTeam teamB = match.getSlots().get(1);

        int onlineA = 0, onlineB = 0;
        for (UUID uuid : teamA.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) { arenaGm.addPlayerToTeam(p, Team.RED); onlineA++; }
        }
        for (UUID uuid : teamB.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) { arenaGm.addPlayerToTeam(p, Team.BLUE); onlineB++; }
        }

        if (onlineA == 0 || onlineB == 0) {
            // Un camp est totalement absent au moment du lancement : forfait immédiat.
            TournamentTeam winner = onlineA == 0 ? teamB : teamA;
            broadcastToTournament(tournament, "&c" + (onlineA == 0 ? teamA.getDisplayName() : teamB.getDisplayName())
                    + " &7est déclaré(e) forfait (aucun joueur en ligne).");
            arenaGm.releaseTournamentReservation();
            arenaGm.forceStop();
            busyHikaBrainArenas.remove(arenaGm.getName());
            hikaBrainMatchTournament.remove(arenaGm.getName());
            hikaBrainMatchByArena.remove(arenaGm.getName());
            finishMatch(tournament, match, winner);
            return;
        }

        arenaGm.setTournamentEndCallback(winner -> onHikaBrainMatchEnd(tournament, match, arenaGm, winner));

        broadcastToTournament(tournament, "&e⚔ Match lancé : &f" + match.getDisplayVersus()
                + " &7sur l'arène HikaBrain &f" + arenaGm.getName());
        for (String rule : tournament.getRules()) {
            broadcastMatch(match, "&7Règle : &f" + rule);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arenaGm.getState() == GameState.WAITING || arenaGm.getState() == GameState.COUNTDOWN) {
                arenaGm.forceStart();
            }
        }, 20L);
    }

    private void onHikaBrainMatchEnd(Tournament tournament, BracketMatch match, GameManager gm, Team winnerColor) {
        TournamentTeam teamA = match.getSlots().get(0);
        TournamentTeam teamB = match.getSlots().get(1);
        TournamentTeam winner = winnerColor == Team.RED ? teamA : teamB;

        // Récupérer les kills individuels pour les stats/records avant qu'ils ne soient réinitialisés.
        for (Map.Entry<UUID, Team> entry : gm.getPlayerTeams().entrySet()) {
            int kills = gm.getPlayerKills(entry.getKey());
            if (kills > 0) {
                match.getLiveKills().put(entry.getKey(), kills);
            }
        }

        finishMatch(tournament, match, winner);

        // Libère l'arène une fois l'écran de victoire de l'engin HikaBrain terminé.
        int delay = plugin.getConfig().getInt("restart-delay", 5) + 2;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gm.releaseTournamentReservation();
            busyHikaBrainArenas.remove(gm.getName());
            hikaBrainMatchTournament.remove(gm.getName());
            hikaBrainMatchByArena.remove(gm.getName());
        }, delay * 20L);
    }

    private void launchDuelMatch(Tournament tournament, BracketMatch match) {
        DuelArena arena = duelArenaManager.findFreeArena(match.getSlots().size(), busyDuelArenas);
        if (arena == null) {
            return; // Retenté par retryPendingMatches().
        }
        busyDuelArenas.add(arena.getName());
        match.setArenaName(arena.getName());
        match.setStatus(MatchStatus.ONGOING);
        match.setStartedAt(System.currentTimeMillis());

        MatchRuntime.Mode mode = (tournament.getBestOf() <= 1 && tournament.getPointsToWin() > 0)
                ? MatchRuntime.Mode.POINTS_RACE : MatchRuntime.Mode.ELIMINATION_ROUNDS;
        MatchRuntime rt = new MatchRuntime(tournament, match, arena, mode);
        activeRuntimes.add(rt);
        for (TournamentTeam team : match.getSlots()) {
            if (team == null) continue;
            for (UUID uuid : team.getMembers()) {
                runtimeByPlayer.put(uuid, rt);
            }
        }

        broadcastToTournament(tournament, "&e⚔ Match lancé : &f" + match.getDisplayVersus()
                + " &7sur l'arène &f" + arena.getName());
        for (String rule : tournament.getRules()) {
            broadcastMatch(match, "&7Règle : &f" + rule);
        }

        startRound(rt);
    }

    private void startRound(MatchRuntime rt) {
        BracketMatch match = rt.getMatch();
        DuelArena arena = rt.getArena();
        rt.getAliveBySlot().clear();
        rt.setCurrentRoundStartedAt(System.currentTimeMillis());

        for (int i = 0; i < match.getSlots().size(); i++) {
            TournamentTeam team = match.getSlots().get(i);
            if (team == null) continue;
            Set<UUID> alive = rt.getAlive(i);
            int offset = 0;
            for (UUID uuid : team.getMembers()) {
                if (team.getDisconnected().contains(uuid)) continue; // ne pas (re)spawn un joueur déco
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                Location spawn = arena.getSpawn(i, offset++);
                if (spawn != null) p.teleport(spawn);
                giveDuelKit(p);
                alive.add(uuid);
            }
        }

        broadcastMatch(match, "&a&lLa manche commence !");
        startMatchTimer(rt);

        // Si un ou plusieurs camps sont totalement absents (aucun membre en ligne), la manche
        // peut déjà être conclue immédiatement (forfait) sans attendre une mort.
        checkEliminationCompletion(rt);
    }

    /** Vérifie, en mode élimination, s'il ne reste qu'un seul camp vivant et conclut la manche le cas échéant. */
    private void checkEliminationCompletion(MatchRuntime rt) {
        if (rt.getMode() != MatchRuntime.Mode.ELIMINATION_ROUNDS) return;
        BracketMatch match = rt.getMatch();
        if (match.getStatus() != MatchStatus.ONGOING) return;
        int slotsStillAlive = 0;
        int lastAliveSlot = -1;
        for (int i = 0; i < match.getSlots().size(); i++) {
            if (match.getSlots().get(i) == null) continue;
            if (!rt.getAlive(i).isEmpty()) {
                slotsStillAlive++;
                lastAliveSlot = i;
            }
        }
        if (slotsStillAlive <= 1) {
            onRoundWon(rt, lastAliveSlot);
        }
    }

    private void giveDuelKit(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20);
        player.setFoodLevel(20);
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setHelmet(new ItemStack(Material.IRON_HELMET));
        inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inv.setBoots(new ItemStack(Material.IRON_BOOTS));
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 1);
        inv.addItem(sword);
        inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
    }

    private void startMatchTimer(MatchRuntime rt) {
        Tournament tournament = rt.getTournament();
        int limit = tournament.getTimeLimitSeconds();
        if (limit <= 0) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> onRoundTimeout(rt), limit * 20L);
        rt.setTimeLimitTask(task);
    }

    private void onRoundTimeout(MatchRuntime rt) {
        if (!activeRuntimes.contains(rt)) return;
        BracketMatch match = rt.getMatch();
        if (match.getStatus() != MatchStatus.ONGOING) return;

        if (rt.getMode() == MatchRuntime.Mode.POINTS_RACE) {
            int best = -1, bestSlot = -1;
            for (int i = 0; i < match.getSlots().size(); i++) {
                if (match.getSlots().get(i) == null) continue;
                int score = rt.getSlotScore(i);
                if (score > best) {
                    best = score;
                    bestSlot = i;
                }
            }
            broadcastMatch(match, "&eTemps écoulé ! Décision au score.");
            if (bestSlot >= 0) onMatchWon(rt, bestSlot);
            return;
        }

        // ELIMINATION_ROUNDS : temps écoulé pendant une manche -> décision au nombre de survivants,
        // puis au nombre de kills marqués pendant cette manche.
        int bestSlot = -1;
        int bestAlive = -1;
        int bestKillsThisRound = -1;
        for (int i = 0; i < match.getSlots().size(); i++) {
            if (match.getSlots().get(i) == null) continue;
            int aliveCount = rt.getAlive(i).size();
            if (aliveCount > bestAlive) {
                bestAlive = aliveCount;
                bestSlot = i;
                bestKillsThisRound = -1;
            }
        }
        broadcastMatch(match, "&eTemps écoulé ! La manche est décidée au nombre de survivants.");
        if (bestSlot >= 0) onRoundWon(rt, bestSlot);
    }

    // ================= ÉVÉNEMENTS DE JEU (appelés par TournamentListener) =================

    public MatchRuntime getRuntime(UUID player) {
        return runtimeByPlayer.get(player);
    }

    /** Appelé par le listener sur PlayerDeathEvent quand la victime est engagée dans un match de tournoi duel. */
    public void onDuelDeath(Player victim, Player killer) {
        MatchRuntime rt = runtimeByPlayer.get(victim.getUniqueId());
        if (rt == null) return;
        BracketMatch match = rt.getMatch();
        if (match.getStatus() != MatchStatus.ONGOING) return;

        int victimSlot = rt.slotIndexOf(victim.getUniqueId());
        if (victimSlot < 0) return;

        if (killer != null) {
            int killerSlot = rt.slotIndexOf(killer.getUniqueId());
            if (killerSlot >= 0 && killerSlot != victimSlot) {
                match.addLiveKill(killer.getUniqueId());
                TournamentTeam killerTeam = match.getSlots().get(killerSlot);
                if (killerTeam != null) killerTeam.addKills(1);
                rt.addSlotScore(killerSlot, 1);
            }
        }

        if (rt.getMode() == MatchRuntime.Mode.POINTS_RACE) {
            // Respawn direct après un court délai, la manche continue.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!activeRuntimes.contains(rt) || match.getStatus() != MatchStatus.ONGOING) return;
                Player p = Bukkit.getPlayer(victim.getUniqueId());
                if (p == null) return;
                Location spawn = rt.getArena().getSpawn(victimSlot, 0);
                if (spawn != null) p.teleport(spawn);
                giveDuelKit(p);
            }, 40L);

            for (int i = 0; i < match.getSlots().size(); i++) {
                if (match.getSlots().get(i) == null) continue;
                if (rt.getSlotScore(i) >= rt.getTournament().getPointsToWin()) {
                    onMatchWon(rt, i);
                    return;
                }
            }
            return;
        }

        // ELIMINATION_ROUNDS : le joueur qui meurt est éliminé de la manche en cours.
        rt.getAlive(victimSlot).remove(victim.getUniqueId());
        victim.setGameMode(GameMode.SPECTATOR);
        Location spectatorSpawn = rt.getArena().getSpectatorSpawn() != null
                ? rt.getArena().getSpectatorSpawn() : rt.getArena().getWaitingSpawn();
        if (spectatorSpawn != null) victim.teleport(spectatorSpawn);

        checkEliminationCompletion(rt);
    }

    private void onRoundWon(MatchRuntime rt, int winningSlot) {
        if (rt.getTimeLimitTask() != null) {
            rt.getTimeLimitTask().cancel();
            rt.setTimeLimitTask(null);
        }
        BracketMatch match = rt.getMatch();
        if (winningSlot < 0) {
            // Aucun survivant des deux côtés (KO simultané) : on relance simplement la manche.
            broadcastMatch(match, "&7Manche nulle, on relance !");
            Bukkit.getScheduler().runTaskLater(plugin, () -> startRound(rt), 60L);
            return;
        }
        match.addRoundWin(winningSlot);
        TournamentTeam winningTeam = match.getSlots().get(winningSlot);
        broadcastMatch(match, "&6&l" + (winningTeam != null ? winningTeam.getDisplayName() : "?") + " &eremporte la manche ! &7(" +
                match.getRoundsWon(winningSlot) + "/" + rt.getTournament().getRoundsNeededToWinMatch() + ")");

        if (match.getRoundsWon(winningSlot) >= rt.getTournament().getRoundsNeededToWinMatch()) {
            onMatchWon(rt, winningSlot);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> startRound(rt), 100L);
        }
    }

    private void onMatchWon(MatchRuntime rt, int winningSlot) {
        BracketMatch match = rt.getMatch();
        if (match.getStatus() != MatchStatus.ONGOING) return;
        if (rt.getTimeLimitTask() != null) {
            rt.getTimeLimitTask().cancel();
            rt.setTimeLimitTask(null);
        }
        TournamentTeam winner = match.getSlots().get(winningSlot);
        finishMatch(rt.getTournament(), match, winner);

        // Remise en spectateur/attente + libération de l'arène.
        DuelArena arena = rt.getArena();
        for (TournamentTeam team : match.getSlots()) {
            if (team == null) continue;
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                p.setGameMode(GameMode.SURVIVAL);
                p.getInventory().clear();
                Location waiting = arena.getWaitingSpawn();
                if (waiting != null) p.teleport(waiting);
            }
        }
        terminateRuntime(rt);
        busyDuelArenas.remove(arena.getName());
    }

    private void terminateRuntime(MatchRuntime rt) {
        rt.cancelAllTasks();
        for (TournamentTeam team : rt.getMatch().getSlots()) {
            if (team == null) continue;
            for (UUID uuid : team.getMembers()) {
                runtimeByPlayer.remove(uuid);
            }
        }
        activeRuntimes.remove(rt);
    }

    // ================= FIN DE MATCH / PROGRESSION DU BRACKET =================

    private void finishMatch(Tournament tournament, BracketMatch match, TournamentTeam winner) {
        match.setStatus(MatchStatus.FINISHED);
        match.setFinishedAt(System.currentTimeMillis());
        match.getQualified().add(winner);
        winner.incrementMatchesWon();

        List<BracketMatch> round = tournament.getCurrentRound();
        boolean wasFinal = round != null && round.size() == 1;
        boolean wasSemifinal = round != null && round.size() == 2;

        TournamentTeam loser = null;
        for (TournamentTeam t : match.getSlots()) {
            if (t != null && t != winner) {
                loser = t;
                t.setEliminated(true);
            }
        }

        if (wasFinal) {
            tournament.setChampion(winner);
            tournament.setRunnerUp(loser);
            onTournamentFinished(tournament);
        } else if (wasSemifinal && loser != null) {
            tournament.getThirdPlace().add(loser);
        }

        checkRoundComplete(tournament);
    }

    private void checkRoundComplete(Tournament tournament) {
        if (tournament.getState() == TournamentState.FINISHED) return;
        List<BracketMatch> round = tournament.getCurrentRound();
        if (round == null) return;
        for (BracketMatch m : round) {
            if (m.getStatus() != MatchStatus.FINISHED && m.getStatus() != MatchStatus.BYE) {
                return; // Il reste des matchs en cours dans ce tour.
            }
        }

        List<TournamentTeam> qualified = new ArrayList<>();
        for (BracketMatch m : round) {
            qualified.addAll(m.getQualified());
        }
        if (qualified.size() <= 1) {
            return; // Le tournoi est déjà terminé (géré par finishMatch -> onTournamentFinished).
        }

        int nextRoundNumber = tournament.getCurrentRoundIndex() + 2;
        List<BracketMatch> nextRound = BracketUtil.generateNextRound(
                qualified, nextRoundNumber, tournament.getFormat().getSlotsPerMatch(), 1);
        tournament.getRounds().add(nextRound);
        tournament.setCurrentRoundIndex(tournament.getCurrentRoundIndex() + 1);

        String roundName = BracketUtil.roundName(nextRound.size(), nextRound.size() == 1);
        broadcastToTournament(tournament, "&6&l" + roundName + " &e- " + nextRound.size() + " match(s) à venir !");

        resolveByesAndLaunch(tournament);
    }

    private void onTournamentFinished(Tournament tournament) {
        tournament.setState(TournamentState.FINISHED);
        tournament.setFinishedAt(System.currentTimeMillis());

        broadcastToTournament(tournament, "&6&l===============================");
        broadcastToTournament(tournament, "&6&l🏆 Champion : &f" + safeName(tournament.getChampion()));
        if (tournament.getRunnerUp() != null) {
            broadcastToTournament(tournament, "&f🥈 " + safeName(tournament.getRunnerUp()));
        }
        for (TournamentTeam t : tournament.getThirdPlace()) {
            broadcastToTournament(tournament, "&c🥉 " + safeName(t));
        }
        broadcastToTournament(tournament, "&6&l===============================");

        distributeRewards(tournament);
        recordHistory(tournament);
        hologramManager.display(tournament);
    }

    private String safeName(TournamentTeam team) {
        return team == null ? "?" : team.getDisplayName();
    }

    private void distributeRewards(Tournament tournament) {
        runRewardCommands("champion", tournament.getChampion());
        runRewardCommands("runner-up", tournament.getRunnerUp());
        for (TournamentTeam t : tournament.getThirdPlace()) {
            runRewardCommands("third-place", t);
        }
    }

    private void runRewardCommands(String key, TournamentTeam team) {
        if (team == null) return;
        List<String> commands = plugin.getConfig().getStringList("tournament.rewards." + key);
        for (UUID uuid : team.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            String playerName = p != null ? p.getName() : uuid.toString();
            for (String cmd : commands) {
                String finalCmd = cmd.replace("%player%", playerName);
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
            }
        }
    }

    private void recordHistory(Tournament tournament) {
        long totalDuration = 0;
        int matchCount = 0;
        Map<UUID, Integer> killsByPlayer = new HashMap<>();
        Map<UUID, String> namesByPlayer = new HashMap<>();
        for (List<BracketMatch> round : tournament.getRounds()) {
            for (BracketMatch m : round) {
                if (m.getFinishedAt() > 0 && m.getStartedAt() > 0) {
                    totalDuration += (m.getFinishedAt() - m.getStartedAt());
                    matchCount++;
                }
                for (Map.Entry<UUID, Integer> e : m.getLiveKills().entrySet()) {
                    killsByPlayer.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        for (TournamentTeam t : tournament.getRegistered()) {
            for (UUID uuid : t.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                namesByPlayer.put(uuid, p != null ? p.getName() : uuid.toString());
            }
        }
        double avgSeconds = matchCount > 0 ? (totalDuration / 1000.0) / matchCount : 0;
        historyManager.recordFinishedTournament(tournament, avgSeconds, killsByPlayer, namesByPlayer);
    }

    // ================= DÉCONNEXIONS =================

    public void onPlayerQuit(Player player) {
        MatchRuntime rt = runtimeByPlayer.get(player.getUniqueId());
        if (rt == null) return;
        BracketMatch match = rt.getMatch();
        int slotIndex = rt.slotIndexOf(player.getUniqueId());
        if (slotIndex < 0) return;
        TournamentTeam team = match.getSlots().get(slotIndex);
        if (team == null) return;

        team.markDisconnected(player.getUniqueId());
        rt.getAlive(slotIndex).remove(player.getUniqueId());

        int graceSeconds = plugin.getConfig().getInt("tournament.disconnect-grace-seconds", 60);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getStatus() != MatchStatus.ONGOING) return;
            if (team.isFullyDisconnected()) {
                // Forfait : l'autre camp gagne directement le match.
                for (int i = 0; i < match.getSlots().size(); i++) {
                    if (match.getSlots().get(i) != null && match.getSlots().get(i) != team) {
                        broadcastMatch(match, "&c" + team.getDisplayName() + " &7a été déclaré(e) forfait (déconnexion).");
                        onMatchWon(rt, i);
                        return;
                    }
                }
            }
        }, graceSeconds * 20L);
        rt.getDisconnectGraceTasks().put(player.getUniqueId(), task);

        // Recompte des slots encore vivants au cas où ce départ suffit à conclure la manche en cours.
        if (rt.getMode() == MatchRuntime.Mode.ELIMINATION_ROUNDS) {
            checkEliminationCompletion(rt);
        }
    }

    public void onPlayerJoin(Player player) {
        MatchRuntime rt = runtimeByPlayer.get(player.getUniqueId());
        if (rt == null) return;
        BracketMatch match = rt.getMatch();
        int slotIndex = rt.slotIndexOf(player.getUniqueId());
        if (slotIndex < 0) return;
        TournamentTeam team = match.getSlots().get(slotIndex);
        if (team == null) return;

        team.markReconnected(player.getUniqueId());
        BukkitTask graceTask = rt.getDisconnectGraceTasks().remove(player.getUniqueId());
        if (graceTask != null) graceTask.cancel();

        if (match.getStatus() == MatchStatus.ONGOING) {
            Location spawn = rt.getArena().getSpawn(slotIndex, 0);
            if (spawn != null) player.teleport(spawn);
            giveDuelKit(player);
            rt.getAlive(slotIndex).add(player.getUniqueId());
            MessageUtil.send(player, "&aTu as rejoint de nouveau ton match de tournoi !");
        }
    }

    // ================= SPECTATEUR =================

    public boolean spectate(String tournamentName, Player player) {
        Tournament tournament = get(tournamentName);
        if (tournament == null || tournament.getState() != TournamentState.IN_PROGRESS) return false;

        Location target = null;
        List<BracketMatch> round = tournament.getCurrentRound();
        if (round != null) {
            for (BracketMatch m : round) {
                if (m.getStatus() == MatchStatus.ONGOING) {
                    if (tournament.getFormat().isHikaBrainEngine()) {
                        GameManager gm = plugin.getArenaManager().get(m.getArenaName());
                        if (gm != null) target = gm.getArena().getLobbySpawn();
                    } else {
                        DuelArena arena = duelArenaManager.get(m.getArenaName());
                        if (arena != null) {
                            target = arena.getSpectatorSpawn() != null ? arena.getSpectatorSpawn() : arena.getWaitingSpawn();
                        }
                    }
                    if (target != null) break;
                }
            }
        }
        if (target == null) return false;

        tournament.getSpectators().add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(target);
        MessageUtil.send(player, "&7Tu observes maintenant le tournoi &f" + tournament.getName() + "&7.");
        return true;
    }

    public void unspectate(Player player) {
        for (Tournament tournament : tournaments.values()) {
            tournament.getSpectators().remove(player.getUniqueId());
        }
        player.setGameMode(GameMode.SURVIVAL);
    }

    // ================= AFFICHAGE =================

    public List<String> describeOngoingMatches(Tournament tournament) {
        List<String> lines = new ArrayList<>();
        List<BracketMatch> round = tournament.getCurrentRound();
        if (round == null) return lines;
        for (BracketMatch m : round) {
            String status;
            switch (m.getStatus()) {
                case ONGOING: status = "&aEn cours"; break;
                case PENDING: status = "&eEn attente d'arène"; break;
                case FINISHED: status = "&7Terminé"; break;
                case BYE: status = "&7Bye"; break;
                default: status = "&7En attente d'adversaires"; break;
            }
            String arenaInfo = m.getArenaName() != null ? " &8(" + m.getArenaName() + ")" : "";
            lines.add(status + " &f- " + m.getDisplayVersus() + arenaInfo);
        }
        return lines;
    }

    private void broadcastToTournament(Tournament tournament, String rawMessage) {
        String message = MessageUtil.format("&8[&6Tournoi&8] &r" + rawMessage);
        for (TournamentTeam team : tournament.getRegistered()) {
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(message);
            }
        }
        for (UUID uuid : tournament.getSpectators()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    private void broadcastMatch(BracketMatch match, String rawMessage) {
        String message = MessageUtil.format("&8[&6Tournoi&8] &r" + rawMessage);
        for (TournamentTeam team : match.getSlots()) {
            if (team == null) continue;
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(message);
            }
        }
    }
}
