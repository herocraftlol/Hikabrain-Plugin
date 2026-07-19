package com.hikabrain.plugin.tournament;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.CuboidRegion;
import com.hikabrain.plugin.tournament.history.TournamentHistoryManager;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Commande principale du système de tournoi : /tournament <sous-commande> ...
 *
 * Voir /tournament help en jeu pour la liste complète.
 */
public class TournamentCommand implements CommandExecutor, TabCompleter {

    private final HikaBrainPlugin plugin;

    public TournamentCommand(HikaBrainPlugin plugin) {
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
            case "create": return handleCreate(sender, args);
            case "join": return handleJoin(sender, args);
            case "leave": return handleLeave(sender, args);
            case "start": return handleStart(sender, args, false);
            case "forcestart": return handleStart(sender, args, true);
            case "cancel": return handleCancel(sender, args);
            case "delete": return handleDelete(sender, args);
            case "list": return handleList(sender);
            case "info": return handleInfo(sender, args);
            case "bracket": return handleBracket(sender, args);
            case "matches": return handleMatches(sender, args);
            case "spectate": return handleSpectate(sender, args);
            case "unspectate": return handleUnspectate(sender);
            case "top": return handleTop(sender, args);
            case "history": return handleHistory(sender);
            case "gui": return handleGui(sender);
            case "rooms": return handleRooms(sender, args);
            case "arena": return handleArena(sender, args);
            case "sethologram": return handleSetHologram(sender);
            case "removehologram": return handleRemoveHologram(sender);
            case "help": default: sendHelp(sender); return true;
        }
    }

    // ================= CRÉATION / CYCLE DE VIE =================

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return true;
        Player player = (Player) sender;
        if (args.length < 4) {
            MessageUtil.send(sender, "&cUsage: /tournament create <nom> <1v1|2v2|ffa|faction|hikabrain> <places> [bo] [points] [temps_s] [arene_hikabrain]");
            return true;
        }
        String name = args[1];
        TournamentFormat format = TournamentFormat.fromString(args[2]);
        if (format == null) {
            MessageUtil.send(sender, "&cFormat inconnu. Formats valides : 1v1, 2v2, ffa, faction, hikabrain.");
            return true;
        }
        int size;
        try {
            size = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "&cLe nombre de places doit être un nombre (idéalement une puissance de 2 : 4, 8, 16, 32, 64).");
            return true;
        }
        int bestOf = args.length > 4 ? parseIntOr(args[4], 3) : 3;
        int pointsToWin = args.length > 5 ? parseIntOr(args[5], 0) : 0;
        int timeLimit = args.length > 6 ? parseIntOr(args[6], plugin.getConfig().getInt("tournament.default-time-limit-seconds", 600)) : plugin.getConfig().getInt("tournament.default-time-limit-seconds", 600);
        String hikabrainArena = args.length > 7 ? args[7] : null;

        List<String> rules = plugin.getConfig().getStringList("tournament.default-rules");

        TournamentManager.CreateResult result = plugin.getTournamentManager().create(
                name, format, format.getDefaultTeamSize(), size, bestOf, pointsToWin, timeLimit, rules,
                player.getUniqueId(), hikabrainArena);

        switch (result) {
            case OK:
                MessageUtil.send(sender, "&aTournoi &f" + name + " &acréé ! Format : &f" + format.getLabel()
                        + " &a| Places : &f" + size + " &a| BO&f" + bestOf);
                MessageUtil.send(sender, "&7Les joueurs peuvent rejoindre avec &f/tournament join " + name + "&7.");
                return true;
            case ALREADY_EXISTS:
                MessageUtil.send(sender, "&cUn tournoi avec ce nom existe déjà.");
                return true;
            case INVALID_SIZE:
                MessageUtil.send(sender, "&cNombre de places invalide (minimum 2).");
                return true;
            case INVALID_ARENA:
                MessageUtil.send(sender, "&cArène HikaBrain introuvable : " + hikabrainArena);
                return true;
        }
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament join <nom> [tag_equipe]");
            return true;
        }
        String teamTag = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
        TournamentManager.JoinResult result = plugin.getTournamentManager().join(args[1], player, teamTag);
        switch (result) {
            case OK: return true;
            case NOT_FOUND: MessageUtil.send(sender, "&cTournoi introuvable."); return true;
            case NOT_OPEN: MessageUtil.send(sender, "&cLes inscriptions sont fermées pour ce tournoi."); return true;
            case FULL: MessageUtil.send(sender, "&cCe tournoi est complet."); return true;
            case ALREADY_REGISTERED: MessageUtil.send(sender, "&cTu es déjà inscrit à ce tournoi."); return true;
            case TEAM_FULL: MessageUtil.send(sender, "&cCette équipe est déjà complète."); return true;
            case TEAM_NAME_REQUIRED: MessageUtil.send(sender, "&cCe format nécessite un tag d'équipe : /tournament join " + args[1] + " <tag>"); return true;
        }
        return true;
    }

    private boolean handleLeave(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament leave <nom>");
            return true;
        }
        boolean ok = plugin.getTournamentManager().leave(args[1], (Player) sender);
        if (!ok) MessageUtil.send(sender, "&cImpossible de quitter ce tournoi (introuvable, déjà commencé, ou tu n'es pas inscrit).");
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args, boolean force) {
        if (!requirePermission(sender, "hikabrain.admin")) return true;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament " + (force ? "forcestart" : "start") + " <nom>");
            return true;
        }
        TournamentManager.StartResult result = plugin.getTournamentManager().start(args[1]);
        switch (result) {
            case OK: MessageUtil.send(sender, "&aLe tournoi &f" + args[1] + " &avient de démarrer !"); return true;
            case NOT_FOUND: MessageUtil.send(sender, "&cTournoi introuvable."); return true;
            case NOT_ENOUGH_TEAMS: MessageUtil.send(sender, "&cIl faut au moins 2 équipes/joueurs inscrits pour démarrer."); return true;
            case ALREADY_STARTED: MessageUtil.send(sender, "&cCe tournoi a déjà démarré ou est terminé."); return true;
        }
        return true;
    }

    private boolean handleCancel(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hikabrain.admin")) return true;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament cancel <nom>");
            return true;
        }
        boolean ok = plugin.getTournamentManager().cancel(args[1]);
        MessageUtil.send(sender, ok ? "&aTournoi annulé." : "&cTournoi introuvable.");
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hikabrain.admin")) return true;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament delete <nom>");
            return true;
        }
        boolean ok = plugin.getTournamentManager().delete(args[1]);
        MessageUtil.send(sender, ok ? "&aTournoi supprimé." : "&cTournoi introuvable.");
        return true;
    }

    // ================= AFFICHAGE =================

    private boolean handleList(CommandSender sender) {
        if (plugin.getTournamentManager().getAll().isEmpty()) {
            MessageUtil.send(sender, "&7Aucun tournoi enregistré. Utilise /tournament create pour en créer un.");
            return true;
        }
        MessageUtil.send(sender, "&6&lTournois :");
        for (Tournament t : plugin.getTournamentManager().getAll()) {
            String state = switch (t.getState()) {
                case REGISTRATION -> "&aInscriptions ouvertes";
                case IN_PROGRESS -> "&eEn cours";
                case FINISHED -> "&7Terminé";
                case CANCELLED -> "&cAnnulé";
            };
            MessageUtil.send(sender, "&f- " + t.getName() + " &7(" + t.getFormat().getLabel() + ") " + state
                    + " &7(" + t.getRegistered().size() + "/" + t.getMaxSlots() + ")");
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament info <nom>");
            return true;
        }
        Tournament t = plugin.getTournamentManager().get(args[1]);
        if (t == null) {
            MessageUtil.send(sender, "&cTournoi introuvable.");
            return true;
        }
        MessageUtil.send(sender, "&6&l" + t.getName());
        MessageUtil.send(sender, "&7Format : &f" + t.getFormat().getLabel() + " &7| Places : &f" + t.getRegistered().size() + "/" + t.getMaxSlots());
        MessageUtil.send(sender, "&7BO&f" + t.getBestOf() + (t.getPointsToWin() > 0 ? " &7| Objectif : &f" + t.getPointsToWin() + " points" : ""));
        MessageUtil.send(sender, "&7Temps limite par manche : &f" + t.getTimeLimitSeconds() + "s");
        MessageUtil.send(sender, "&7État : &f" + t.getState());
        if (!t.getRules().isEmpty()) {
            MessageUtil.send(sender, "&7Règles :");
            for (String r : t.getRules()) MessageUtil.send(sender, "&7 - &f" + r);
        }
        if (t.getState() == TournamentState.FINISHED) {
            MessageUtil.send(sender, "&6🏆 Champion : &f" + safeName(t.getChampion()));
            MessageUtil.send(sender, "&f🥈 " + safeName(t.getRunnerUp()));
            for (TournamentTeam third : t.getThirdPlace()) {
                MessageUtil.send(sender, "&c🥉 " + safeName(third));
            }
        } else {
            MessageUtil.send(sender, "&7Inscrits :");
            for (TournamentTeam team : t.getRegistered()) {
                MessageUtil.send(sender, "&7 - &f" + team.getDisplayName());
            }
        }
        return true;
    }

    private boolean handleBracket(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament bracket <nom>");
            return true;
        }
        Tournament t = plugin.getTournamentManager().get(args[1]);
        if (t == null) {
            MessageUtil.send(sender, "&cTournoi introuvable.");
            return true;
        }
        if (t.getRounds().isEmpty()) {
            MessageUtil.send(sender, "&7Le bracket n'a pas encore été généré (le tournoi n'a pas démarré).");
            return true;
        }
        for (int i = 0; i < t.getRounds().size(); i++) {
            List<BracketMatch> round = t.getRounds().get(i);
            String roundName = com.hikabrain.plugin.tournament.util.BracketUtil.roundName(round.size(), i == t.getRounds().size() - 1 && round.size() == 1);
            MessageUtil.send(sender, "&6&l" + roundName + " &7(tour " + (i + 1) + ")");
            for (BracketMatch m : round) {
                String winnerTag = m.getQualified().isEmpty() ? "" : " &a→ " + m.getQualified().get(0).getDisplayName();
                MessageUtil.send(sender, "&7  " + m.getDisplayVersus() + winnerTag);
            }
        }
        return true;
    }

    private boolean handleMatches(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament matches <nom>");
            return true;
        }
        Tournament t = plugin.getTournamentManager().get(args[1]);
        if (t == null) {
            MessageUtil.send(sender, "&cTournoi introuvable.");
            return true;
        }
        List<String> lines = plugin.getTournamentManager().describeOngoingMatches(t);
        if (lines.isEmpty()) {
            MessageUtil.send(sender, "&7Aucun match en cours.");
            return true;
        }
        MessageUtil.send(sender, "&6&lMatchs du tour actuel :");
        for (String line : lines) MessageUtil.send(sender, line);
        return true;
    }

    private boolean handleSpectate(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament spectate <nom>");
            return true;
        }
        boolean ok = plugin.getTournamentManager().spectate(args[1], (Player) sender);
        if (!ok) MessageUtil.send(sender, "&cImpossible de rejoindre ce tournoi en spectateur (introuvable ou aucun match en cours).");
        return true;
    }

    private boolean handleUnspectate(CommandSender sender) {
        if (!requirePlayer(sender)) return true;
        plugin.getTournamentManager().unspectate((Player) sender);
        MessageUtil.send(sender, "&7Tu as quitté le mode spectateur.");
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        TournamentHistoryManager history = plugin.getTournamentHistoryManager();
        String category = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "wins";
        if (category.equals("kills")) {
            MessageUtil.send(sender, "&6&lTop kills (toutes manches, tous tournois) :");
            int rank = 1;
            for (TournamentHistoryManager.PlayerStat s : history.getTopByKills(10)) {
                MessageUtil.send(sender, "&f" + rank++ + ". " + s.name + " &7- &f" + s.totalKills + " kills");
            }
            TournamentHistoryManager.PlayerStat record = history.getKillRecordHolder();
            if (record != null) {
                MessageUtil.send(sender, "&cRecord de kills dans un même match : &f" + record.name + " &7(" + record.bestKillsInAMatch + ")");
            }
        } else {
            MessageUtil.send(sender, "&6&lTop victoires de tournoi :");
            int rank = 1;
            for (TournamentHistoryManager.PlayerStat s : history.getTopByWins(10)) {
                MessageUtil.send(sender, "&f" + rank++ + ". " + s.name + " &7- &f" + s.tournamentsWon + " tournoi(s) gagné(s)");
            }
        }
        return true;
    }

    private boolean handleHistory(CommandSender sender) {
        List<TournamentHistoryManager.Entry> recent = plugin.getTournamentHistoryManager().getRecentHistory(10);
        if (recent.isEmpty()) {
            MessageUtil.send(sender, "&7Aucun tournoi terminé pour l'instant.");
            return true;
        }
        MessageUtil.send(sender, "&6&lHistorique des tournois :");
        for (TournamentHistoryManager.Entry e : recent) {
            MessageUtil.send(sender, "&f" + e.name + " &7(" + e.format + ", " + TournamentHistoryManager.formatDate(e.finishedAt) + ")");
            MessageUtil.send(sender, "&7  🏆 " + e.champion + " &7| 🥈 " + e.runnerUp
                    + (e.thirdPlace.isEmpty() ? "" : " &7| 🥉 " + String.join(", ", e.thirdPlace)));
            MessageUtil.send(sender, "&7  Participants: &f" + e.participants + " &7| Temps moyen/match: &f"
                    + String.format(Locale.FRANCE, "%.0f", e.averageMatchSeconds) + "s &7| Meilleur joueur: &f"
                    + e.bestPlayerName + " (" + e.bestPlayerKills + " kills)");
        }
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!requirePlayer(sender)) return true;
        plugin.getTournamentGUI().open((Player) sender);
        return true;
    }

    private boolean handleRooms(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        if (args.length < 2) {
            MessageUtil.send(sender, "&cUsage: /tournament rooms <nom>");
            return true;
        }
        Player player = (Player) sender;
        Tournament tournament = plugin.getTournamentManager().get(args[1]);
        if (tournament == null) {
            MessageUtil.send(sender, "&cTournoi introuvable.");
            return true;
        }
        if (!plugin.getTournamentRoomsGUI().canOpen(tournament)) {
            MessageUtil.send(sender, "&cCe menu n'est disponible que pendant que le tournoi est en cours.");
            return true;
        }
        plugin.getTournamentRoomsGUI().open(player, tournament);
        return true;
    }

    private boolean handleSetHologram(CommandSender sender) {
        if (!requirePlayerAdmin(sender)) return true;
        Player player = (Player) sender;
        plugin.getTournamentHologramManager().setLocation(player.getLocation());
        MessageUtil.send(sender, "&aEmplacement de l'hologramme des vainqueurs défini ici.");
        return true;
    }

    private boolean handleRemoveHologram(CommandSender sender) {
        if (!requirePermission(sender, "hikabrain.admin")) return true;
        plugin.getTournamentHologramManager().remove();
        MessageUtil.send(sender, "&aHologramme des vainqueurs supprimé.");
        return true;
    }

    // ================= ARÈNES DE DUEL (1v1/2v2/FFA/Faction) =================

    private boolean handleArena(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return true;
        Player player = (Player) sender;
        if (args.length < 3) {
            MessageUtil.send(sender, "&cUsage: /tournament arena <create|delete|list|setwaiting|setspectator|setbounds1|setbounds2|addspawn|clearspawns> <nom> [slot]");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String name = args[2];
        DuelArenaManager manager = plugin.getDuelArenaManager();

        switch (action) {
            case "create": {
                manager.getOrCreate(name);
                manager.saveAll();
                MessageUtil.send(sender, "&aArène de duel &f" + name + " &acréée.");
                return true;
            }
            case "delete": {
                boolean ok = manager.delete(name);
                manager.saveAll();
                MessageUtil.send(sender, ok ? "&aArène supprimée." : "&cArène introuvable.");
                return true;
            }
            case "list": {
                MessageUtil.send(sender, "&6Arènes de duel : &f" + String.join(", ", manager.getNames()));
                return true;
            }
            case "setwaiting": {
                DuelArena arena = manager.getOrCreate(name);
                arena.setWaitingSpawn(player.getLocation());
                manager.saveAll();
                MessageUtil.send(sender, "&aSpawn d'attente défini pour &f" + name + "&a.");
                return true;
            }
            case "setspectator": {
                DuelArena arena = manager.getOrCreate(name);
                arena.setSpectatorSpawn(player.getLocation());
                manager.saveAll();
                MessageUtil.send(sender, "&aZone spectateur définie pour &f" + name + "&a.");
                return true;
            }
            case "setbounds1": {
                DuelArena arena = manager.getOrCreate(name);
                Location corner2 = arena.getBounds() != null ? arena.getBounds().getCorner2() : player.getLocation();
                arena.setBounds(new CuboidRegion(player.getLocation(), corner2));
                manager.saveAll();
                MessageUtil.send(sender, "&aCoin 1 des limites d'arène défini pour &f" + name + "&a.");
                return true;
            }
            case "setbounds2": {
                DuelArena arena = manager.getOrCreate(name);
                Location corner1 = arena.getBounds() != null ? arena.getBounds().getCorner1() : player.getLocation();
                arena.setBounds(new CuboidRegion(corner1, player.getLocation()));
                manager.saveAll();
                MessageUtil.send(sender, "&aCoin 2 des limites d'arène défini pour &f" + name + "&a.");
                return true;
            }
            case "addspawn": {
                if (args.length < 4) {
                    MessageUtil.send(sender, "&cUsage: /tournament arena addspawn <nom> <slot 0-based>");
                    return true;
                }
                int slot = parseIntOr(args[3], -1);
                if (slot < 0) {
                    MessageUtil.send(sender, "&cIndex de slot invalide.");
                    return true;
                }
                DuelArena arena = manager.getOrCreate(name);
                arena.addSpawn(slot, player.getLocation());
                manager.saveAll();
                MessageUtil.send(sender, "&aSpawn ajouté au slot &f" + slot + " &ade l'arène &f" + name + "&a.");
                return true;
            }
            case "clearspawns": {
                if (args.length < 4) {
                    MessageUtil.send(sender, "&cUsage: /tournament arena clearspawns <nom> <slot 0-based>");
                    return true;
                }
                int slot = parseIntOr(args[3], -1);
                DuelArena arena = manager.getOrCreate(name);
                arena.clearSpawns(slot);
                manager.saveAll();
                MessageUtil.send(sender, "&aSpawns du slot &f" + slot + " &avidés.");
                return true;
            }
            default:
                MessageUtil.send(sender, "&cAction inconnue.");
                return true;
        }
    }

    // ================= UTILITAIRES =================

    private String safeName(TournamentTeam team) {
        return team == null ? "?" : team.getDisplayName();
    }

    private int parseIntOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "&cCette commande est réservée aux joueurs.");
            return false;
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            MessageUtil.send(sender, "&cTu n'as pas la permission d'utiliser cette commande.");
            return false;
        }
        return true;
    }

    private boolean requirePlayerAdmin(CommandSender sender) {
        return requirePlayer(sender) && requirePermission(sender, "hikabrain.admin");
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "&6&l=== Système de tournoi HikaBrain ===");
        MessageUtil.send(sender, "&f/tournament join <nom> [tag] &7- Rejoindre un tournoi");
        MessageUtil.send(sender, "&f/tournament leave <nom> &7- Quitter un tournoi");
        MessageUtil.send(sender, "&f/tournament list &7- Voir les tournois");
        MessageUtil.send(sender, "&f/tournament info <nom> &7- Détails d'un tournoi");
        MessageUtil.send(sender, "&f/tournament bracket <nom> &7- Voir le bracket");
        MessageUtil.send(sender, "&f/tournament matches <nom> &7- Matchs en cours");
        MessageUtil.send(sender, "&f/tournament spectate <nom> &7- Mode spectateur");
        MessageUtil.send(sender, "&f/tournament top [wins|kills] &7- Classements");
        MessageUtil.send(sender, "&f/tournament history &7- Historique des tournois");
        MessageUtil.send(sender, "&f/tournament gui &7- Ouvrir le menu des tournois");
        MessageUtil.send(sender, "&f/tournament rooms <nom> &7- Salles du tournoi en cours (rejoindre/spectate)");
        if (sender.hasPermission("hikabrain.admin")) {
            MessageUtil.send(sender, "&e--- Administration ---");
            MessageUtil.send(sender, "&f/tournament create <nom> <format> <places> [bo] [points] [temps] [arene] &7- Créer");
            MessageUtil.send(sender, "&f/tournament start|forcestart|cancel|delete <nom>");
            MessageUtil.send(sender, "&f/tournament arena <create|delete|list|setwaiting|setspectator|setbounds1|setbounds2|addspawn|clearspawns> ...");
            MessageUtil.send(sender, "&f/tournament sethologram &7- Définir l'emplacement de l'hologramme des vainqueurs");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("join", "leave", "list", "info", "bracket", "matches",
                    "spectate", "unspectate", "top", "history", "gui", "rooms", "help"));
            if (sender.hasPermission("hikabrain.admin")) {
                subs.addAll(Arrays.asList("create", "start", "forcestart", "cancel", "delete", "arena", "sethologram", "removehologram"));
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "join": case "leave": case "info": case "bracket": case "matches":
                case "spectate": case "start": case "forcestart": case "cancel": case "delete": case "rooms":
                    return filter(plugin.getTournamentManager().getAll().stream().map(Tournament::getName).collect(Collectors.toList()), args[1]);
                case "create":
                    return Collections.emptyList();
                case "top":
                    return filter(Arrays.asList("wins", "kills"), args[1]);
                case "arena":
                    return filter(Arrays.asList("create", "delete", "list", "setwaiting", "setspectator", "setbounds1", "setbounds2", "addspawn", "clearspawns"), args[1]);
            }
        }
        if (args.length == 3 && sub.equals("create")) {
            return filter(Arrays.asList("1v1", "2v2", "ffa", "faction", "hikabrain"), args[2]);
        }
        if (args.length == 3 && sub.equals("arena")) {
            return filter(plugin.getDuelArenaManager().getNames(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
