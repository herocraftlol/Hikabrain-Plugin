package com.spaceship.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.spaceship.plugin.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Gère la détection des kills et deaths pour les statistiques (globales + individuelles).
 */
public class PlayerDeathListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerDeathListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        GameManager gm = plugin.getSpaceArenaManager().findArenaOf(victim);

        if (gm == null) return;
        if (!gm.isPlaying(victim)) return;

        int teamSize = Math.max(gm.getPlayerCountForTeam(com.spaceship.plugin.game.Team.BLACK),
                                gm.getPlayerCountForTeam(com.spaceship.plugin.game.Team.WHITE));

        Player killer = victim.getKiller();

        if (killer != null && gm.isPlaying(killer) && !killer.equals(victim)) {
            plugin.getSpaceStatsManager().addKill(gm.getTeam(killer), teamSize);
            plugin.getSpaceStatsManager().addPlayerKill(killer.getUniqueId(), killer.getName(), teamSize);
            gm.addKill(gm.getTeam(killer));
            gm.addPlayerKill(killer.getUniqueId());
        }

        plugin.getSpaceStatsManager().addDeath(gm.getTeam(victim), teamSize);
        plugin.getSpaceStatsManager().addPlayerDeath(victim.getUniqueId(), victim.getName(), teamSize);
        gm.addDeath(gm.getTeam(victim));
        gm.addPlayerDeath(victim.getUniqueId());
    }
}
