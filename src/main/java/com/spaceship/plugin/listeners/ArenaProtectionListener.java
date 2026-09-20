package com.spaceship.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.spaceship.plugin.game.Arena;
import com.spaceship.plugin.game.ArenaSnapshot;
import com.spaceship.plugin.game.GameManager;
import com.spaceship.plugin.game.GameState;
import com.spaceship.plugin.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Protège la zone de jeu façon WorldGuard maison, pour chaque arène configurée :
 * - Les blocs déjà présents lors de la configuration (capturés dans le snapshot) ne peuvent
 *   jamais être cassés tant qu'ils sont dans leur état d'origine.
 * - Les joueurs peuvent poser des blocs librement dans la zone de jeu, SAUF à proximité
 *   immédiate d'un point de spawn (voir onBlockPlace) : cela évite qu'un joueur ne se fasse
 *   enfermer ou piéger dès son apparition (ou celle d'un adversaire).
 * - Les blocs posés par un joueur peuvent être cassés normalement (ils ne correspondent
 *   plus à l'état d'origine du snapshot).
 * - En dehors de toute zone de jeu (ou si aucune zone n'est configurée), aucune restriction.
 */
public class ArenaProtectionListener implements Listener {

    private final HikaBrainPlugin plugin;

    public ArenaProtectionListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // On cherche, parmi toutes les arènes actives, celle dont la zone de jeu contient ce bloc.
        // On se base sur la position du bloc plutôt que sur le statut "en partie" du joueur,
        // pour rester correct même dans des cas limites (spectateur, tiers proche de la zone).
        for (GameManager gameManager : plugin.getSpaceArenaManager().getAll()) {
            GameState state = gameManager.getState();
            if (state != GameState.PLAYING && state != GameState.ROUND_RESET) {
                continue;
            }

            Arena arena = gameManager.getArena();
            if (!arena.isInGameZone(event.getBlock().getLocation())) {
                continue;
            }

            ArenaSnapshot snapshot = gameManager.getArenaSnapshot();
            if (snapshot.isUnmodifiedOriginalBlock(event.getBlock())) {
                event.setCancelled(true);
            }
            return;
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Même principe que onBlockBreak : on cherche l'arène active dont la zone de jeu
        // contient ce bloc, puis on interdit de poser à proximité d'un spawn (n'importe
        // laquelle des salles, pas seulement la salle actuellement active) pour empêcher
        // qu'un joueur bloque, enferme ou piège un spawn.
        for (GameManager gameManager : plugin.getSpaceArenaManager().getAll()) {
            GameState state = gameManager.getState();
            if (state != GameState.PLAYING && state != GameState.ROUND_RESET) {
                continue;
            }

            Arena arena = gameManager.getArena();
            if (!arena.isInGameZone(event.getBlock().getLocation())) {
                continue;
            }

            double radius = plugin.getSpaceConfig().getDouble("spawn-protection-radius", 3.0);
            if (arena.isNearAnySpawn(event.getBlock().getLocation(), radius)) {
                event.setCancelled(true);
                MessageUtil.send(event.getPlayer(), "&cImpossible de construire aussi près d'un spawn !");
            }
            return;
        }
    }
}
