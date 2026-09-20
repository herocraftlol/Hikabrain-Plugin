package fr.cabintransport.journey;

import fr.cabintransport.model.Route;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Représente un voyage en cours pour un joueur donné.
 * Le joueur est déplacé directement (téléportation à chaque tick) le long
 * de la trajectoire, entouré d'un décor de cabine optionnel.
 */
public class Journey {

    private final UUID playerUuid;
    private final Route route;
    private final List<BlockDisplay> cabinEntities = new ArrayList<>();

    private BukkitTask task;
    private int currentTick = 0;

    public Journey(Player player, Route route) {
        this.playerUuid = player.getUniqueId();
        this.route = route;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Route getRoute() {
        return route;
    }

    public List<BlockDisplay> getCabinEntities() {
        return cabinEntities;
    }

    public BukkitTask getTask() {
        return task;
    }

    public void setTask(BukkitTask task) {
        this.task = task;
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public void incrementTick() {
        this.currentTick++;
    }

    public double progress() {
        return Math.min(1.0, currentTick / (double) route.getDurationTicks());
    }
}
