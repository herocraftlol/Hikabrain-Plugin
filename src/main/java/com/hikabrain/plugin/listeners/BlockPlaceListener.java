package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Empêche de poser des blocs dans les zones de capture.
 */
public class BlockPlaceListener implements Listener {

    private final HikaBrainPlugin plugin;

    public BlockPlaceListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        
        if (gm == null) return;
        
        // Vérifier uniquement pendant la partie (PLAYING ou ROUND_RESET)
        if (gm.getState() != GameState.PLAYING && gm.getState() != GameState.ROUND_RESET) {
            return;
        }
        
        // Vérifier si le bloc est placé dans une zone de capture
        // Les deux équipes - on interdit dans les deux zones de capture
        if (gm.getArena().isInCaptureZone(com.hikabrain.plugin.game.Team.RED, event.getBlock().getLocation()) ||
            gm.getArena().isInCaptureZone(com.hikabrain.plugin.game.Team.BLUE, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
