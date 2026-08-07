package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.chat.QuickMessage;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Détecte quand un joueur sélectionne (en scrollant/appuyant sur 1-9) un bloc de couleur
 * "message rapide" dans sa hotbar pendant le temps d'attente après un point (ou à la
 * victoire finale), et envoie alors le message correspondant dans le chat de l'arène —
 * voir {@link QuickMessage} et {@link GameManager#sendQuickChatMessage}.
 *
 * On déclenche sur la SÉLECTION du slot (PlayerItemHeldEvent), pas sur un clic/interaction
 * classique : c'est plus rapide (pas besoin de viser/cliquer), ça fonctionne même en mode
 * spectateur (les interactions y sont très restreintes), et ça correspond exactement à
 * l'usage "je n'ai pas le temps d'écrire dans le chat, je veux juste presser 1/2/3/4".
 */
public class QuickChatListener implements Listener {

    /** Anti-spam : évite qu'un scroll rapide dans la hotbar déclenche une rafale de messages. */
    private static final long COOLDOWN_MILLIS = 1500;

    private final HikaBrainPlugin plugin;
    private final Map<UUID, Long> lastSentAt = new HashMap<>();

    public QuickChatListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        if (gm == null) return;

        // Les blocs de message rapide ne sont dans la hotbar que pendant le temps
        // d'attente après un point (ROUND_RESET) ou à l'écran de victoire (ENDING).
        GameState state = gm.getState();
        if (state != GameState.ROUND_RESET && state != GameState.ENDING) return;

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        QuickMessage message = QuickMessage.fromItem(newItem);
        if (message == null) return;

        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) return;
        lastSentAt.put(player.getUniqueId(), now);

        gm.sendQuickChatMessage(player, message);
    }
}
