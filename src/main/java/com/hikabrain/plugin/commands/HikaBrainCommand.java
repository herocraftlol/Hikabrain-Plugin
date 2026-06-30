package com.hikabrain.plugin.commands;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.Arena;
import com.hikabrain.plugin.game.ArenaManager;
import com.hikabrain.plugin.game.CuboidRegion;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.Team;
import com.hikabrain.plugin.hologram.CategoryLeaderboardManager;
import com.hikabrain.plugin.hologram.StatsHologramManager;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Gère la commande /hb et tous ses sous-arguments.
 *
 * Le plugin gère plusieurs arènes HikaBrain nommées et indépendantes ; presque toutes
 * les commandes (autres que join/leave/list) attendent donc un nom d'arène en paramètre :
 *   /hb create <nom>                          - crée une nouvelle arène vide
 *   /hb delete <nom>                          - supprime une arène
 *   /hb list                                  - liste toutes les arènes
 *   /hb setlobby <nom>                        - définit le lobby (à ta position)
 *   /hb setspawn <nom> <red|blue> <index>      - définit/remplace le spawn #index d'une équipe
 *   /hb delspawn <nom> <red|blue> <index>      - supprime le spawn #index d'une équipe
 *   /hb setcapture <nom> <red|blue> <pos1|pos2> - définit une zone de capture
 *   /hb setgamezone <nom> <pos1|pos2>          - définit + capture la zone de jeu protégée
 *   /hb join <nom>                            - rejoindre une arène
 *   /hb joinrandom                            - rejoindre une arène au hasard (priorité à celles déjà occupées)
 *   /hb leave                                 - quitter l'arène en cours
 *   /hb start <nom>                           - forcer le démarrage
 *   /hb stop <nom>                            - forcer l'arrêt
 *   /hb info <nom>                            - infos sur une arène
 *
 * Pour la sélection des coins de zone, on utilise une astuce simple en deux étapes :
 * pos1 enregistre le premier coin à la position du joueur, pos2 enregistre le second
 * et finalise la zone.
 */
public class HikaBrainCommand implements CommandExecutor, TabCompleter {

    private final HikaBrainPlugin plugin;

    // Stocke temporairement le coin 1 d'une zone de capture en attendant le coin 2,
    // par couple (nom d'arène + équipe).
    private final Map<String, Location> pendingCaptureCorner1 = new HashMap<>();

    // Stocke temporairement le coin 1 de la zone de jeu globale, par nom d'arène.
    private final Map<String, Location> pendingGameZoneCorner1 = new HashMap<>();

    public HikaBrainCommand(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "setlobby" -> handleSetLobby(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "delspawn" -> handleDelSpawn(sender, args);
            case "setcapture" -> handleSetCapture(sender, args);
            case "setgamezone" -> handleSetGameZone(sender, args);
            case "join" -> handleJoin(sender, args);
            case "joinrandom" -> handleJoinRandom(sender);
            case "arenas" -> handleArenasGui(sender);
            case "leave" -> handleLeave(sender);
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            case "info" -> handleInfo(sender, args);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            case "resetstats" -> handleResetStats(sender);
            case "holostats" -> handleHoloStats(sender);
            case "holoremove" -> handleHoloRemove(sender);
            case "leaderboard" -> handleLeaderboard(sender, args);
            // Scoreboard commands
            case "setsbserver" -> handleSetSbServer(sender, args);
            case "setsbgame" -> handleSetSbGame(sender, args);
            case "setsbtitle" -> handleSetSbTitle(sender, args);
            case "setsblines" -> handleSetSbLines(sender, args);
            case "reloadsb" -> handleReloadSb(sender);
            case "sbinfo" -> handleSbInfo(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ================= GESTION DES ARÈNES (ADMIN) =================

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb create <nom>");
            return;
        }
        String name = args[1];
        boolean created = plugin.getArenaManager().create(name);
        MessageUtil.send(sender, created
                ? "&aArène '" + name + "' créée. Configure-la avec /hb setlobby " + name + ", etc."
                : "&cUne arène '" + name + "' existe déjà.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb delete <nom>");
            return;
        }
        String name = args[1];
        boolean deleted = plugin.getArenaManager().delete(name);
        MessageUtil.send(sender, deleted ? "&aArène '" + name + "' supprimée." : "&cAucune arène '" + name + "' trouvée.");
    }

