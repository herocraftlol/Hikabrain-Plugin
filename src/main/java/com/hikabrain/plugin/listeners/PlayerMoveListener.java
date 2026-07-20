package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Gère les déplacements des joueurs :
 * - Immobilisation propre quand le joueur est gelé (après un point marqué, ou pendant
 *   le compte à rebours de démarrage de la partie)
 *   Le joueur peut toujours regarder autour de lui (yaw/pitch conservés).
 * - Mort automatique si le joueur sort de la zone de jeu
 * - Confinement des spectateurs à l'intérieur de la zone de l'arène qu'ils observent
 *   (retéléportation au point central/spectateur dès qu'ils tentent d'en sortir, ce qui
 *   empêche de facto de "traverser les murs" en mode spectateur puisque le noclip du
 *   spectateur est immédiatement annulé par la retéléportation).
 * - Le même confinement s'applique aux joueurs de la partie qui viennent de terminer
 *   (état ENDING, mis en mode spectateur le temps de l'écran de victoire) : ils ne
 *   peuvent pas quitter l'arène avant d'être automatiquement retéléportés au lobby.
 */
public class PlayerMoveListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerMoveListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Confinement des spectateurs, indépendamment du fait qu'ils "jouent" ou non.
        GameManager spectating = plugin.getArenaManager().findSpectatorArenaOf(player);
        if (spectating != null) {
            if (!isSameBlock(event.getFrom(), event.getTo()) && !spectating.isWithinSpectatorBounds(event.getTo())) {
                confineToCenter(player, spectating, event);
            }
            return;
        }

        GameManager gm = plugin.getArenaManager().findArenaOf(player);

        if (gm == null) {
            return;
        }

        // FREEZE propre : bloquer tout déplacement du corps, autoriser la tête
        if (gm.isFrozen(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getBlockX() != to.getBlockX()
                    || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ()) {
                // Conserver yaw/pitch du "to" pour que le joueur puisse regarder
                Location fixed = from.clone();
                fixed.setYaw(to.getYaw());
                fixed.setPitch(to.getPitch());
                event.setTo(fixed);
                // Annuler aussi la vélocité résiduelle (saut, knockback, etc.)
                player.setVelocity(new Vector(0, 0, 0));
            }
            return;
        }

        GameState state = gm.getState();

        // Ignorer les micro-mouvements (juste la tête)
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        // Fin de partie : les joueurs (mis en spectateur pour l'écran de victoire) ne
        // doivent pas pouvoir quitter l'arène avant la téléportation automatique au lobby.
        // On applique exactement le même confinement que pour les vrais spectateurs.
        if (state == GameState.ENDING) {
            if (!gm.isWithinSpectatorBounds(event.getTo())) {
                confineToCenter(player, gm, event);
            }
            return;
        }

        // Pendant PLAYING : vérifier si le joueur sort de la zone
        if (state == GameState.PLAYING) {
            if (!gm.getArena().isInGameZone(player.getLocation())) {
                player.setHealth(0);
            }
        }
    }

    /**
     * Ramène un joueur au point de téléportation "spectateur" de l'arène (le point dédié
     * s'il existe, sinon le centre de la zone de jeu, sinon le lobby), en conservant son
     * orientation actuelle. Utilisé aussi bien pour les vrais spectateurs que pour les
     * joueurs en fin de partie (état ENDING).
     */
    private void confineToCenter(Player player, GameManager gm, PlayerMoveEvent event) {
        Location center = gm.getSpectatorTeleportLocation();
        if (center == null) {
            return;
        }
        Location fixed = center.clone();
        fixed.setYaw(event.getTo().getYaw());
        fixed.setPitch(event.getTo().getPitch());
        event.setTo(fixed);
        // En mode spectateur (noclip), un simple setTo() peut ne pas suffire si le
        // déplacement d'un seul tick est déjà très grand (vol rapide) : on force
        // aussi une téléportation directe pour être certain de ramener le joueur.
        player.teleport(fixed);
    }

    private boolean isSameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
