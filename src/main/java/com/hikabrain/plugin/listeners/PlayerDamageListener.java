package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.KitManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Dans un minijeu comme HikaBrain, mourir (peu importe la cause : coup d'épée, chute,
 * lave, vide, noyade...) ne doit pas sortir le joueur de la partie : on le fait
 * respawn directement au spawn de son équipe, comme sur les serveurs type Hypixel.
 * On lui redonne aussi une pomme dorée fraîche à chaque mort.
 *
 * Les dégâts de chute sont par ailleurs entièrement désactivés pour tout joueur engagé
 * sur une arène HikaBrain (en lobby, en partie ou en spectateur) : ce mode ne doit
 * jamais infliger de dégâts de chute, quelle que soit la hauteur.
 */
public class PlayerDamageListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerDamageListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isActiveGameState(GameState state) {
        return state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING;
    }

    /**
     * Annule tous les dégâts de chute pour un joueur engagé (lobby, partie ou spectateur)
     * sur une arène HikaBrain. On ne veut aucun dégât de chute dans ce mode.
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        boolean inArena = plugin.getArenaManager().findArenaOf(player) != null
                || plugin.getArenaManager().findSpectatorArenaOf(player) != null;
        if (inArena) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        GameManager gameManager = plugin.getArenaManager().findArenaOf(player);

        if (gameManager == null || !isActiveGameState(gameManager.getState())) {
            return;
        }

        // On vide les drops pour ne pas perdre l'inventaire entre les morts (comportement minijeu).
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /**
     * Se déclenche au moment précis où le joueur respawn (après l'écran de mort, ou
     * immédiatement si doImmediateRespawn est activé). On y fixe directement la
     * position de respawn pour éviter un aller-retour visible au spawn du monde,
     * et on redonne une pomme dorée fraîche.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameManager gameManager = plugin.getArenaManager().findArenaOf(player);

        if (gameManager == null || !isActiveGameState(gameManager.getState())) {
            return;
        }

        // Utilise le spawn fixe attribué au joueur pour toute la partie (voir
        // GameManager#getAssignedSpawn), pour qu'il respawn toujours au même endroit.
        Location spawn = gameManager.getAssignedSpawn(player);
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }

        // setHealth/setFoodLevel/inventaire doivent être appliqués au tick suivant : juste après
        // le respawn, certaines valeurs (santé, faim, inventaire) ne sont pas encore stabilisées.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setHealth(20);
                player.setFoodLevel(20);
                KitManager.regiveGoldenApple(player);
            }
        });
    }
}
