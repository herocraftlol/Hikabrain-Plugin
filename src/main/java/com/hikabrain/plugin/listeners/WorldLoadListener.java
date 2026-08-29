package com.hikabrain.plugin.listeners;

import org.bukkit.GameRule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Applique le respawn instantané (GameRule.DO_IMMEDIATE_RESPAWN) à tout monde chargé
 * APRÈS le démarrage du plugin — HikaBrainPlugin#onEnable ne l'applique qu'aux mondes
 * déjà chargés à ce moment-là ; sans ce listener, un monde chargé plus tard (arène dans
 * un monde à part chargé à la demande, par exemple) redemanderait un clic manuel sur
 * "Respawn" après chaque mort, ce qu'on ne veut jamais dans HikaBrain.
 */
public class WorldLoadListener implements Listener {

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
    }
}
