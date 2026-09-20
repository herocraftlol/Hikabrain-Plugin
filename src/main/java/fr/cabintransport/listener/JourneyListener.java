package fr.cabintransport.listener;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class JourneyListener implements Listener {

    private final HikaBrainPlugin plugin;

    public JourneyListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getDiscoveryManager().tryStart(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getJourneyManager().isTraveling(event.getPlayer())) {
            plugin.getJourneyManager().cancel(event.getPlayer());
        }
    }

    /** Le joueur est invulnérable pendant le trajet (chute, vide, suffocation dans le décor...). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getJourneyManager().isTraveling(player)) {
            event.setCancelled(true);
        }
    }
}