    private void handleList(CommandSender sender) {
        ArenaManager am = plugin.getArenaManager();
        Set<String> names = am.getNames();
        if (names.isEmpty()) {
            MessageUtil.send(sender, "&7Aucune arène configurée. Utilise /hb create <nom> pour en créer une.");
            return;
        }
        MessageUtil.send(sender, "&8&m----------&r &bArènes HikaBrain &8&m----------");
        for (String name : names) {
            GameManager gm = am.get(name);
            MessageUtil.send(sender, "&e" + name + " &7- État: &f" + gm.getState()
                    + " &7- Joueurs: &f" + gm.getPlayerCount()
                    + " &7- Configurée: " + (gm.getArena().isFullyConfigured() ? "&aOui" : "&cNon"));
        }
    }

    // ================= SETUP (ADMIN) =================

    /**
     * Récupère l'arène désignée par args[nameIndex] et envoie un message d'erreur si elle
     * n'existe pas. Renvoie null si l'arène n'existe pas (l'appelant doit alors arrêter le traitement).
     */
    private GameManager resolveArena(CommandSender sender, String[] args, int nameIndex) {
        if (args.length <= nameIndex) {
            return null;
        }
        GameManager gm = plugin.getArenaManager().get(args[nameIndex]);
        if (gm == null) {
            MessageUtil.send(sender, "&cAucune arène nommée '" + args[nameIndex] + "' n'existe. Utilise /hb create " + args[nameIndex] + " d'abord.");
        }
        return gm;
    }

    private void handleSetLobby(CommandSender sender, String[] args) {
        if (!checkAdminAndPlayer(sender)) return;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb setlobby <nom>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;
        Player player = (Player) sender;

        gm.getArena().setLobbySpawn(player.getLocation());
        gm.saveArenaConfig();

        MessageUtil.send(sender, "&aLe point de lobby de '" + args[1] + "' a été défini à ta position.");
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!checkAdminAndPlayer(sender)) return;
        if (args.length < 4) {
            MessageUtil.send(sender, "&cUsage: /hb setspawn <nom> <red|blue> <index>");
            MessageUtil.send(sender, "&7L'index commence à 1. Utilise le prochain index disponible pour ajouter un nouveau spawn.");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;
        Player player = (Player) sender;

        Team team = parseTeam(args[2]);
        if (team == null) {
            MessageUtil.send(sender, "&cÉquipe invalide. Utilise 'red' ou 'blue'.");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "&cL'index doit être un nombre entier (ex: 1, 2, 3...).");
            return;
        }

        int nextAvailable = gm.getArena().getSpawnCount(team) + 1;
        boolean ok = gm.getArena().setSpawn(team, index, player.getLocation());
        if (!ok) {
            MessageUtil.send(sender, "&cIndex invalide. Le prochain index disponible pour cette équipe est &7" + nextAvailable + "&c.");
            return;
        }
        gm.saveArenaConfig();

