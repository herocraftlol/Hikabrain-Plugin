package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Empêche de poser des blocs dans les zones de capture et les zones de spawn.
 */
public class BlockPlaceListener implements Listener {

    private static final int SPAWN_RADIUS = 3; // Rayon autour du spawn où les blocs sont interdits

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
        
        Location blockLoc = event.getBlock().getLocation();
        
        // Vérifier si le bloc est placé dans une zone de capture (les deux équipes)
        if (gm.getArena().isInCaptureZone(Team.RED, blockLoc) ||
            gm.getArena().isInCaptureZone(Team.BLUE, blockLoc)) {
            event.setCancelled(true);
            return;
        }
        
        // Vérifier si le bloc est placé dans une zone de spawn (les deux équipes)
        Location redSpawn = gm.getArena().getSpawn(Team.RED);
        Location blueSpawn = gm.getArena().getSpawn(Team.BLUE);
        
        if (isInSpawnZone(blockLoc, redSpawn) || isInSpawnZone(blockLoc, blueSpawn)) {
            event.setCancelled(true);
        }
    }

    /**
     * Vérifie si une location est dans la zone de spawn (rayon de 3 blocs autour du spawn)
     */
    private boolean isInSpawnZone(Location blockLoc, Location spawnLoc) {
        if (spawnLoc == null || blockLoc.getWorld() == null || !blockLoc.getWorld().equals(spawnLoc.getWorld())) {
            return false;
        }
        
        int dx = Math.abs(blockLoc.getBlockX() - spawnLoc.getBlockX());
        int dy = Math.abs(blockLoc.getBlockY() - spawnLoc.getBlockY());
        int dz = Math.abs(blockLoc.getBlockZ() - spawnLoc.getBlockZ());
        
        return dx <= SPAWN_RADIUS && dy <= SPAWN_RADIUS && dz <= SPAWN_RADIUS;
    }
}
