package fr.spide.listener;

import fr.spide.GameManager;
import fr.spide.model.SpideMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerStateListener implements Listener {

    private final GameManager gameManager;

    public PlayerStateListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Location hub = gameManager.getHub();
        if (hub != null) {
            event.getPlayer().teleport(hub);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nettoyage complet (équipe, spectateurs, scoreboard) quel que soit l'état du joueur,
        // pour ne jamais laisser un emplacement d'équipe bloqué par un joueur déconnecté.
        gameManager.leaveGame(event.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        SpideMap map = gameManager.getMapOfPlayer(player.getUniqueId());
        if (map == null) return; // pas dans une partie -> comportement vanilla

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            gameManager.eliminatePlayer(player, map);
        } else {
            // Dans Spide, seule la chute élimine : aucune autre source de dégâts n'est autorisée.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Élimine immédiatement un joueur actif qui tombe sous le bas de la zone sélectionnée
        // (au lieu d'attendre qu'il tombe dans le vide du monde, ce qui pouvait prendre
        // très longtemps si la map est haute dans le ciel).
        SpideMap gameMap = gameManager.getMapOfPlayer(player.getUniqueId());
        if (gameMap != null) {
            Double minY = gameMap.getMinY();
            if (minY != null && event.getTo() != null && event.getTo().getY() < minY) {
                gameManager.eliminatePlayer(player, gameMap);
                return;
            }
        }

        if (player.getGameMode() != GameMode.SPECTATOR) return;

        // Couvre à la fois les vrais spectateurs (rejoints via le GUI/clic sur une partie
        // en cours) et les joueurs de la partie elle-même une fois éliminés ou en
        // spectateur libre de fin de partie (les deux passent en gamemode SPECTATOR).
        SpideMap map = gameManager.getMapOfSpectator(player.getUniqueId());
        if (map == null) map = gameManager.getMapOfPlayer(player.getUniqueId());
        if (map == null) return;
        if (map.getPos1() == null || map.getPos2() == null) return;

        if (!map.isInside(event.getTo())) {
            // Bloque toute sortie de la zone de jeu pour les spectateurs.
            event.setTo(event.getFrom());
        }
    }
}