        boolean wasReplaced = index <= (nextAvailable - 1);
        MessageUtil.send(sender, "&aLe spawn &7#" + index + " &ade l'équipe " + team.getColoredName()
                + " &asur '" + args[1] + "' a été " + (wasReplaced ? "remplacé" : "défini") + " à ta position. &7(" + gm.getArena().getSpawnCount(team) + " spawn(s) au total pour cette équipe)");
    }

    private void handleDelSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 4) {
            MessageUtil.send(sender, "&cUsage: /hb delspawn <nom> <red|blue> <index>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;

        Team team = parseTeam(args[2]);
        if (team == null) {
            MessageUtil.send(sender, "&cÉquipe invalide. Utilise 'red' ou 'blue'.");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "&cL'index doit être un nombre entier (ex: 1, 2, 3...).");
            return;
        }

        boolean ok = gm.getArena().removeSpawn(team, index);
        if (!ok) {
            MessageUtil.send(sender, "&cIndex invalide. Cette équipe a actuellement &7" + gm.getArena().getSpawnCount(team) + " &cspawn(s).");
            return;
        }
        gm.saveArenaConfig();
        MessageUtil.send(sender, "&aSpawn &7#" + index + " &ade l'équipe " + team.getColoredName() + " &asupprimé sur '" + args[1] + "'.");
    }

    private void handleSetCapture(CommandSender sender, String[] args) {
        if (!checkAdminAndPlayer(sender)) return;
        if (args.length < 4) {
            MessageUtil.send(sender, "&cUsage: /hb setcapture <nom> <red|blue> <pos1|pos2>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;
        Player player = (Player) sender;

        Team team = parseTeam(args[2]);
        if (team == null) {
            MessageUtil.send(sender, "&cÉquipe invalide. Utilise 'red' ou 'blue'.");
            return;
        }

        String posArg = args[3].toLowerCase(Locale.ROOT);
        String pendingKey = args[1].toLowerCase(Locale.ROOT) + ":" + team.name();

        if (posArg.equals("pos1")) {
            pendingCaptureCorner1.put(pendingKey, player.getTargetBlock(null, 5).getLocation());
            MessageUtil.send(sender, "&aCoin 1 de la zone de capture &7" + team.getColoredName()
                    + "&a enregistré. Place-toi au coin opposé et fais &7/hb setcapture " + args[1] + " " + args[2] + " pos2");
        } else if (posArg.equals("pos2")) {
            Location corner1 = pendingCaptureCorner1.get(pendingKey);
            if (corner1 == null) {
                MessageUtil.send(sender, "&cTu dois d'abord définir le coin 1 avec /hb setcapture " + args[1] + " " + args[2] + " pos1");
                return;
            }
            Location corner2 = player.getTargetBlock(null, 5).getLocation();
            if (corner1.getWorld() == null || !corner1.getWorld().equals(corner2.getWorld())) {
                MessageUtil.send(sender, "&cLes deux coins doivent être dans le même monde !");
                return;
            }

            CuboidRegion region = new CuboidRegion(corner1, corner2);
            gm.getArena().setCaptureZone(team, region);
            pendingCaptureCorner1.remove(pendingKey);
            gm.saveArenaConfig();

            MessageUtil.send(sender, "&aZone de capture de l'équipe " + team.getColoredName() + " &asur '" + args[1] + "' définie avec succès !");
        } else {
            MessageUtil.send(sender, "&cUsage: /hb setcapture <nom> <red|blue> <pos1|pos2>");
        }
    }

    /**
     * Définit la zone de jeu globale (protection des blocs + restauration de la map).
     * Une fois les deux coins posés, capture immédiatement un snapshot de tous les blocs
     * actuellement présents dans la zone : c'est cet état qui sera restauré à chaque round.
     */
    private void handleSetGameZone(CommandSender sender, String[] args) {
        if (!checkAdminAndPlayer(sender)) return;
        if (args.length < 3) {
            MessageUtil.send(sender, "&cUsage: /hb setgamezone <nom> <pos1|pos2>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;
        Player player = (Player) sender;

        String posArg = args[2].toLowerCase(Locale.ROOT);
        String arenaKey = args[1].toLowerCase(Locale.ROOT);

        if (posArg.equals("pos1")) {
            pendingGameZoneCorner1.put(arenaKey, player.getTargetBlock(null, 5).getLocation());
            MessageUtil.send(sender, "&aCoin 1 de la zone de jeu enregistré. Place-toi au coin opposé et fais &7/hb setgamezone " + args[1] + " pos2");
        } else if (posArg.equals("pos2")) {
            Location corner1 = pendingGameZoneCorner1.get(arenaKey);
            if (corner1 == null) {
                MessageUtil.send(sender, "&cTu dois d'abord définir le coin 1 avec /hb setgamezone " + args[1] + " pos1");
                return;
            }
            Location corner2 = player.getTargetBlock(null, 5).getLocation();
            if (corner1.getWorld() == null || !corner1.getWorld().equals(corner2.getWorld())) {
                MessageUtil.send(sender, "&cLes deux coins doivent être dans le même monde !");
                return;
            }

            CuboidRegion region = new CuboidRegion(corner1, corner2);
            gm.getArena().setGameZone(region);
            pendingGameZoneCorner1.remove(arenaKey);
            gm.saveArenaConfig();

            MessageUtil.send(sender, "&aZone de jeu définie ! Capture du snapshot de la map en cours...");
            gm.captureGameZone();
            MessageUtil.send(sender, "&aSnapshot de la map capturé avec succès. Elle sera restaurée à chaque round.");
        } else {
            MessageUtil.send(sender, "&cUsage: /hb setgamezone <nom> <pos1|pos2>");
        }
    }

    // ================= JOUEUR =================

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb join <nom>");
            return;
        }
        ArenaManager am = plugin.getArenaManager();
        GameManager gm = am.get(args[1]);
        if (gm == null) {
            MessageUtil.send(sender, "&cAucune arène nommée '" + args[1] + "' n'existe.");
            return;
        }

        GameManager current = am.findArenaOf(player);
        if (current != null) {
            MessageUtil.send(sender, "&cTu es déjà dans une partie (" + current.getName() + "). Fais /hb leave d'abord.");
            return;
        }

        gm.addPlayer(player);
    }

    /**
     * Rejoint automatiquement la "meilleure" arène disponible :
     * - en priorité une arène qui a déjà des joueurs en attente (pour la compléter,
     *   typiquement pour finir un 1v1 ou rejoindre une partie qui se remplit), en
     *   choisissant celle qui en a le plus, et au hasard en cas d'égalité ;
     * - sinon, une arène vide tirée au hasard parmi celles disponibles.
     */
    private void handleJoinRandom(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }
        ArenaManager am = plugin.getArenaManager();

        GameManager current = am.findArenaOf(player);
        if (current != null) {
            MessageUtil.send(sender, "&cTu es déjà dans une partie (" + current.getName() + "). Fais /hb leave d'abord.");
            return;
        }

        GameManager gm = am.findBestArenaForRandomJoin();
        if (gm == null) {
            MessageUtil.send(sender, "&cAucune arène disponible pour le moment. Réessaie plus tard.");
            return;
        }

        gm.addPlayer(player);
    }

    private void handleArenasGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }
        plugin.getArenaGUI().open(player);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }
        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        if (gm == null) {
            MessageUtil.send(sender, "&cTu n'es pas dans une partie.");
            return;
        }
        gm.removePlayer(player);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        ArenaManager am = plugin.getArenaManager();

        if (args.length < 2) {
            // Sans nom précisé : si le joueur est en partie, affiche son arène actuelle.
            if (sender instanceof Player player) {
                GameManager current = am.findArenaOf(player);
                if (current != null) {
                    printArenaInfo(sender, current);
                    return;
                }
            }
            MessageUtil.send(sender, "&cUsage: /hb info <nom>");
            return;
        }

        GameManager gm = am.get(args[1]);
        if (gm == null) {
            MessageUtil.send(sender, "&cAucune arène nommée '" + args[1] + "' n'existe.");
            return;
        }
        printArenaInfo(sender, gm);
    }

    private void printArenaInfo(CommandSender sender, GameManager gm) {
        Arena arena = gm.getArena();
        MessageUtil.send(sender, "&8&m----------&r &bHikaBrain: " + gm.getName() + " &8&m----------");
        MessageUtil.send(sender, "&7État : &f" + gm.getState());
        MessageUtil.send(sender, "&7Joueurs en partie : &f" + gm.getPlayerCount());
        MessageUtil.send(sender, "&7Map configurée : " + (arena.isFullyConfigured() ? "&aOui" : "&cNon"));
        MessageUtil.send(sender, "&7Spawns rouges : &c" + arena.getSpawnCount(Team.RED) + " &7/ Spawns bleus : &9" + arena.getSpawnCount(Team.BLUE));
        MessageUtil.send(sender, "&7Zone de jeu (protection) : " + (gm.getArenaSnapshot().isCaptured() ? "&aOui" : "&cNon configurée"));
        MessageUtil.send(sender, "&c● Rouge: &f" + gm.getScore(Team.RED) + "  &9● Bleu: &f" + gm.getScore(Team.BLUE));
    }

    // ================= ADMIN: START/STOP =================

    private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb start <nom>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;

        if (!gm.getArena().isFullyConfigured()) {
            MessageUtil.send(sender, "&cLa map n'est pas complètement configurée (lobby/spawns/zones manquants).");
            return;
        }
        if (gm.getState() == GameState.PLAYING || gm.getState() == GameState.ROUND_RESET) {
            MessageUtil.send(sender, "&cUne partie est déjà en cours sur cette arène.");
            return;
        }
        boolean started = gm.forceStart();
        MessageUtil.send(sender, started ? "&aPartie démarrée de force." : "&cImpossible de démarrer : il faut au moins un joueur dans chaque équipe.");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb stop <nom>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;

        gm.forceStop();
        MessageUtil.send(sender, "&aPartie arrêtée sur '" + args[1] + "', retour au lobby.");
    }

    // ================= SCOREBOARD (ADMIN) =================

    private void handleSetSbServer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb setsbserver <nom_du_serveur>");
            return;
        }
        String serverName = args[1];
        plugin.getScoreboardManager().setServerName(serverName);
        MessageUtil.send(sender, "&aNom du serveur défini sur: &f" + serverName);
    }

    private void handleSetSbGame(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb setsbgame <nom_du_jeu>");
            return;
        }
        String gameName = args[1];
        plugin.getScoreboardManager().setGameName(gameName);
        MessageUtil.send(sender, "&aNom du jeu défini sur: &f" + gameName);
    }

    private void handleSetSbTitle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb setsbtitle <titre> (utilise & pour les couleurs)");
            return;
        }
        // Reconstruire le titre à partir de tous les arguments
        StringBuilder title = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) title.append(" ");
            title.append(args[i]);
        }
        plugin.getScoreboardManager().setTitle(title.toString());
        MessageUtil.send(sender, "&aTitre du scoreboard défini sur: &f" + title);
    }

    private void handleSetSbLines(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb setsblines <ligne1> | <ligne2> | ...");
            MessageUtil.send(sender, "&7Variables disponibles: %server%, %game%, %red_score%, %blue_score%, %players%, %elapsed_time%");
            MessageUtil.send(sender, "&7Sépare les lignes par des | (pipe character)");
            return;
        }
        // Reconstruire les lignes à partir de tous les arguments séparés par |
        StringBuilder allArgs = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) allArgs.append(" ");
            allArgs.append(args[i]);
        }
        
        String[] linesArray = allArgs.toString().split("\\|");
        List<String> lines = new ArrayList<>();
        for (String line : linesArray) {
            lines.add(line.trim());
        }
        
        plugin.getScoreboardManager().setLines(lines);
        MessageUtil.send(sender, "&aLignes du scoreboard mises à jour. (" + lines.size() + " lignes)");
    }

    private void handleReloadSb(CommandSender sender) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        plugin.getScoreboardManager().reload();
        MessageUtil.send(sender, "&aConfiguration du scoreboard rechargée.");
    }

    private void handleSbInfo(CommandSender sender) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        MessageUtil.send(sender, "&8&m----------&r &bScoreboard &8&m----------");
        MessageUtil.send(sender, "&7Titre: &f" + plugin.getScoreboardManager().getTitle());
        MessageUtil.send(sender, "&7Serveur: &f" + plugin.getScoreboardManager().getServerName());
        MessageUtil.send(sender, "&7Jeu: &f" + plugin.getScoreboardManager().getGameName());
        MessageUtil.send(sender, "&7Lignes: &f" + plugin.getConfig().getStringList("scoreboard.lines").size());
        MessageUtil.send(sender, "&8&m----------&r &7Commandes &8&m----------");
        MessageUtil.send(sender, "&e/hb setsbserver <nom> &7- Définir le nom du serveur");
        MessageUtil.send(sender, "&e/hb setsbgame <nom> &7- Définir le nom du jeu");
        MessageUtil.send(sender, "&e/hb setsbtitle <titre> &7- Définir le titre");
        MessageUtil.send(sender, "&e/hb setsblines <lignes> &7- Définir les lignes (séparées par |)");
        MessageUtil.send(sender, "&e/hb reloadsb &7- Recharger la config");
    }

    // ================= STATISTIQUES =================

    private void handleStats(CommandSender sender, String[] args) {
        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            // /hb stats <pseudo>
            targetName = args[1];
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore() && !offline.isOnline()) {
                MessageUtil.send(sender, "&cAucune statistique trouvée pour &e" + targetName + "&c.");
                return;
            }
            targetUuid = offline.getUniqueId();
            targetName = offline.getName() != null ? offline.getName() : targetName;
        } else {
            // /hb stats → propres stats
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender, "&cPrécise un pseudo : &e/hb stats <pseudo>");
                return;
            }
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        com.hikabrain.plugin.stats.StatsManager.PlayerStats stats =
                plugin.getStatsManager().getPlayerStats(targetUuid, targetName);

        int losses = stats.gamesPlayed - stats.gamesWon;
        double winRate = stats.gamesPlayed > 0
                ? Math.round((double) stats.gamesWon / stats.gamesPlayed * 1000.0) / 10.0
                : 0.0;

        MessageUtil.send(sender, "&8&m----------&r &bStats de " + targetName + " &8&m----------");
        MessageUtil.send(sender, "&f▸ Kills: &a" + stats.kills + " &7/ Deaths: &c" + stats.deaths + " &7/ K/D: &e" + stats.getKD());
        MessageUtil.send(sender, "&f▸ Parties jouées: &7" + stats.gamesPlayed);
        MessageUtil.send(sender, "&f▸ Parties gagnées: &a" + stats.gamesWon + " &7(défaites: &c" + losses + "&7)");
        MessageUtil.send(sender, "&f▸ Taux de victoire: &6" + winRate + "%");
        MessageUtil.send(sender, "&8&m----------&r &7Stats HikaBrain Global &8&m----------");
        MessageUtil.send(sender, "&fParties jouées: &7" + plugin.getStatsManager().getTotalGames()
                + "  &fCaptures: &7" + plugin.getStatsManager().getTotalCaptures());
        MessageUtil.send(sender, "&8&m----------&r");
    }

    private void handleTop(CommandSender sender, String[] args) {
        // /hb top [kd|kills|wins|games]  -- "wins" par défaut
        String criterion = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "wins";

        Comparator<com.hikabrain.plugin.stats.StatsManager.PlayerStats> comparator;
        String label;
        switch (criterion) {
            case "kd" -> {
                comparator = Comparator.comparingDouble(com.hikabrain.plugin.stats.StatsManager.PlayerStats::getKD);
                label = "Meilleur K/D";
            }
            case "kills" -> {
                comparator = Comparator.comparingInt(s -> s.kills);
                label = "Plus de Kills";
            }
            case "games" -> {
                comparator = Comparator.comparingInt(s -> s.gamesPlayed);
                label = "Plus de Parties Jouées";
            }
            case "wins" -> {
                comparator = Comparator.comparingInt(s -> s.gamesWon);
                label = "Plus de Victoires";
            }
            default -> {
                MessageUtil.send(sender, "&cCritère inconnu. Utilise : &e/hb top <kd|kills|wins|games>");
                return;
            }
        }

        List<Map.Entry<UUID, com.hikabrain.plugin.stats.StatsManager.PlayerStats>> top =
                plugin.getStatsManager().getTopPlayers(10, comparator);

        MessageUtil.send(sender, "&8&m----------&r &6&lClassement: " + label + " &8&m----------");

        if (top.isEmpty()) {
            MessageUtil.send(sender, "&7Aucune statistique enregistrée pour le moment.");
        } else {
            int rank = 1;
            for (Map.Entry<UUID, com.hikabrain.plugin.stats.StatsManager.PlayerStats> entry : top) {
                com.hikabrain.plugin.stats.StatsManager.PlayerStats s = entry.getValue();
                String rankColor = switch (rank) {
                    case 1 -> "&6";
                    case 2 -> "&7";
                    case 3 -> "&c";
                    default -> "&f";
                };
                String value = switch (criterion) {
                    case "kd"    -> String.valueOf(s.getKD());
                    case "kills" -> String.valueOf(s.kills);
                    case "games" -> String.valueOf(s.gamesPlayed);
                    default      -> String.valueOf(s.gamesWon);
                };
                MessageUtil.send(sender, rankColor + "&l#" + rank + " &f" + s.name + " &7- &e" + value);
                rank++;
            }
        }

        MessageUtil.send(sender, "&8&m----------&r");
    }

    private void handleHoloStats(CommandSender sender) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }
        StatsHologramManager hm = plugin.getHologramManager();
        hm.spawn(player.getLocation());
        MessageUtil.send(sender, "&aHologramme de stats spawné à ta position !");
        MessageUtil.send(sender, "&7Clique sur la ligne des modes &e[1v1] [2v2] [3v3] [4v4] &7pour changer.");
    }

    private void handleHoloRemove(CommandSender sender) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        StatsHologramManager hm = plugin.getHologramManager();
        if (!hm.isSpawned()) {
            MessageUtil.send(sender, "&cAucun hologramme actif.");
            return;
        }
        hm.despawn();
        MessageUtil.send(sender, "&aHologramme supprimé.");
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur (elle utilise ta position).");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb leaderboard <victoires|kills|kd|parties> [remove]");
            return;
        }

        CategoryLeaderboardManager.Category category = CategoryLeaderboardManager.Category.fromKey(args[1]);
        if (category == null) {
            MessageUtil.send(sender, "&cCatégorie inconnue. Utilise: victoires, kills, kd ou parties.");
            return;
        }

        CategoryLeaderboardManager lm = plugin.getLeaderboardManager();

        if (args.length >= 3 && args[2].equalsIgnoreCase("remove")) {
            boolean removed = lm.despawn(category);
            MessageUtil.send(sender, removed
                    ? "&aLeaderboard '" + category.key + "' supprimé."
                    : "&cAucun leaderboard '" + category.key + "' n'est actuellement actif.");
            return;
        }

        lm.spawn(category, player.getLocation());
        MessageUtil.send(sender, "&aLeaderboard '" + category.key + "' (top 10) spawné à ta position !");
    }

    private void handleResetStats(CommandSender sender) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        plugin.getStatsManager().resetStats();
        MessageUtil.send(sender, "&aToutes les statistiques ont été réinitialisées.");
    }

    // ================= UTILITAIRES =================

    private boolean checkAdminAndPlayer(CommandSender sender) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return false;
        }
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur (elle utilise ta position).");
            return false;
        }
        return true;
    }

    private Team parseTeam(String arg) {
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "red", "rouge" -> Team.RED;
            case "blue", "bleu" -> Team.BLUE;
            default -> null;
        };
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "&8&m----------&r &bHikaBrain &8&m----------");
        MessageUtil.send(sender, "&e/hb join <nom> &7- Rejoindre une arène");
        MessageUtil.send(sender, "&e/hb joinrandom &7- Rejoindre une arène au hasard");
        MessageUtil.send(sender, "&e/hb leave &7- Quitter la partie en cours");
        MessageUtil.send(sender, "&e/hb list &7- Lister toutes les arènes");
        MessageUtil.send(sender, "&e/hb info [nom] &7- Voir l'état d'une arène");
        MessageUtil.send(sender, "&e/hb stats [pseudo] &7- Voir tes statistiques (ou celles d'un joueur)");
        MessageUtil.send(sender, "&e/hb top [kd|kills|wins|games] &7- Voir le classement des meilleurs joueurs");
        if (sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&c/hb create <nom> &7- Créer une nouvelle arène");
            MessageUtil.send(sender, "&c/hb delete <nom> &7- Supprimer une arène");
            MessageUtil.send(sender, "&c/hb setlobby <nom> &7- Définir le point de lobby");
            MessageUtil.send(sender, "&c/hb setspawn <nom> <red|blue> <index> &7- Définir/remplacer un spawn d'équipe");
            MessageUtil.send(sender, "&c/hb delspawn <nom> <red|blue> <index> &7- Supprimer un spawn d'équipe");
            MessageUtil.send(sender, "&c/hb setcapture <nom> <red|blue> <pos1|pos2> &7- Définir la zone de capture");
            MessageUtil.send(sender, "&c/hb setgamezone <nom> <pos1|pos2> &7- Définir la zone de jeu (protection + restauration)");
            MessageUtil.send(sender, "&c/hb start <nom> &7- Forcer le démarrage");
            MessageUtil.send(sender, "&c/hb stop <nom> &7- Forcer l'arrêt");
            MessageUtil.send(sender, "&c/hb resetstats &7- Réinitialiser les statistiques");
            MessageUtil.send(sender, "&b/hb holostats &7- Spawner l'hologramme de stats à ta position");
            MessageUtil.send(sender, "&b/hb holoremove &7- Supprimer l'hologramme de stats");
            MessageUtil.send(sender, "&b/hb leaderboard <victoires|kills|kd|parties> &7- Spawner un leaderboard top 10 à ta position");
            MessageUtil.send(sender, "&b/hb leaderboard <catégorie> remove &7- Supprimer ce leaderboard");
            MessageUtil.send(sender, "&8&m----------&r &dScoreboard &8&m----------");
            MessageUtil.send(sender, "&d/hb setsbserver <nom> &7- Définir le nom du serveur");
            MessageUtil.send(sender, "&d/hb setsbgame <nom> &7- Définir le nom du jeu");
            MessageUtil.send(sender, "&d/hb setsbtitle <titre> &7- Définir le titre");
            MessageUtil.send(sender, "&d/hb setsblines <lignes> &7- Définir les lignes (| pour séparer)");
            MessageUtil.send(sender, "&d/hb reloadsb &7- Recharger la config");
            MessageUtil.send(sender, "&d/hb sbinfo &7- Voir les infos du scoreboard");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("join", "joinrandom", "leave", "info", "list", "stats", "top"));
            if (sender.hasPermission("hikabrain.admin")) {
                options.addAll(List.of("create", "delete", "setlobby", "setspawn", "delspawn", "setcapture", "setgamezone", "start", "stop"));
                options.addAll(List.of("setsbserver", "setsbgame", "setsbtitle", "setsblines", "reloadsb", "sbinfo"));
                options.addAll(List.of("resetstats", "holostats", "holoremove", "leaderboard"));
            }
            return filterStartingWith(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Le 2e argument est presque toujours un nom d'arène existant (sauf pour "create").
        if (args.length == 2 && Set.of("join", "info", "delete", "setlobby", "setspawn", "delspawn", "setcapture", "setgamezone", "start", "stop").contains(sub)) {
            return filterStartingWith(new ArrayList<>(plugin.getArenaManager().getNames()), args[1]);
        }

        if (args.length == 3 && (sub.equals("setspawn") || sub.equals("delspawn") || sub.equals("setcapture"))) {
            return filterStartingWith(List.of("red", "blue"), args[2]);
        }

        if (args.length == 3 && sub.equals("setgamezone")) {
            return filterStartingWith(List.of("pos1", "pos2"), args[2]);
        }

        if (args.length == 4 && sub.equals("setcapture")) {
            return filterStartingWith(List.of("pos1", "pos2"), args[3]);
        }

        if (args.length == 4 && (sub.equals("setspawn") || sub.equals("delspawn"))) {
            return filterStartingWith(List.of("1", "2", "3", "4"), args[3]);
        }

        if (args.length == 2 && sub.equals("top")) {
            return filterStartingWith(List.of("kd", "kills", "wins", "games"), args[1]);
        }

        if (args.length == 2 && sub.equals("leaderboard")) {
            return filterStartingWith(List.of("victoires", "kills", "kd", "parties"), args[1]);
        }

        if (args.length == 3 && sub.equals("leaderboard")) {
            return filterStartingWith(List.of("remove"), args[2]);
        }

        if (args.length == 2 && sub.equals("stats")) {
            List<String> onlineNames = new ArrayList<>();
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) onlineNames.add(p.getName());
            return filterStartingWith(onlineNames, args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterStartingWith(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                result.add(option);
            }
        }
        return result;
    }
}
