package com.hikabrain.plugin.lobby;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Déclenche la visite de découverte du lobby (voir fr.cabintransport.manager.DiscoveryManager)
 * à la connexion d'un joueur — c'était le maillon manquant : DiscoveryManager#tryStart
 * existait déjà (chargement, sauvegarde "déjà vu"...) mais n'était appelé nulle part.
 *
 * Un court délai (30 ticks, 1,5s) laisse le temps au client de finir de charger le monde
 * avant de commencer à le déplacer le long du trajet caméra.
 */
public class LobbyJoinListener implements Listener {

    private final HikaBrainPlugin plugin;

    public LobbyJoinListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getDiscoveryManager().tryStart(player);
            }
        }, 30L);
    }
}
