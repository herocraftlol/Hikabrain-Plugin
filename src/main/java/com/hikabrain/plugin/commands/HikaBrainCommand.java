package com.hikabrain.plugin.commands;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.Arena;
import com.hikabrain.plugin.game.ArenaManager;
import com.hikabrain.plugin.game.CuboidRegion;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.Team;
import com.hikabrain.plugin.hologram.CategoryLeaderboardManager;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Gère la commande /hb et tous ses sous-arguments.
 *
 * Le plugin gère plusieurs arènes HikaBrain nommées et indépendantes ; presque toutes
 * les commandes (autres que join/leave/list) attendent donc un nom d'arène en paramètre :
 *   /hb create <nom>                          - crée une nouvelle arène vide
 *   /hb copy <source> <nouveau_nom>            - duplique une arène en translatant toutes les positions vers la position du joueur (nouveau lobby)
 *   /hb delete <nom>                          - supprime une arène
 *   /hb list                                  - liste toutes les arènes
 *   /hb setlobby <nom>                        - définit le lobby (à ta position)
 *   /hb setspawn <nom> <red|blue> <index>      - définit/remplace le spawn #index d'une équipe
 *   /hb delspawn <nom> <red|blue> <index>      - supprime le spawn #index d'une équipe
 *   /hb setcapture <nom> <red|blue> <pos1|pos2> - définit une zone de capture
 *   /hb setgamezone <nom> <pos1|pos2>          - définit + capture la zone de jeu protégée
 *   /hb setmaxplayers <nom> <nombre>          - définit le max de joueurs de l'arène (0 = global)
 *   /hb setminplayers <nom> <nombre>          - définit le min de joueurs de l'arène (0 = global)
 *   /hb guislot <page> <emplacement 1-45> <arène|clear> - place une arène à un emplacement précis d'une page du menu /arenas
 *   /hb join <nom>                            - rejoindre une arène
 *   /hb joinrandom                            - rejoindre une arène au hasard (priorité à celles déjà occupées)
 *   /hb leave                                 - quitter l'arène en cours
 *   /hb start <nom>                           - forcer le démarrage
 *   /hb stop <nom>                            - forcer l'arrêt
 *   /hb info <nom>                            - infos sur une arène
 *   /hb top [kd|kills|wins|games|points|force] - classement des meilleurs joueurs
 *   /hb force [pseudo]                        - classement de force "qui bat qui" d'un joueur
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
            case "copy" -> handleCopy(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "setlobby" -> handleSetLobby(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "delspawn" -> handleDelSpawn(sender, args);
            case "setcapture" -> handleSetCapture(sender, args);
            case "setgamezone" -> handleSetGameZone(sender, args);
            case "setmaxplayers" -> handleSetMaxPlayers(sender, args);
            case "setminplayers" -> handleSetMinPlayers(sender, args);
            case "guislot" -> handleGuiSlot(sender, args);
            case "join" -> handleJoin(sender, args);
            case "joinrandom" -> handleJoinRandom(sender);
            case "arenas" -> handleArenasGui(sender);
            case "leave" -> handleLeave(sender);
            case "spectate" -> handleSpectate(sender, args);
            case "unspectate" -> handleUnspectate(sender);
            case "setspectatorspawn" -> handleSetSpectatorSpawn(sender, args);
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            case "info" -> handleInfo(sender, args);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            case "points" -> handlePoints(sender, args);
            case "force" -> handleForce(sender, args);
            case "perk" -> handlePerk(sender, args);
            case "resetstats" -> handleResetStats(sender);
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

    /**
     * /hb copy <source> <nouveau_nom> : duplique intégralement une arène existante
     * (lobby, spawns, zones de capture, zone de jeu + snapshot des blocs, min/max joueurs)
     * sous un nouveau nom, pour déployer rapidement plusieurs arènes déjà configurées.
     *
     * Les coordonnées sont translatées : le joueur doit se tenir à l'endroit qui correspond
     * au lobby de la nouvelle arène (là où il a reconstruit la map), sa position devient le
     * nouveau point de référence, et tous les autres points (spawns, captures, zone de jeu,
     * spectateur) sont décalés d'autant. La zone de jeu est re-capturée à son nouvel endroit.
     */
    private void handleCopy(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 3) {
            MessageUtil.send(sender, "&cUsage: /hb copy <arène source> <nouveau nom>");
            MessageUtil.send(sender, "&7Tiens-toi à l'endroit où doit se trouver le lobby de la nouvelle arène avant de lancer la commande : "
                    + "toutes les positions (spawns, zones de capture, zone de jeu...) seront décalées d'autant par rapport à l'arène source.");
            return;
        }
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur : ta position sert de nouveau point de référence (nouveau lobby).");
            return;
        }

        String sourceName = args[1];
        String newName = args[2];

        ArenaManager.CopyResult result = plugin.getArenaManager().copy(sourceName, newName, player.getLocation());
        switch (result) {
            case SUCCESS -> MessageUtil.send(sender, "&aArène '" + sourceName + "' copiée vers '" + newName
                    + "' ! &7Toutes les positions (spawns, zones de capture, zone de jeu, spectateur) ont été "
                    + "décalées par rapport à ta position actuelle (nouveau lobby). "
                    + "Ajuste-les individuellement avec /hb setspawn, setcapture, setgamezone si besoin.");
            case SOURCE_NOT_FOUND -> MessageUtil.send(sender, "&cAucune arène '" + sourceName + "' trouvée.");
            case TARGET_ALREADY_EXISTS -> MessageUtil.send(sender, "&cUne arène '" + newName + "' existe déjà.");
            case SOURCE_HAS_NO_LOBBY -> MessageUtil.send(sender, "&cL'arène '" + sourceName
                    + "' n'a pas de lobby défini : impossible de calculer le décalage des positions. "
                    + "Configure d'abord son lobby avec /hb setlobby " + sourceName + ".");
            case INVALID_NAME -> MessageUtil.send(sender, "&cNom d'arène invalide.");
        }
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

    /**
     * Définit le point de téléportation dédié aux spectateurs de cette arène (à la position
     * actuelle de l'admin). Facultatif : sans ce point, on retombe automatiquement sur le
     * centre de la zone de jeu (gameZone), sinon sur le lobby.
     */
    private void handleSetSpectatorSpawn(CommandSender sender, String[] args) {
        if (!checkAdminAndPlayer(sender)) return;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb setspectatorspawn <nom>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;
        Player player = (Player) sender;

        gm.getArena().setSpectatorSpawn(player.getLocation());
        gm.saveArenaConfig();

        MessageUtil.send(sender, "&aLe point de spawn spectateur de '" + args[1] + "' a été défini à ta position.");
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

    /**
     * Définit le nombre maximum de joueurs pour une arène spécifique. Un nombre <= 0
     * réinitialise la valeur spécifique (l'arène retombera alors sur le max-players global).
     */
    private void handleSetMaxPlayers(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 3) {
            MessageUtil.send(sender, "&cUsage: /hb setmaxplayers <nom> <nombre>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;

        int max;
        try {
            max = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "&cLe nombre de joueurs doit être un entier.");
            return;
        }
        if (max < 0) {
            MessageUtil.send(sender, "&cLe nombre de joueurs ne peut pas être négatif.");
            return;
        }

        gm.getArena().setMaxPlayers(max);
        gm.saveArenaConfig();

        if (max == 0) {
            MessageUtil.send(sender, "&aLe nombre maximum de joueurs spécifique à l'arène &7" + gm.getName()
                    + " &aa été réinitialisé (utilisera désormais le max-players global).");
        } else {
            MessageUtil.send(sender, "&aLe nombre maximum de joueurs de l'arène &7" + gm.getName()
                    + " &aa été fixé à &7" + max + "&a.");
        }
    }

    /**
     * Définit le nombre minimum de joueurs pour une arène spécifique, avant que le compte à
     * rebours du lobby ne puisse se lancer. Un nombre <= 0 réinitialise la valeur spécifique
     * (l'arène retombera alors sur le min-players global).
     */
    private void handleSetMinPlayers(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 3) {
            MessageUtil.send(sender, "&cUsage: /hb setminplayers <nom> <nombre>");
            return;
        }
        GameManager gm = resolveArena(sender, args, 1);
        if (gm == null) return;

        int min;
        try {
            min = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "&cLe nombre de joueurs doit être un entier.");
            return;
        }
        if (min < 0) {
            MessageUtil.send(sender, "&cLe nombre de joueurs ne peut pas être négatif.");
            return;
        }

        gm.getArena().setMinPlayers(min);
        gm.saveArenaConfig();

        if (min == 0) {
            MessageUtil.send(sender, "&aLe nombre minimum de joueurs spécifique à l'arène &7" + gm.getName()
                    + " &aa été réinitialisé (utilisera désormais le min-players global).");
        } else {
            MessageUtil.send(sender, "&aLe nombre minimum de joueurs de l'arène &7" + gm.getName()
                    + " &aa été fixé à &7" + min + "&a.");
        }
    }

    /**
     * /hb guislot <page> <emplacement 1-45> <arène|clear> : place une arène à un emplacement
     * précis d'une page précise du menu /arenas (le GUI d'inventaire), ou retire son
     * assignation explicite avec "clear". La dernière ligne de chaque page (navigation +
     * bouton "arène aléatoire") n'est pas assignable.
     */
    private void handleGuiSlot(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }

        int maxSlot = com.hikabrain.plugin.gui.ArenaGUI.getMaxAssignableSlot();

        if (args.length < 4) {
            MessageUtil.send(sender, "&cUsage: /hb guislot <page> <emplacement 1-" + maxSlot + "> <arène|clear>");
            MessageUtil.send(sender, "&7Exemple: /hb guislot 1 5 arena1 → place 'arena1' au 5ème emplacement de la page 1.");
            return;
        }

        int page1Based;
        try {
            page1Based = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "&cLa page doit être un nombre (1 = première page).");
            return;
        }
        if (page1Based < 1) {
            MessageUtil.send(sender, "&cLa page doit être supérieure ou égale à 1.");
            return;
        }

        int slot1Based;
        try {
            slot1Based = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "&cL'emplacement doit être un nombre entre 1 et " + maxSlot + ".");
            return;
        }

        if (slot1Based < 1 || slot1Based > maxSlot) {
            MessageUtil.send(sender, "&cL'emplacement doit être compris entre 1 et " + maxSlot
                    + " (la dernière ligne de chaque page est réservée à la navigation et au bouton \"arène aléatoire\").");
            return;
        }

        int page0Based = page1Based - 1;
        int slot0Based = slot1Based - 1;

        if (args[3].equalsIgnoreCase("clear")) {
            boolean removed = plugin.getArenaGUI().clearSlot(page0Based, slot0Based);
            MessageUtil.send(sender, removed
                    ? "&aEmplacement &e" + slot1Based + " &a(page " + page1Based + ") libéré : cette case sera de nouveau remplie automatiquement."
                    : "&cAucune arène n'était explicitement assignée à cet emplacement (page " + page1Based + ").");
            return;
        }

        String arenaName = args[3];
        if (!plugin.getArenaManager().exists(arenaName)) {
            MessageUtil.send(sender, "&cAucune arène '" + arenaName + "' trouvée.");
            return;
        }

        plugin.getArenaGUI().assignSlot(page0Based, slot0Based, arenaName);
        MessageUtil.send(sender, "&aArène '" + arenaName + "' placée à l'emplacement &e" + slot1Based
                + " &ade la page &e" + page1Based + " &adu menu /arenas.");
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

        GameManager currentSpectate = am.findSpectatorArenaOf(player);
        if (currentSpectate != null) {
            MessageUtil.send(sender, "&cTu es en mode spectateur (" + currentSpectate.getName() + "). Fais /hb unspectate d'abord.");
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

        GameManager currentSpectate = am.findSpectatorArenaOf(player);
        if (currentSpectate != null) {
            MessageUtil.send(sender, "&cTu es en mode spectateur (" + currentSpectate.getName() + "). Fais /hb unspectate d'abord.");
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

    /**
     * Rejoint le mode spectateur sur une arène donnée, pour regarder une partie en cours
     * (ou en attente) sans y participer. Le joueur reste confiné à la zone de l'arène
     * (voir PlayerMoveListener) et peut repartir à tout moment avec /hb unspectate.
     */
    private void handleSpectate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb spectate <nom>");
            return;
        }

        ArenaManager am = plugin.getArenaManager();
        GameManager gm = am.get(args[1]);
        if (gm == null) {
            MessageUtil.send(sender, "&cAucune arène nommée '" + args[1] + "' n'existe.");
            return;
        }

        GameManager currentGame = am.findArenaOf(player);
        if (currentGame != null) {
            MessageUtil.send(sender, "&cTu es déjà dans une partie (" + currentGame.getName() + "). Fais /hb leave d'abord.");
            return;
        }

        GameManager currentSpectate = am.findSpectatorArenaOf(player);
        if (currentSpectate != null) {
            if (currentSpectate.getName().equals(gm.getName())) {
                MessageUtil.send(sender, "&cTu regardes déjà cette arène en spectateur.");
                return;
            }
            // On quitte le spectate actuel avant de rejoindre le nouveau, pour rester cohérent
            // avec un seul mode spectateur actif à la fois.
            currentSpectate.removeSpectator(player);
        }

        gm.addSpectator(player);
    }

    private void handleUnspectate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }
        GameManager gm = plugin.getArenaManager().findSpectatorArenaOf(player);
        if (gm == null) {
            MessageUtil.send(sender, "&cTu n'es pas en mode spectateur en ce moment.");
            return;
        }
        gm.removeSpectator(player);
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
        MessageUtil.send(sender, "&7Joueurs en partie : &f" + gm.getPlayerCount() + "&7/&f" + gm.getMaxPlayers()
                + (arena.getMaxPlayers() > 0 ? " &8(spécifique)" : " &8(global)"));
        MessageUtil.send(sender, "&7Joueurs minimum pour démarrer : &f" + gm.getMinPlayers()
                + (arena.getMinPlayers() > 0 ? " &8(spécifique)" : " &8(global)"));
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
        // /hb top [kd|kills|wins|games|points|force] [alltime|today|week|custom <de> <à>]  -- "wins"/"alltime" par défaut
        String criterion = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "wins";

        if (criterion.equals("points")) {
            handleTopPoints(sender, args);
            return;
        }
        if (criterion.equals("force")) {
            handleTopForce(sender, args);
            return;
        }

        LocalDate[] range = parsePeriodArg(sender, args, 2);
        if (range != null && range.length == 0) return; // erreur déjà envoyée par parsePeriodArg

        String label;
        List<String> lines = new ArrayList<>();

        if (range == null) {
            // ── Classement "depuis toujours" (comportement historique, inchangé) ──
            Comparator<com.hikabrain.plugin.stats.StatsManager.PlayerStats> comparator;
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
                case "hits" -> {
                    comparator = Comparator.comparingInt(s -> s.hitsGiven);
                    label = "Plus de Coups Donnés";
                }
                case "hitsreceived" -> {
                    comparator = Comparator.comparingInt(s -> s.hitsReceived);
                    label = "Plus de Coups Reçus";
                }
                case "goals" -> {
                    comparator = Comparator.comparingInt(s -> s.goalsScored);
                    label = "Plus de Buts Marqués";
                }
                default -> {
                    MessageUtil.send(sender, "&cCritère inconnu. Utilise : &e/hb top <kd|kills|wins|games|points|force|hits|hitsreceived|goals>");
                    return;
                }
            }

            List<Map.Entry<UUID, com.hikabrain.plugin.stats.StatsManager.PlayerStats>> top =
                    plugin.getStatsManager().getTopPlayers(10, comparator);

            int rank = 1;
            for (Map.Entry<UUID, com.hikabrain.plugin.stats.StatsManager.PlayerStats> entry : top) {
                com.hikabrain.plugin.stats.StatsManager.PlayerStats s = entry.getValue();
                String value = switch (criterion) {
                    case "kd"    -> String.valueOf(s.getKD());
                    case "kills" -> String.valueOf(s.kills);
                    case "games" -> String.valueOf(s.gamesPlayed);
                    case "hits"  -> String.valueOf(s.hitsGiven);
                    case "hitsreceived" -> String.valueOf(s.hitsReceived);
                    case "goals" -> String.valueOf(s.goalsScored);
                    default      -> String.valueOf(s.gamesWon);
                };
                lines.add(rankLine(rank++, s.name, value));
            }
        } else {
            // ── Classement limité à une plage de temps (voir MatchHistoryManager) ──
            Comparator<com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats> comparator;
            switch (criterion) {
                case "kd" -> {
                    comparator = Comparator.comparingDouble(com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats::getKD);
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
                    comparator = Comparator.comparingInt(s -> s.wins);
                    label = "Plus de Victoires";
                }
                case "hits" -> {
                    comparator = Comparator.comparingInt(s -> s.hits);
                    label = "Plus de Coups Donnés";
                }
                case "hitsreceived" -> {
                    comparator = Comparator.comparingInt(s -> s.hitsReceived);
                    label = "Plus de Coups Reçus";
                }
                case "goals" -> {
                    comparator = Comparator.comparingInt(s -> s.goals);
                    label = "Plus de Buts Marqués";
                }
                default -> {
                    MessageUtil.send(sender, "&cCritère inconnu. Utilise : &e/hb top <kd|kills|wins|games|points|force|hits|hitsreceived|goals>");
                    return;
                }
            }

            List<Map.Entry<UUID, com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats>> top =
                    new ArrayList<>(plugin.getMatchHistoryManager().aggregatePlayerStats(range[0], range[1]).entrySet());
            top.sort((a, b) -> comparator.compare(b.getValue(), a.getValue()));
            if (top.size() > 10) top = top.subList(0, 10);

            int rank = 1;
            for (Map.Entry<UUID, com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats> entry : top) {
                com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats s = entry.getValue();
                String value = switch (criterion) {
                    case "kd"    -> String.valueOf(s.getKD());
                    case "kills" -> String.valueOf(s.kills);
                    case "games" -> String.valueOf(s.gamesPlayed);
                    case "hits"  -> String.valueOf(s.hits);
                    case "hitsreceived" -> String.valueOf(s.hitsReceived);
                    case "goals" -> String.valueOf(s.goals);
                    default      -> String.valueOf(s.wins);
                };
                lines.add(rankLine(rank++, s.name, value));
            }
        }

        MessageUtil.send(sender, "&8&m----------&r &6&lClassement: " + label + " " + describePeriod(range) + " &8&m----------");
        if (lines.isEmpty()) {
            MessageUtil.send(sender, "&7Aucune statistique enregistrée pour cette période.");
        } else {
            lines.forEach(l -> MessageUtil.send(sender, l));
        }
        MessageUtil.send(sender, "&8&m----------&r");
    }

    /**
     * Formate une ligne de classement "#rang nom - valeur", avec la couleur d'or/argent/
     * bronze pour le podium. Petit utilitaire commun à tous les /hb top.
     */
    private String rankLine(int rank, String name, String value) {
        String rankColor = switch (rank) {
            case 1 -> "&6";
            case 2 -> "&7";
            case 3 -> "&c";
            default -> "&f";
        };
        return rankColor + "&l#" + rank + " &f" + name + " &7- &e" + value;
    }

    /**
     * Interprète l'argument de période optionnel d'un /hb top (à partir de l'index donné) :
     *  - absent, "alltime"/"all"/"total"       → null (= classement "depuis toujours")
     *  - "today"/"jour"/"aujourdhui"           → aujourd'hui uniquement
     *  - "week"/"semaine"                       → 7 derniers jours glissants (aujourd'hui inclus)
     *  - "custom" <AAAA-MM-JJ> <AAAA-MM-JJ>     → plage de dates précise, incluse des deux côtés
     * Renvoie un tableau vide (longueur 0, à distinguer de null) si la syntaxe est invalide
     * — un message d'erreur a alors déjà été envoyé, l'appelant doit juste s'arrêter.
     */
    private LocalDate[] parsePeriodArg(CommandSender sender, String[] args, int index) {
        if (args.length <= index) return null; // pas précisé → "depuis toujours"

        String period = args[index].toLowerCase(Locale.ROOT);
        switch (period) {
            case "alltime", "all", "total" -> {
                return null;
            }
            case "today", "jour", "aujourdhui" -> {
                return plugin.getMatchHistoryManager().rangeForToday();
            }
            case "week", "semaine" -> {
                return plugin.getMatchHistoryManager().rangeForWeek();
            }
            case "custom" -> {
                if (args.length < index + 3) {
                    MessageUtil.send(sender, "&cUsage: ... custom <AAAA-MM-JJ> <AAAA-MM-JJ>");
                    return new LocalDate[0];
                }
                try {
                    LocalDate from = LocalDate.parse(args[index + 1]);
                    LocalDate to = LocalDate.parse(args[index + 2]);
                    if (from.isAfter(to)) {
                        LocalDate tmp = from;
                        from = to;
                        to = tmp;
                    }
                    return new LocalDate[]{ from, to };
                } catch (DateTimeParseException e) {
                    MessageUtil.send(sender, "&cDates invalides. Format attendu : &eAAAA-MM-JJ &c(ex: 2026-08-01)");
                    return new LocalDate[0];
                }
            }
            default -> {
                MessageUtil.send(sender, "&cPériode inconnue. Utilise : &ealltime&c, &etoday&c, &eweek&c, ou &ecustom <AAAA-MM-JJ> <AAAA-MM-JJ>");
                return new LocalDate[0];
            }
        }
    }

    /** Petit texte "(cette semaine)"/"(aujourd'hui)"/"(du X au Y)" pour l'en-tête d'un classement. Vide si "depuis toujours". */
    private String describePeriod(LocalDate[] range) {
        if (range == null) return "";
        LocalDate today = LocalDate.now();
        if (range[0].equals(today) && range[1].equals(today)) return "&7(aujourd'hui)";
        if (range[1].equals(today) && range[0].equals(today.minusDays(6))) return "&7(cette semaine)";
        return "&7(du " + range[0] + " au " + range[1] + ")";
    }

    /**
     * /hb top points : classement des 10 premiers joueurs par points HikaBrain cumulés.
     */
    /**
     * /hb top points [alltime|today|week|custom <de> <à>] : classement par points.
     * "alltime" (par défaut) classe par total de points cumulés à vie. Pour une période
     * précise, classe par points GAGNÉS pendant cette période (pas le total à vie) — donc
     * "qui a le plus progressé cette semaine", par exemple.
     */
    private void handleTopPoints(CommandSender sender, String[] args) {
        LocalDate[] range = parsePeriodArg(sender, args, 2);
        if (range != null && range.length == 0) return; // erreur déjà envoyée

        List<String> lines = new ArrayList<>();

        if (range == null) {
            com.hikabrain.plugin.levels.LevelManager lm = plugin.getLevelManager();
            List<Map.Entry<UUID, com.hikabrain.plugin.levels.LevelManager.PlayerLevelData>> top = lm.getTopPlayers(10);
            int rank = 1;
            for (Map.Entry<UUID, com.hikabrain.plugin.levels.LevelManager.PlayerLevelData> entry : top) {
                com.hikabrain.plugin.levels.LevelManager.PlayerLevelData data = entry.getValue();
                int level = lm.getLevelForPoints(data.points);
                lines.add(rankLine(rank++, data.name, data.points + " pts &7(niv. &b" + level + "&7)"));
            }
        } else {
            List<Map.Entry<UUID, com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats>> top =
                    new ArrayList<>(plugin.getMatchHistoryManager().aggregatePlayerStats(range[0], range[1]).entrySet());
            top.sort((a, b) -> Integer.compare(b.getValue().pointsGained, a.getValue().pointsGained));
            if (top.size() > 10) top = top.subList(0, 10);
            int rank = 1;
            for (Map.Entry<UUID, com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats> entry : top) {
                com.hikabrain.plugin.stats.MatchHistoryManager.AggregatedStats s = entry.getValue();
                lines.add(rankLine(rank++, s.name, "+" + s.pointsGained + " pts"));
            }
        }

        MessageUtil.send(sender, "&8&m----------&r &6&lClassement: Points HikaBrain " + describePeriod(range) + " &8&m----------");
        if (lines.isEmpty()) {
            MessageUtil.send(sender, "&7Aucun joueur n'a encore gagné de points sur cette période.");
        } else {
            lines.forEach(l -> MessageUtil.send(sender, l));
        }
        MessageUtil.send(sender, "&8&m----------&r");
    }

    /**
     * /hb top force [alltime|today|week|custom <de> <à>] : classement des joueurs les plus
     * forts, calculé à partir des confrontations directes entre joueurs (qui a battu qui),
     * pas seulement du nombre brut de victoires — voir PowerRankingCalculator. Pour une
     * période précise, le graphe de confrontations est recalculé UNIQUEMENT à partir des
     * parties de cette période (voir MatchHistoryManager#aggregateHeadToHead).
     */
    private void handleTopForce(CommandSender sender, String[] args) {
        LocalDate[] range = parsePeriodArg(sender, args, 2);
        if (range != null && range.length == 0) return; // erreur déjà envoyée

        List<com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower> ranking = range == null
                ? com.hikabrain.plugin.stats.PowerRankingCalculator.compute(plugin.getHeadToHeadManager())
                : com.hikabrain.plugin.stats.PowerRankingCalculator.compute(plugin.getMatchHistoryManager().aggregateHeadToHead(range[0], range[1]));

        MessageUtil.send(sender, "&8&m----------&r &6&lClassement: Force (qui bat qui) " + describePeriod(range) + " &8&m----------");

        if (ranking.isEmpty()) {
            MessageUtil.send(sender, "&7Aucune confrontation enregistrée sur cette période (il faut au moins une partie terminée).");
        } else {
            int rank = 1;
            for (com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower p : ranking) {
                if (rank > 10) break;
                MessageUtil.send(sender, rankLine(rank, p.name,
                        String.format(java.util.Locale.FRANCE, "%.1f", p.score * 1000)
                                + " &7(&a" + p.totalWins + "V &7/ &c" + p.totalLosses + "D&7, " + p.distinctOpponents + " adversaire(s))"));
                rank++;
            }
        }

        MessageUtil.send(sender, "&8&m----------&r");
    }

    /**
     * /hb force [pseudo] : détail du classement de force d'un joueur (soi-même par défaut) :
     * score, rang, bilan, et sa "meilleure victoire" (l'adversaire le mieux classé qu'il a
     * déjà battu, ce qui illustre concrètement la logique "qui bat qui").
     */
    private void handleForce(CommandSender sender, String[] args) {
        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            targetName = args[1];
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore() && !offline.isOnline()) {
                MessageUtil.send(sender, "&cAucune donnée trouvée pour &e" + targetName + "&c.");
                return;
            }
            targetUuid = offline.getUniqueId();
            targetName = offline.getName() != null ? offline.getName() : targetName;
        } else {
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender, "&cPrécise un pseudo : &e/hb force <pseudo>");
                return;
            }
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        com.hikabrain.plugin.stats.HeadToHeadManager h2h = plugin.getHeadToHeadManager();
        com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPowerWithRank result =
                com.hikabrain.plugin.stats.PowerRankingCalculator.computeForPlayer(h2h, targetUuid);

        if (result == null) {
            MessageUtil.send(sender, "&c" + targetName + " n'a encore aucune confrontation enregistrée (il faut avoir terminé au moins une partie).");
            return;
        }

        com.hikabrain.plugin.stats.PowerRankingCalculator.PlayerPower p = result.power;

        MessageUtil.send(sender, "&8&m----------&r &bForce de " + targetName + " &8&m----------");
        MessageUtil.send(sender, "&f▸ Rang: &e#" + result.rank + " &7/ " + result.totalRanked);
        MessageUtil.send(sender, "&f▸ Score de force: &e" + String.format(java.util.Locale.FRANCE, "%.1f", p.score * 1000));
        MessageUtil.send(sender, "&f▸ Bilan: &a" + p.totalWins + " victoire(s) &7/ &c" + p.totalLosses + " défaite(s)"
                + " &7face à &f" + p.distinctOpponents + " &7adversaire(s) différent(s)");
        if (p.bestWinOpponentName != null) {
            MessageUtil.send(sender, "&f▸ Meilleure victoire: &6" + p.bestWinOpponentName
                    + " &7(l'adversaire le mieux classé qu'il/elle a déjà battu)");
        }
        MessageUtil.send(sender, "&8&m----------&r");
    }

    /**
     * /hb points [pseudo] : affiche les points, le niveau, la progression vers le niveau
     * suivant, l'avantage équipé et les avantages débloqués d'un joueur (soi-même par défaut).
     */
    private void handlePoints(CommandSender sender, String[] args) {
        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            targetName = args[1];
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore() && !offline.isOnline()) {
                MessageUtil.send(sender, "&cAucune donnée trouvée pour &e" + targetName + "&c.");
                return;
            }
            targetUuid = offline.getUniqueId();
            targetName = offline.getName() != null ? offline.getName() : targetName;
        } else {
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender, "&cPrécise un pseudo : &e/hb points <pseudo>");
                return;
            }
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        com.hikabrain.plugin.levels.LevelManager lm = plugin.getLevelManager();
        int points = lm.getPoints(targetUuid);
        int level = lm.getLevelForPoints(points);
        int toNext = lm.getPointsToNextLevel(targetUuid);
        com.hikabrain.plugin.levels.Perk equipped = lm.getEquippedPerk(targetUuid);

        MessageUtil.send(sender, "&8&m----------&r &bPoints de " + targetName + " &8&m----------");
        MessageUtil.send(sender, "&f▸ Niveau: &a" + level + " &7(&e" + points + " pts&7)");
        MessageUtil.send(sender, "&f▸ Points pour le niveau suivant: &e" + toNext);
        MessageUtil.send(sender, "&f▸ Avantage équipé: " + (equipped != null ? MessageUtil.format(equipped.getDisplayName()) : "&7Aucun"));

        List<com.hikabrain.plugin.levels.Perk> unlocked = lm.getUnlockedPerks(targetUuid);
        if (unlocked.isEmpty()) {
            MessageUtil.send(sender, "&f▸ Avantages débloqués: &7Aucun pour le moment");
        } else {
            StringBuilder sb = new StringBuilder("&f▸ Avantages débloqués: ");
            for (int i = 0; i < unlocked.size(); i++) {
                sb.append(MessageUtil.format(unlocked.get(i).getDisplayName()));
                if (i < unlocked.size() - 1) sb.append("&7, ");
            }
            MessageUtil.send(sender, sb.toString());
        }
        MessageUtil.send(sender, "&7Utilise &e/hb perk list &7pour voir tous les avantages et &e/hb perk <id> &7pour en équiper un.");
        MessageUtil.send(sender, "&8&m----------&r");
    }

    /**
     * /hb perk               : équivalent de "/hb perk list"
     * /hb perk list          : liste tous les avantages, débloqués ou non, avec l'ID à utiliser
     * /hb perk none          : retire l'avantage équipé
     * /hb perk <id>          : équipe un avantage débloqué
     */
    private void handlePerk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur.");
            return;
        }

        com.hikabrain.plugin.levels.LevelManager lm = plugin.getLevelManager();
        UUID uuid = player.getUniqueId();

        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            com.hikabrain.plugin.levels.Perk equipped = lm.getEquippedPerk(uuid);
            MessageUtil.send(sender, "&8&m----------&r &dAvantages HikaBrain &8&m----------");
            for (com.hikabrain.plugin.levels.Perk perk : com.hikabrain.plugin.levels.Perk.values()) {
                boolean unlocked = lm.isPerkUnlocked(uuid, perk);
                String status = !unlocked ? "&7(verrouillé, niveau " + perk.getUnlockLevel() + " requis)"
                        : (perk == equipped ? "&a(équipé)" : "&7(/hb perk " + perk.getId() + ")");
                MessageUtil.send(sender, (unlocked ? "&a" : "&8") + "▸ " + MessageUtil.format(perk.getDisplayName())
                        + " &7- " + MessageUtil.format(perk.getDescription()));
                MessageUtil.send(sender, "   " + status);
            }
            MessageUtil.send(sender, "&7Ces avantages sont purement cosmétiques : aucun n'affecte le jeu.");
            MessageUtil.send(sender, "&8&m----------&r");
            return;
        }

        if (args[1].equalsIgnoreCase("none")) {
            lm.equipPerk(uuid, player.getName(), null);
            MessageUtil.send(sender, "&aAvantage retiré.");
            return;
        }

        com.hikabrain.plugin.levels.Perk perk = com.hikabrain.plugin.levels.Perk.fromId(args[1]);
        if (perk == null) {
            MessageUtil.send(sender, "&cAvantage inconnu. Utilise &e/hb perk list &cpour voir la liste.");
            return;
        }

        boolean equipped = lm.equipPerk(uuid, player.getName(), perk);
        if (!equipped) {
            MessageUtil.send(sender, "&cTu n'as pas encore débloqué cet avantage (niveau " + perk.getUnlockLevel() + " requis).");
        } else {
            MessageUtil.send(sender, "&aAvantage équipé : " + MessageUtil.format(perk.getDisplayName()));
        }
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&cTu n'as pas la permission.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /hb leaderboard <victoires|kills|kd|parties> [remove|size <taille>]");
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

        if (args.length >= 3 && args[2].equalsIgnoreCase("size")) {
            if (args.length < 4) {
                MessageUtil.send(sender, "&cUsage: /hb leaderboard <catégorie> size <taille>");
                MessageUtil.send(sender, "&7La taille est un multiplicateur (ex: 1.0 = normal, 2.0 = deux fois plus grand, 0.5 = deux fois plus petit). Doit être entre 0.1 et 10.");
                return;
            }
            if (!lm.isSpawned(category)) {
                MessageUtil.send(sender, "&cAucun leaderboard '" + category.key + "' n'est actuellement actif.");
                return;
            }
            double scale;
            try {
                scale = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                MessageUtil.send(sender, "&cTaille invalide. Utilise un nombre (ex: 1.5).");
                return;
            }
            if (scale < 0.1 || scale > 10.0) {
                MessageUtil.send(sender, "&cLa taille doit être comprise entre 0.1 et 10.");
                return;
            }
            lm.setScale(category, scale);
            MessageUtil.send(sender, "&aTaille du leaderboard '" + category.key + "' réglée sur " + scale + ".");
            return;
        }

        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "&cCette commande doit être exécutée par un joueur (elle utilise ta position).");
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
        plugin.getHeadToHeadManager().resetAll();
        MessageUtil.send(sender, "&aToutes les statistiques (dont les confrontations directes/classement de force) ont été réinitialisées.");
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
        MessageUtil.send(sender, "&e/hb spectate <nom> &7- Regarder une partie en cours en spectateur");
        MessageUtil.send(sender, "&e/hb unspectate &7- Quitter le mode spectateur");
        MessageUtil.send(sender, "&e/hb list &7- Lister toutes les arènes");
        MessageUtil.send(sender, "&e/hb arenas &7- Ouvrir le menu pour rejoindre une arène");
        MessageUtil.send(sender, "&e/hb info [nom] &7- Voir l'état d'une arène");
        MessageUtil.send(sender, "&e/hb stats [pseudo] &7- Voir tes statistiques (ou celles d'un joueur)");
        MessageUtil.send(sender, "&e/hb top <kd|kills|wins|games|points|force|hits|hitsreceived|goals> [période] &7- Classement");
        MessageUtil.send(sender, "  &7Période optionnelle : &falltime &7(déf.), &ftoday&7, &fweek&7, ou &fcustom <AAAA-MM-JJ> <AAAA-MM-JJ>");
        MessageUtil.send(sender, "&e/hb points [pseudo] &7- Voir tes points, ton niveau et tes avantages débloqués");
        MessageUtil.send(sender, "&e/hb force [pseudo] &7- Voir ton classement de force (qui bat qui) et ta meilleure victoire");
        MessageUtil.send(sender, "&e/hb perk [list|none|<id>] &7- Voir/équiper tes avantages cosmétiques débloqués");
        if (sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&c/hb create <nom> &7- Créer une nouvelle arène");
            MessageUtil.send(sender, "&c/hb copy <source> <nouveau nom> &7- Dupliquer une arène : tiens-toi au nouveau lobby, toutes les positions sont décalées d'autant");
            MessageUtil.send(sender, "&c/hb delete <nom> &7- Supprimer une arène");
            MessageUtil.send(sender, "&c/hb setlobby <nom> &7- Définir le point de lobby");
            MessageUtil.send(sender, "&c/hb setspectatorspawn <nom> &7- Définir le point de spawn des spectateurs");
            MessageUtil.send(sender, "&c/hb setspawn <nom> <red|blue> <index> &7- Définir/remplacer un spawn d'équipe");
            MessageUtil.send(sender, "&c/hb delspawn <nom> <red|blue> <index> &7- Supprimer un spawn d'équipe");
            MessageUtil.send(sender, "&c/hb setcapture <nom> <red|blue> <pos1|pos2> &7- Définir la zone de capture");
            MessageUtil.send(sender, "&c/hb setgamezone <nom> <pos1|pos2> &7- Définir la zone de jeu (protection + restauration)");
            MessageUtil.send(sender, "&c/hb setmaxplayers <nom> <nombre> &7- Définir le nombre max de joueurs de l'arène (0 = global)");
            MessageUtil.send(sender, "&c/hb setminplayers <nom> <nombre> &7- Définir le nombre min de joueurs de l'arène (0 = global)");
            MessageUtil.send(sender, "&c/hb guislot <page> <emplacement 1-45> <arène|clear> &7- Placer une arène à un emplacement précis d'une page du menu /arenas");
            MessageUtil.send(sender, "&c/hb start <nom> &7- Forcer le démarrage");
            MessageUtil.send(sender, "&c/hb stop <nom> &7- Forcer l'arrêt");
            MessageUtil.send(sender, "&c/hb resetstats &7- Réinitialiser les statistiques");
            MessageUtil.send(sender, "&b/hb leaderboard <victoires|kills|kd|parties> &7- Spawner un leaderboard top 10 à ta position");
            MessageUtil.send(sender, "&b/hb leaderboard <catégorie> remove &7- Supprimer ce leaderboard");
            MessageUtil.send(sender, "&b/hb leaderboard <catégorie> size <taille> &7- Régler la taille de l'hologramme (ex: 1.5)");
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
            List<String> options = new ArrayList<>(List.of("join", "joinrandom", "leave", "spectate", "unspectate", "info", "list", "arenas", "stats", "top", "points", "perk", "force"));
            if (sender.hasPermission("hikabrain.admin")) {
                options.addAll(List.of("create", "copy", "delete", "setlobby", "setspectatorspawn", "setspawn", "delspawn", "setcapture", "setgamezone", "setmaxplayers", "setminplayers", "guislot", "start", "stop"));
                options.addAll(List.of("setsbserver", "setsbgame", "setsbtitle", "setsblines", "reloadsb", "sbinfo"));
                options.addAll(List.of("resetstats", "leaderboard"));
            }
            return filterStartingWith(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Le 2e argument est presque toujours un nom d'arène existant (sauf pour "create").
        if (args.length == 2 && Set.of("join", "info", "delete", "copy", "setlobby", "setspectatorspawn", "setspawn", "delspawn", "setcapture", "setgamezone", "setmaxplayers", "setminplayers", "start", "stop", "spectate").contains(sub)) {
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
            return filterStartingWith(List.of("kd", "kills", "wins", "games", "points", "force", "hits", "hitsreceived", "goals"), args[1]);
        }

        if (args.length == 3 && sub.equals("top")) {
            return filterStartingWith(List.of("alltime", "today", "week", "custom"), args[2]);
        }

        if (args.length == 2 && sub.equals("force")) {
            List<String> onlineNames = new ArrayList<>();
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) onlineNames.add(p.getName());
            return filterStartingWith(onlineNames, args[1]);
        }

        if (args.length == 2 && sub.equals("guislot")) {
            return filterStartingWith(List.of("1", "2", "3"), args[1]);
        }

        if (args.length == 3 && sub.equals("guislot")) {
            return filterStartingWith(List.of("1", "2", "3", "9", "10", "18", "27", "36", "45"), args[2]);
        }

        if (args.length == 4 && sub.equals("guislot")) {
            List<String> options = new ArrayList<>(List.of("clear"));
            options.addAll(plugin.getArenaManager().getNames());
            return filterStartingWith(options, args[3]);
        }

        if (args.length == 2 && sub.equals("points")) {
            List<String> onlineNames = new ArrayList<>();
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) onlineNames.add(p.getName());
            return filterStartingWith(onlineNames, args[1]);
        }

        if (args.length == 2 && sub.equals("perk")) {
            List<String> options = new ArrayList<>(List.of("list", "none"));
            for (com.hikabrain.plugin.levels.Perk perk : com.hikabrain.plugin.levels.Perk.values()) {
                options.add(perk.getId());
            }
            return filterStartingWith(options, args[1]);
        }

        if (args.length == 2 && sub.equals("leaderboard")) {
            return filterStartingWith(List.of("victoires", "kills", "kd", "parties"), args[1]);
        }

        if (args.length == 3 && sub.equals("leaderboard")) {
            return filterStartingWith(List.of("remove", "size"), args[2]);
        }

        if (args.length == 4 && sub.equals("leaderboard") && args[2].equalsIgnoreCase("size")) {
            return filterStartingWith(List.of("0.5", "1.0", "1.5", "2.0", "3.0"), args[3]);
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
