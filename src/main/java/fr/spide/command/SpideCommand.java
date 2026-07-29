package fr.spide.command;

import fr.spide.GameManager;
import fr.spide.gui.MapGUI;
import fr.spide.model.MapState;
import fr.spide.model.SpideMap;
import fr.spide.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpideCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;

    public SpideCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMenu(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "gui":
                return handleGui(sender);
            case "create":
                return handleCreate(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "list":
                return handleList(sender);
            case "join":
                return handleJoin(sender, args);
            case "leave":
                return handleLeave(sender);
            case "teamlist":
                return handleTeamlist(sender, args);
            case "hub":
                return handleHub(sender);
            case "help":
                return handleHelp(sender);
            default:
                return handleMapCommand(sender, args);
        }
    }

    // ------------------------------------------------------------------
    // Menu principal (/sp sans argument) : chaque fonctionnalité est
    // proposée directement dans le chat, cliquable pour être pré-remplie.
    // ------------------------------------------------------------------

    private void sendMenu(CommandSender sender) {
        sender.sendMessage(Component.text("§6§l======== Spide ========"));
        sendClickable(sender, "/sp gui", "Ouvrir le menu de sélection des maps", "/sp gui");
        sendClickable(sender, "/sp list", "Afficher la liste de toutes les maps", "/sp list");
        sendClickable(sender, "/sp join <nom>", "Rejoindre une map directement (sans le GUI)", "/sp join ");
        sendClickable(sender, "/sp leave", "Quitter la partie en cours et retourner au hub", "/sp leave");
        sendClickable(sender, "/sp create <nom>", "Créer une nouvelle map", "/sp create ");
        sendClickable(sender, "/sp delete <nom>", "Supprimer une map et tous ses réglages", "/sp delete ");
        sendClickable(sender, "/sp <nom> info", "Voir toutes les informations d'une map", "/sp ");
        sendClickable(sender, "/sp teamlist", "Voir l'ordre des couleurs d'équipe", "/sp teamlist");
        sendClickable(sender, "/sp teamlist add <couleur>", "Ajouter une couleur d'équipe personnalisée", "/sp teamlist add ");
        sendClickable(sender, "/sp hub", "Définir le hub à votre position", "/sp hub");
        sendClickable(sender, "/sp help", "Afficher l'aide complète du plugin", "/sp help");
        sender.sendMessage(Component.text("§7Clique sur une commande pour la préremplir dans ton chat.")
                .decoration(TextDecoration.ITALIC, false));
    }

    /** Envoie une ligne "§e▶ label §7- description", cliquable pour suggérer la commande "suggestion". */
    private void sendClickable(CommandSender sender, String label, String description, String suggestion) {
        Component line = Component.text("§e▶ §f" + label + " §7- §f" + description)
                .decoration(TextDecoration.ITALIC, false)
                .clickEvent(ClickEvent.suggestCommand(suggestion))
                .hoverEvent(HoverEvent.showText(Component.text("§7Clique pour préremplir : §f" + suggestion)));
        sender.sendMessage(line);
    }

    // ------------------------------------------------------------------
    // /sp help
    // ------------------------------------------------------------------

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage("§6§l======== Aide Spide ========");
        sender.sendMessage("§7Spide est un jeu PvP par équipes : les flèches ne blessent");
        sender.sendMessage("§7jamais mais détruisent des blocs. Le but est de faire tomber");
        sender.sendMessage("§7les joueurs adverses en cassant les blocs de leur base.");
        sender.sendMessage("");
        sender.sendMessage("§e§lCommandes générales");
        sender.sendMessage("§f/sp §7- affiche un menu cliquable avec toutes les fonctionnalités.");
        sender.sendMessage("§f/sp gui §7- ouvre le menu de sélection des maps (double coffre).");
        sender.sendMessage("§7   Gris = vide, §avert clair = disponible§7, orange = maintenance, §crouge = en cours§7.");
        sender.sendMessage("§f/sp list §7- liste toutes les maps existantes et leur état.");
        sender.sendMessage("§f/sp join <nom> §7- rejoint une map directement (comme un clic dans le GUI :");
        sender.sendMessage("§7   en joueur si elle est disponible, en spectateur si une partie y est en cours).");
        sender.sendMessage("§f/sp leave §7- quitte la partie ou le spectateur en cours et retourne au hub.");
        sender.sendMessage("§f/sp hub §7- définit le hub (point de retour) à ta position actuelle.");
        sender.sendMessage("");
        sender.sendMessage("§e§lCréation et suppression de map");
        sender.sendMessage("§f/sp create <nom> §7- crée une map (état initial : maintenance).");
        sender.sendMessage("§f/sp delete <nom> §7- supprime une map et tous ses réglages (zone,");
        sender.sendMessage("§7   équipes, spawns, lobby...). Impossible si une partie y est en cours.");
        sender.sendMessage("§f/sp <nom> info §7- affiche toutes les informations sur une map.");
        sender.sendMessage("");
        sender.sendMessage("§e§lConfiguration d'une map");
        sender.sendMessage("§f/sp <nom> pos1 §7et §f/sp <nom> pos2 §7- définissent les 2 coins de la zone de jeu.");
        sender.sendMessage("§f/sp <nom> posconfirm §7- confirme la zone une fois pos1 et pos2 définis.");
        sender.sendMessage("§f/sp <nom> equipe <nbEquipes> <joueursParEquipe> §7- crée les équipes,");
        sender.sendMessage("§7   avec les couleurs prises dans l'ordre de §f/sp teamlist§7.");
        sender.sendMessage("§f/sp <nom> spawn color <couleur> §7- ajoute un spawn (à ta position)");
        sender.sendMessage("§7   pour l'équipe indiquée, jusqu'à atteindre le nombre de joueurs requis.");
        sender.sendMessage("§f/sp <nom> lobby §7- définit le lobby d'attente et le point d'apparition des spectateurs.");
        sender.sendMessage("§f/sp <nom> point <n> §7- nombre de manches à gagner pour remporter la partie.");
        sender.sendMessage("§f/sp <nom> rayon <n> §7- rayon de destruction des flèches (1 = un seul bloc).");
        sender.sendMessage("§f/sp <nom> rayon pierce §7- les flèches traversent tout sans jamais s'arrêter.");
        sender.sendMessage("§f/sp <nom> reset §7- force la réinitialisation d'une map bloquée (renvoie tout le");
        sender.sendMessage("§7   monde au hub, remet les scores à zéro).");
        sender.sendMessage("§f/sp <nom> joueurs <min> <max> §7- nombre de joueurs minimum (démarre un décompte");
        sender.sendMessage("§7   de 10s une fois atteint) et maximum (démarre la partie immédiatement) pour cette map.");
        sender.sendMessage("§7   Par défaut : min = max = nombre total de places d'équipe (comme avant).");
        sender.sendMessage("§7L'arène est automatiquement régénérée (blocs restaurés) à chaque nouvelle manche,");
        sender.sendMessage("§7à partir du snapshot pris lors du dernier §f/sp <nom> posconfirm§7.");
        sender.sendMessage("");
        sender.sendMessage("§e§lCouleurs d'équipe");
        sender.sendMessage("§f/sp teamlist §7- affiche l'ordre actuel des couleurs d'équipe.");
        sender.sendMessage("§f/sp teamlist add <couleur> §7- ajoute une couleur personnalisée à la fin de la liste.");
        sender.sendMessage("");
        sender.sendMessage("§7Une map devient §averte (disponible)§7 automatiquement dès que : zone confirmée,");
        sender.sendMessage("§7au moins 2 équipes avec tous leurs spawns renseignés, lobby défini et points réglés.");
        sender.sendMessage("§7Tant qu'il manque un élément, elle reste §6orange (maintenance)§7.");
        return true;
    }

    // ------------------------------------------------------------------
    // /sp gui
    // ------------------------------------------------------------------

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSeul un joueur peut ouvrir le GUI.");
            return true;
        }
        MapGUI gui = new MapGUI(gameManager.allMapsOrdered());
        player.openInventory(gui.getInventoryToOpen());
        return true;
    }

    // ------------------------------------------------------------------
    // /sp create
    // ------------------------------------------------------------------

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /sp create <nom>");
            return true;
        }
        String name = args[1];
        if (gameManager.createMap(name)) {
            sender.sendMessage("§aMap §f" + name + " §acréée (en maintenance).");
        } else {
            sender.sendMessage("§cUne map avec ce nom existe déjà.");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // /sp delete
    // ------------------------------------------------------------------

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /sp delete <nom>");
            return true;
        }
        String name = args[1];
        GameManager.DeleteResult result = gameManager.deleteMap(name);
        switch (result) {
            case OK:
                sender.sendMessage("§aMap §f" + name + " §asupprimée avec tous ses paramètres.");
                break;
            case NOT_FOUND:
                sender.sendMessage("§cAucune map nommée §f" + name + "§c.");
                break;
            case OCCUPIED:
                sender.sendMessage("§cImpossible de supprimer §f" + name + "§c : une partie est en cours dessus.");
                break;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // /sp list
    // ------------------------------------------------------------------

    private boolean handleList(CommandSender sender) {
        List<SpideMap> maps = gameManager.allMapsOrdered();
        if (maps.isEmpty()) {
            sender.sendMessage("§eAucune map n'a été créée pour le moment. Utilise §f/sp create <nom>");
            return true;
        }

        sender.sendMessage("§6§l--- Liste des maps (" + maps.size() + ") ---");
        for (SpideMap map : maps) {
            String stateColor;
            String stateLabel;
            switch (map.getState()) {
                case AVAILABLE:
                    stateColor = "§a";
                    stateLabel = "Disponible";
                    break;
                case OCCUPIED:
                    stateColor = "§c";
                    stateLabel = "En cours";
                    break;
                case MAINTENANCE:
                default:
                    stateColor = "§6";
                    stateLabel = "Maintenance";
                    break;
            }

            Component line = Component.text("§f- " + map.getName() + " §7(" + stateColor + stateLabel
                            + "§7) §7- §f" + map.getTotalCurrentPlayers() + "/" + map.getTotalRequiredPlayers() + " joueurs")
                    .decoration(TextDecoration.ITALIC, false)
                    .clickEvent(ClickEvent.suggestCommand("/sp " + map.getName() + " info"))
                    .hoverEvent(HoverEvent.showText(Component.text("§7Clique pour voir les infos de §f" + map.getName())));
            sender.sendMessage(line);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // /sp join <map>
    // ------------------------------------------------------------------

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSeul un joueur peut rejoindre une partie.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /sp join <nom>");
            return true;
        }
        SpideMap map = gameManager.getMap(args[1]);
        if (map == null) {
            sender.sendMessage("§cAucune map nommée §f" + args[1] + "§c.");
            return true;
        }
        switch (map.getState()) {
            case AVAILABLE:
                if (gameManager.joinAsPlayer(player, map)) {
                    sender.sendMessage("§aTu as rejoint §f" + map.getName() + "§a.");
                } else {
                    sender.sendMessage("§cImpossible de rejoindre cette map (équipes complètes).");
                }
                return true;
            case OCCUPIED:
                gameManager.joinAsSpectator(player, map);
                sender.sendMessage("§eTu rejoins §f" + map.getName() + " §een spectateur (partie en cours).");
                return true;
            case MAINTENANCE:
            default:
                sender.sendMessage("§6Cette map est en maintenance.");
                return true;
        }
    }

    // ------------------------------------------------------------------
    // /sp leave
    // ------------------------------------------------------------------

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSeul un joueur peut quitter une partie.");
            return true;
        }
        SpideMap map = gameManager.leaveGame(player);
        if (map == null) {
            sender.sendMessage("§cTu n'es dans aucune partie actuellement.");
        } else {
            sender.sendMessage("§aTu as quitté §f" + map.getName() + " §aet es retourné au hub.");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // /sp teamlist
    // ------------------------------------------------------------------

    private boolean handleTeamlist(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
            boolean added = gameManager.getColorRegistry().add(args[2]);
            sender.sendMessage(added
                    ? "§aCouleur §f" + args[2].toUpperCase() + " §aajoutée à la liste."
                    : "§cCette couleur existe déjà dans la liste.");
            return true;
        }
        List<String> order = gameManager.getColorRegistry().getOrder();
        StringBuilder sb = new StringBuilder("§eOrdre des couleurs d'équipe: §f");
        for (int i = 0; i < order.size(); i++) {
            sb.append(i + 1).append(". ").append(order.get(i));
            if (i < order.size() - 1) sb.append("§7, §f");
        }
        sender.sendMessage(sb.toString());
        return true;
    }

    // ------------------------------------------------------------------
    // /sp hub
    // ------------------------------------------------------------------

    private boolean handleHub(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSeul un joueur peut définir le hub.");
            return true;
        }
        gameManager.setHub(player.getLocation());
        sender.sendMessage("§aHub défini à votre position actuelle.");
        return true;
    }

    // ------------------------------------------------------------------
    // /sp <map> ...
    // ------------------------------------------------------------------

    private boolean handleMapCommand(CommandSender sender, String[] args) {
        String mapName = args[0];
        SpideMap map = gameManager.getMap(mapName);
        if (map == null) {
            sender.sendMessage("§cAucune map nommée §f" + mapName + "§c. Utilise /sp create " + mapName);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /sp " + mapName + " <pos1|pos2|posconfirm|equipe|spawn|lobby|point|rayon|joueurs|info|reset>");
            return true;
        }

        String action = args[1].toLowerCase();

        if (action.equals("info")) {
            return handleInfo(sender, map);
        }

        if (action.equals("reset")) {
            return handleReset(sender, map);
        }

        if (action.equals("joueurs")) {
            return handleJoueurs(sender, map, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette sous-commande nécessite d'être un joueur.");
            return true;
        }

        switch (action) {
            case "pos1":
                map.setPos1(player.getLocation());
                sender.sendMessage("§aPos1 défini pour §f" + mapName + "§a.");
                return true;

            case "pos2":
                map.setPos2(player.getLocation());
                sender.sendMessage("§aPos2 défini pour §f" + mapName + "§a.");
                return true;

            case "posconfirm":
                if (map.getPos1() == null || map.getPos2() == null) {
                    sender.sendMessage("§cDéfinis pos1 et pos2 avant de confirmer.");
                    return true;
                }
                map.setRegionConfirmed(true);
                gameManager.captureSnapshot(map);
                map.refreshState();
                gameManager.save();
                sender.sendMessage("§aZone de jeu confirmée pour §f" + mapName + "§a. Un snapshot a été pris pour"
                        + " régénérer l'arène à chaque manche (relance cette commande si tu reconstruis la map).");
                return true;

            case "equipe":
                return handleEquipe(sender, map, args);

            case "spawn":
                return handleSpawn(sender, player, map, args);

            case "lobby":
                map.setLobby(player.getLocation());
                map.refreshState();
                gameManager.save();
                sender.sendMessage("§aLobby défini pour §f" + mapName + "§a.");
                return true;

            case "point":
                return handlePoint(sender, map, args);

            case "rayon":
                return handleRayon(sender, map, args);

            default:
                sender.sendMessage("§cSous-commande inconnue: " + action);
                return true;
        }
    }

    // ------------------------------------------------------------------
    // /sp <map> info
    // ------------------------------------------------------------------

    private boolean handleInfo(CommandSender sender, SpideMap map) {
        sender.sendMessage("§6§l--- Infos: " + map.getName() + " ---");
        sender.sendMessage("§7État : " + stateLabelColored(map.getState()));
        sender.sendMessage("§7Zone (pos1/pos2) : "
                + (map.getPos1() != null && map.getPos2() != null ? "§adéfinie" : "§cnon définie")
                + (map.isRegionConfirmed() ? " §7(confirmée)" : " §7(non confirmée)"));
        sender.sendMessage("§7Lobby : " + (map.getLobby() != null ? "§adéfini" : "§cnon défini"));
        sender.sendMessage("§7Joueurs min/max : §f" + map.getEffectiveMinPlayers() + "§7/§f" + map.getEffectiveMaxPlayers());
        sender.sendMessage("§7Snapshot de régénération : " + (map.getSnapshot() != null ? "§apris" : "§cnon pris (relance posconfirm)"));
        sender.sendMessage("§7Points pour gagner : §f" + map.getPointsToWin());
        sender.sendMessage("§7Destruction : " + (map.isPierce()
                ? "§epierce §7(les flèches traversent tout)"
                : "§f" + map.getRadius() + " bloc(s)"));
        sender.sendMessage("§7Joueurs : §f" + map.getTotalCurrentPlayers() + "§7/§f" + map.getTotalRequiredPlayers());
        sender.sendMessage("§7Spectateurs : §f" + map.getSpectators().size());

        if (map.getTeams().isEmpty()) {
            sender.sendMessage("§7Équipes : §caucune configurée");
        } else {
            sender.sendMessage("§7Équipes (" + map.getTeams().size() + ") :");
            for (Team t : map.getTeams()) {
                sender.sendMessage("  §f- " + t.getColor() + " §7: §f" + t.getMembers().size() + "/" + t.getRequiredPlayers()
                        + " §7joueurs, spawns §f" + t.getSpawnPoints().size() + "/" + t.getRequiredPlayers()
                        + " §7, score §f" + t.getScore());
            }
        }

        sender.sendMessage("§7Entièrement configurée (peut passer disponible) : "
                + (map.isFullyConfigured() ? "§aoui" : "§cnon"));
        return true;
    }

    private String stateLabelColored(MapState state) {
        switch (state) {
            case AVAILABLE:
                return "§aDisponible";
            case OCCUPIED:
                return "§cEn cours";
            case MAINTENANCE:
            default:
                return "§6Maintenance";
        }
    }

    // ------------------------------------------------------------------
    // Sous-commandes de configuration existantes
    // ------------------------------------------------------------------

    private boolean handleEquipe(CommandSender sender, SpideMap map, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /sp " + map.getName() + " equipe <nb equipes> <joueurs par equipe>");
            return true;
        }
        try {
            int nbTeams = Integer.parseInt(args[2]);
            int playersPerTeam = Integer.parseInt(args[3]);
            if (nbTeams < 2) {
                sender.sendMessage("§cIl faut au minimum 2 équipes.");
                return true;
            }
            gameManager.setupTeams(map, nbTeams, playersPerTeam);
            sender.sendMessage("§a" + nbTeams + " équipes de " + playersPerTeam + " joueur(s) configurées pour §f" + map.getName() + "§a.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLes nombres d'équipes et de joueurs doivent être des entiers.");
        }
        return true;
    }

    private boolean handleSpawn(CommandSender sender, Player player, SpideMap map, String[] args) {
        if (args.length < 4 || !args[2].equalsIgnoreCase("color")) {
            sender.sendMessage("§cUsage: /sp " + map.getName() + " spawn color <couleur>");
            return true;
        }
        String color = args[3];
        Team team = map.getTeam(color);
        if (team == null) {
            sender.sendMessage("§cÉquipe inconnue: " + color + ". Configure d'abord /sp " + map.getName() + " equipe ...");
            return true;
        }
        boolean added = gameManager.addSpawn(map, color, player.getLocation());
        if (added) {
            sender.sendMessage("§aSpawn ajouté pour l'équipe §f" + color.toUpperCase()
                    + " §a(" + team.getSpawnPoints().size() + "/" + team.getRequiredPlayers() + ").");
        } else {
            sender.sendMessage("§cCette équipe a déjà tous ses spawns (" + team.getRequiredPlayers() + ").");
        }
        return true;
    }

    private boolean handlePoint(CommandSender sender, SpideMap map, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /sp " + map.getName() + " point <nombre>");
            return true;
        }
        try {
            int points = Integer.parseInt(args[2]);
            if (points < 1) {
                sender.sendMessage("§cLe nombre de points doit être au moins 1.");
                return true;
            }
            map.setPointsToWin(points);
            map.refreshState();
            gameManager.save();
            sender.sendMessage("§aIl faudra désormais §f" + points + " §apoint(s) pour gagner sur §f" + map.getName() + "§a.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLe nombre de points doit être un entier.");
        }
        return true;
    }

    private boolean handleRayon(CommandSender sender, SpideMap map, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /sp " + map.getName() + " rayon <nombre|pierce>");
            return true;
        }
        if (args[2].equalsIgnoreCase("pierce")) {
            map.setPierce(true);
            gameManager.save();
            sender.sendMessage("§aMode §epierce §aactivé sur §f" + map.getName() + "§a: les flèches traversent tout.");
            return true;
        }
        try {
            int radius = Integer.parseInt(args[2]);
            if (radius < 1) {
                sender.sendMessage("§cLe rayon doit être au moins 1.");
                return true;
            }
            map.setRadius(radius);
            gameManager.save();
            sender.sendMessage("§aRayon de destruction réglé à §f" + radius + "§a sur §f" + map.getName() + "§a.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLe rayon doit être un entier ou 'pierce'.");
        }
        return true;
    }

    private boolean handleJoueurs(CommandSender sender, SpideMap map, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§7Actuellement sur §f" + map.getName() + "§7 : min §f" + map.getEffectiveMinPlayers()
                    + "§7, max §f" + map.getEffectiveMaxPlayers() + "§7.");
            sender.sendMessage("§cUsage: /sp " + map.getName() + " joueurs <min> <max>");
            return true;
        }
        try {
            int min = Integer.parseInt(args[2]);
            int max = Integer.parseInt(args[3]);
            if (!gameManager.setPlayerLimits(map, min, max)) {
                sender.sendMessage("§cValeurs invalides : min doit être >= 2 et <= max.");
                return true;
            }
            sender.sendMessage("§aSur §f" + map.getName() + "§a : minimum §f" + min + " §ajoueur(s) (démarre un"
                    + " décompte de 10s), maximum §f" + max + " §a(démarre la partie immédiatement).");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLe minimum et le maximum doivent être des entiers.");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // /sp <map> reset
    // ------------------------------------------------------------------

    private boolean handleReset(CommandSender sender, SpideMap map) {
        gameManager.forceReset(map);
        sender.sendMessage("§aMap §f" + map.getName() + " §aréinitialisée (joueurs renvoyés au hub, scores remis à zéro).");
        return true;
    }

    // ------------------------------------------------------------------
    // Tab-completion : suggestions réelles dans la barre de chat (touche Tab),
    // pas seulement le menu cliquable de /sp.
    // ------------------------------------------------------------------

    private static final List<String> TOP_LEVEL = Arrays.asList("gui", "create", "delete", "list", "join", "leave", "teamlist", "hub", "help");
    private static final List<String> MAP_ACTIONS = Arrays.asList(
            "pos1", "pos2", "posconfirm", "equipe", "spawn", "lobby", "point", "rayon", "info", "reset", "joueurs");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String current = args[args.length - 1];

        if (args.length == 1) {
            List<String> options = new ArrayList<>(TOP_LEVEL);
            for (SpideMap m : gameManager.allMapsOrdered()) options.add(m.getName());
            return filter(options, current);
        }

        String first = args[0].toLowerCase();

        if (first.equals("teamlist")) {
            if (args.length == 2) return filter(List.of("add"), current);
            return List.of();
        }

        if (first.equals("delete") || first.equals("create") || first.equals("join")) {
            if (args.length == 2 && (first.equals("delete") || first.equals("join"))) {
                return filter(gameManager.allMapsOrdered().stream().map(SpideMap::getName).collect(Collectors.toList()), current);
            }
            return List.of();
        }

        if (first.equals("gui") || first.equals("list") || first.equals("leave") || first.equals("hub") || first.equals("help")) {
            return List.of();
        }

        // À partir d'ici, args[0] est censé être un nom de map existant.
        SpideMap map = gameManager.getMap(first);
        if (map == null) return List.of();

        if (args.length == 2) {
            return filter(MAP_ACTIONS, current);
        }

        String action = args[1].toLowerCase();
        if (args.length == 3) {
            switch (action) {
                case "equipe":
                    return filter(Arrays.asList("2", "3", "4"), current);
                case "spawn":
                    return filter(List.of("color"), current);
                case "point":
                    return filter(Arrays.asList("1", "3", "5"), current);
                case "rayon":
                    return filter(Arrays.asList("1", "4", "pierce"), current);
                case "joueurs":
                    return filter(Arrays.asList("2", "3", "4"), current);
                default:
                    return List.of();
            }
        }

        if (args.length == 4) {
            if (action.equals("equipe")) {
                return filter(Arrays.asList("1", "2", "3"), current);
            }
            if (action.equals("joueurs")) {
                return filter(Arrays.asList("4", "6", "8"), current);
            }
            if (action.equals("spawn") && args[2].equalsIgnoreCase("color")) {
                List<String> colors = map.getTeams().stream().map(Team::getColor).collect(Collectors.toList());
                if (colors.isEmpty()) colors = gameManager.getColorRegistry().getOrder();
                return filter(colors, current);
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String current) {
        String lower = current.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
