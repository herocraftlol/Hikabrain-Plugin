package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.Arena;
import com.hikabrain.plugin.game.ArenaSnapshot;
import com.hikabrain.plugin.game.CuboidRegion;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.util.BoundingBox;

/**
 * Protège la zone de jeu façon WorldGuard maison, pour chaque arène configurée :
 * - Les blocs déjà présents lors de la configuration (capturés dans le snapshot) ne peuvent
 *   jamais être cassés tant qu'ils sont dans leur état d'origine.
 * - Les joueurs peuvent poser des blocs librement dans la zone de jeu (aucune restriction
 *   n'est appliquée à BlockPlaceEvent).
 * - Les blocs posés par un joueur peuvent être cassés normalement (ils ne correspondent
 *   plus à l'état d'origine du snapshot).
 * - En dehors de toute zone de jeu (ou si aucune zone n'est configurée), aucune restriction.
 *
 * - AUCUN item ne doit jamais traîner au sol dans la zone de jeu d'une arène active (kit
 *   cassé accidentellement, bloc du kit détruit par une explosion, etc.) : on annule la
 *   création de l'item dès son apparition (ItemSpawnEvent), ET un balayage périodique
 *   supprime tout ce qui aurait quand même échappé à cette première protection.
 */
public class ArenaProtectionListener implements Listener {

    private final HikaBrainPlugin plugin;

    public ArenaProtectionListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        // Toutes les 5 secondes : filet de sécurité en plus de onItemSpawn ci-dessous,
        // au cas où un item se retrouverait quand même au sol (créé par un autre plugin,
        // déjà présent avant le début de la partie, etc.).
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupGroundItems, 100L, 100L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // On cherche, parmi toutes les arènes actives, celle dont la zone de jeu contient ce bloc.
        // On se base sur la position du bloc plutôt que sur le statut "en partie" du joueur,
        // pour rester correct même dans des cas limites (spectateur, tiers proche de la zone).
        for (GameManager gameManager : plugin.getArenaManager().getAll()) {
            GameState state = gameManager.getState();
            if (state != GameState.PLAYING && state != GameState.ROUND_RESET && state != GameState.STARTING) {
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

    /**
     * Empêche tout item de toucher le sol dans la zone de jeu d'une arène active : casse
     * d'un bloc du kit, item éjecté par une explosion, etc. — rien ne doit jamais y rester.
     */
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Location loc = event.getLocation();
        for (GameManager gameManager : plugin.getArenaManager().getAll()) {
            GameState state = gameManager.getState();
            if (state != GameState.PLAYING && state != GameState.ROUND_RESET
                    && state != GameState.STARTING && state != GameState.ENDING) {
                continue;
            }
            if (gameManager.getArena().isInGameZone(loc)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Filet de sécurité périodique : supprime toute entité "item au sol" qui se
     * trouverait quand même dans la zone de jeu d'une arène active.
     */
    private void cleanupGroundItems() {
        for (GameManager gameManager : plugin.getArenaManager().getAll()) {
            GameState state = gameManager.getState();
            if (state != GameState.PLAYING && state != GameState.ROUND_RESET
                    && state != GameState.STARTING && state != GameState.ENDING) {
                continue;
            }

            CuboidRegion zone = gameManager.getArena().getGameZone();
            if (zone == null) continue;

            Location c1 = zone.getCorner1();
            Location c2 = zone.getCorner2();
            if (c1 == null || c2 == null || c1.getWorld() == null) continue;

            BoundingBox box = BoundingBox.of(c1, c2).expand(1.0);
            for (Entity entity : c1.getWorld().getNearbyEntities(box)) {
                if (entity instanceof Item) {
                    entity.remove();
                }
            }
        }
    }
}
