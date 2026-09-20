package fr.cabintransport.command;

import com.hikabrain.plugin.HikaBrainPlugin;
import fr.cabintransport.model.Route;
import fr.cabintransport.model.CameraPoint;
import fr.cabintransport.model.PointRef;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TransportCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "list", "go", "cancel", "create", "setstart", "setend",
            "setduration", "setheight", "setcabin", "setcamera", "delete", "reload", "save"
            , "addpoint", "clearpoints", "setpointduration"
    );

    private final HikaBrainPlugin plugin;

    public TransportCommand(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> handleList(sender);
            case "go" -> handleGo(sender, args);
            case "cancel" -> handleCancel(sender);
            case "create" -> handleCreate(sender, args);
            case "setstart" -> handleSetPoint(sender, args, true);
            case "setend" -> handleSetPoint(sender, args, false);
            case "setduration" -> handleSetDuration(sender, args);
            case "setheight" -> handleSetHeight(sender, args);
            case "setcabin" -> handleSetCabin(sender, args);
            case "setcamera" -> handleSetCamera(sender, args);
            case "addpoint" -> handleAddPoint(sender, args);
            case "clearpoints" -> handleClearPoints(sender, args);
            case "setpointduration" -> handlePointDuration(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "reload" -> handleReload(sender);
            case "save" -> handleSave(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b&lCabinTransport &8- &7/transport list|go <trajet>|cancel"));
        if (sender.hasPermission("transport.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Admin: &8/transport create|setstart|setend|setduration|setheight|setcabin|setcamera|delete|reload|save <trajet>"));
            sender.sendMessage(ChatColor.GRAY + "Points caméra: /transport addpoint <trajet> <durée-vers-point-suivant> | clearpoints | setpointduration <trajet> <index> <secondes>");
        }
    }

    private void handleList(CommandSender sender) {
        plugin.getMessages().send(sender, "route-list-header");
        for (Route route : plugin.getRouteManager().getRoutes()) {
            String status;
            if (!route.isComplete()) {
                status = ChatColor.RED + " (incomplet)";
            } else if (!route.isReady()) {
                status = ChatColor.YELLOW + " (monde non charge)";
            } else {
                status = "";
            }
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.AQUA + route.getId()
                    + ChatColor.GRAY + " : " + route.getDisplayName() + status);
        }
    }

    private void handleGo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("transport.use")) {
            plugin.getMessages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) {
            plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]);
            return;
        }
        if (plugin.getJourneyManager().isTraveling(player)) {
            plugin.getMessages().send(sender, "already-traveling");
            return;
        }
        if (!route.isComplete()) {
            plugin.getMessages().send(sender, "route-incomplete");
            return;
        }
        boolean started = plugin.getJourneyManager().start(player, route);
        if (started) {
            plugin.getMessages().send(sender, "journey-start", "%route%", route.getDisplayName());
        } else if (!route.isReady()) {
            plugin.getMessages().send(sender, "world-not-loaded");
        } else {
            plugin.getMessages().send(sender, "different-world-error");
        }
    }

    private void handleCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        if (!plugin.getJourneyManager().isTraveling(player)) {
            plugin.getMessages().send(sender, "not-traveling");
            return;
        }
        plugin.getJourneyManager().cancel(player);
        plugin.getMessages().send(sender, "journey-cancelled");
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String id = args[1];
        if (plugin.getRouteManager().exists(id)) {
            sender.sendMessage(ChatColor.RED + "Ce trajet existe déjà.");
            return;
        }
        Route route = plugin.getRouteManager().createRoute(id);
        if (args.length > 2) {
            String display = String.join(" ", List.of(args).subList(2, args.length));
            route.setDisplayName(display);
        }
        plugin.getMessages().send(sender, "route-created", "%route%", id);
        plugin.getRouteManager().save();
    }

    private void handleSetPoint(CommandSender sender, String[] args, boolean isStart) {
        if (!checkAdmin(sender)) return;
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) {
            plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]);
            return;
        }
        if (isStart) {
            route.setStart(player.getLocation().clone());
        } else {
            route.setEnd(player.getLocation().clone());
        }
        plugin.getMessages().send(sender, "point-set", "%point%", isStart ? "depart" : "arrivee");
        plugin.getRouteManager().save();
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) {
            plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]);
            return;
        }
        try {
            double seconds = Double.parseDouble(args[2]);
            route.setDurationSeconds(seconds);
            plugin.getMessages().send(sender, "route-updated", "%route%", route.getId());
            plugin.getRouteManager().save();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Nombre invalide.");
        }
    }

    private void handleSetHeight(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) {
            plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]);
            return;
        }
        try {
            double height = Double.parseDouble(args[2]);
            route.setArcHeight(height);
            plugin.getMessages().send(sender, "route-updated", "%route%", route.getId());
            plugin.getRouteManager().save();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Nombre invalide.");
        }
    }

    private void handleSetCabin(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) {
            plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]);
            return;
        }
        route.setCabinVisual(Boolean.parseBoolean(args[2]));
        plugin.getMessages().send(sender, "route-updated", "%route%", route.getId());
        plugin.getRouteManager().save();
    }

    private void handleSetCamera(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) {
            plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]);
            return;
        }
        route.setLockCamera(Boolean.parseBoolean(args[2]));
        plugin.getMessages().send(sender, "route-updated", "%route%", route.getId());
        plugin.getRouteManager().save();
    }

    private void handleAddPoint(CommandSender sender, String[] args) {
        if (!checkAdmin(sender) || !(sender instanceof Player player) || args.length < 3) return;
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) { plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]); return; }
        try {
            double seconds = Double.parseDouble(args[2]);
            route.addCameraPoint(new CameraPoint(PointRef.of(player.getLocation()), seconds));
            plugin.getRouteManager().save();
            sender.sendMessage(ChatColor.GREEN + "Point caméra " + route.getCameraPoints().size() + " ajouté avec yaw="
                    + Math.round(player.getLocation().getYaw()) + " pitch=" + Math.round(player.getLocation().getPitch()) + ".");
        } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Durée invalide."); }
    }

    private void handleClearPoints(CommandSender sender, String[] args) {
        if (!checkAdmin(sender) || args.length < 2) return;
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) { plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]); return; }
        route.clearCameraPoints(); plugin.getRouteManager().save();
        sender.sendMessage(ChatColor.GREEN + "Points caméra supprimés.");
    }

    private void handlePointDuration(CommandSender sender, String[] args) {
        if (!checkAdmin(sender) || args.length < 4) return;
        Route route = plugin.getRouteManager().get(args[1]);
        if (route == null) { plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]); return; }
        try {
            int index = Integer.parseInt(args[2]) - 1;
            double seconds = Double.parseDouble(args[3]);
            if (index < 0 || index >= route.getCameraPoints().size() - 1) { sender.sendMessage(ChatColor.RED + "Index invalide (le dernier point n'a pas de transition). "); return; }
            route.getCameraPoints().get(index).setTransitionSeconds(seconds);
            plugin.getRouteManager().save();
            sender.sendMessage(ChatColor.GREEN + "Durée de transition du point " + (index + 1) + " mise à jour.");
        } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Index ou durée invalide."); }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        if (!plugin.getRouteManager().exists(args[1])) {
            plugin.getMessages().send(sender, "unknown-route", "%route%", args[1]);
            return;
        }
        plugin.getRouteManager().deleteRoute(args[1]);
        plugin.getMessages().send(sender, "route-deleted", "%route%", args[1]);
        plugin.getRouteManager().save();
    }

    private void handleReload(CommandSender sender) {
        if (!checkAdmin(sender)) return;
        plugin.reloadConfig();
        plugin.getRouteManager().load();
        plugin.getMessages().send(sender, "config-reloaded");
    }

    private void handleSave(CommandSender sender) {
        if (!checkAdmin(sender)) return;
        plugin.getRouteManager().save();
        plugin.getMessages().send(sender, "config-saved");
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("transport.admin")) {
            plugin.getMessages().send(sender, "no-permission");
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            out.addAll(SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList()));
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            out.addAll(plugin.getRouteManager().getRoutes().stream()
                    .map(Route::getId)
                    .filter(id -> id.startsWith(prefix))
                    .collect(Collectors.toList()));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("setcabin") || args[0].equalsIgnoreCase("setcamera"))) {
            out.addAll(List.of("true", "false"));
        }
        return out;
    }
}
