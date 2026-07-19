package com.hikabrain.plugin.tournament;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Relie les événements Bukkit (mort, déconnexion, reconnexion, sortie de zone) au
 * {@link TournamentManager}, pour les matchs de tournoi qui utilisent le moteur de
 * duel interne (1v1/2v2/FFA/Faction). Les matchs HikaBrain sont eux gérés par les
 * listeners existants du plugin + le callback de fin de partie du GameManager.
 */
public class TournamentListener implements Listener {

    private final HikaBrainPlugin plugin;

    public TournamentListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (plugin.getTournamentManager().getRuntime(victim.getUniqueId()) == null) {
            return;
        }
        Player killer = victim.getKiller();
        plugin.getTournamentManager().onDuelDeath(victim, killer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTournamentManager().onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getTournamentManager().onPlayerJoin(event.getPlayer());
    }

    /** Empêche un compétiteur de tomber en dehors des limites configurées de l'arène de duel. */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        MatchRuntime rt = plugin.getTournamentManager().getRuntime(player.getUniqueId());
        if (rt == null || rt.getArena().getBounds() == null) return;
        if (rt.getArena().isOutOfBounds(player.getLocation()) && rt.getAlive(rt.slotIndexOf(player.getUniqueId())).contains(player.getUniqueId())) {
            // Simule une "mort" hors-limites : on le renvoie tuer par le vide en abaissant sa vie à 0.
            player.setHealth(0.0);
        }
    }
}
