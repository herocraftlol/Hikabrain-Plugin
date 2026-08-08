package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.chat.QuickMessage;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Détecte quand un joueur fait un CLIC (gauche OU droit) en tenant un bloc de couleur
 * "message rapide" pendant le temps d'attente après un point (ou à la victoire finale),
 * et envoie alors le message correspondant dans le chat de l'arène — voir
 * {@link QuickMessage} et {@link GameManager#sendQuickChatMessage}.
 */
public class QuickChatListener implements Listener {

    /** Anti-spam : évite qu'un clic maintenu déclenche une rafale de messages. */
    private static final long COOLDOWN_MILLIS = 1500;

    private final HikaBrainPlugin plugin;
    private final Map<UUID, Long> lastSentAt = new HashMap<>();

    public QuickChatListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean isClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!isClick) return;

        Player player = event.getPlayer();
        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        if (gm == null) return;

        // Les blocs de message rapide ne sont dans la hotbar que pendant le temps
        // d'attente après un point (ROUND_RESET) ou à l'écran de victoire (ENDING).
        GameState state = gm.getState();
        if (state != GameState.ROUND_RESET && state != GameState.ENDING) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        QuickMessage message = QuickMessage.fromItem(item);
        if (message == null) return;

        // Empêche le clic de casser/placer un bloc regardé, ou toute autre interaction
        // (ex: ouvrir un conteneur) pendant que ce bloc sert de message rapide.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) return;
        lastSentAt.put(player.getUniqueId(), now);

        gm.sendQuickChatMessage(player, message);
    }
}
