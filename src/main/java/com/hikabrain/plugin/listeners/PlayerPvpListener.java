package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.Team;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Restreint les dégâts entre joueurs sur une arène HikaBrain :
 * - Dans le lobby d'attente (avant que la partie ne commence), aucun joueur ne peut
 *   taper un autre joueur, peu importe l'équipe.
 * - Pendant la partie (2v2, 3v3, 4v4...), les coéquipiers ne peuvent pas se blesser
 *   entre eux (friendly fire désactivé), mais les coups entre équipes adverses restent
 *   autorisés normalement.
 */
public class PlayerPvpListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerPvpListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player damager = resolveDamagingPlayer(event.getDamager());
        if (damager == null || damager.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        GameManager victimArena = plugin.getArenaManager().findArenaOf(victim);
        GameManager damagerArena = plugin.getArenaManager().findArenaOf(damager);

        // Les deux joueurs doivent être engagés dans la même arène pour que la règle s'applique.
        if (victimArena == null || damagerArena == null || victimArena != damagerArena) {
            return;
        }

        GameManager gm = victimArena;
        GameState state = gm.getState();

        // Lobby d'attente (ou compte à rebours) : aucun coup autorisé entre joueurs.
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }

        // En partie (y compris le gel de démarrage) : on bloque uniquement les coups entre coéquipiers.
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING) {
            Team victimTeam = gm.getTeam(victim);
            Team damagerTeam = gm.getTeam(damager);
            if (victimTeam != null && victimTeam == damagerTeam) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Détermine le joueur à l'origine des dégâts : soit directement l'attaquant s'il s'agit
     * d'un joueur, soit le tireur d'un projectile lancé par un joueur.
     */
    private Player resolveDamagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
