package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Affiche le TITRE cosmétique équipé (voir CosmeticManager, catégorie TAG) à côté du
 * pseudo de l'expéditeur dans le chat — c'était le bug signalé : le titre existait bien
 * en boutique (achat/équipement fonctionnels) mais n'était jamais réellement affiché
 * nulle part, {@link com.hikabrain.plugin.cosmetics.CosmeticManager#getActiveTag}
 * n'étant appelé par aucun autre code.
 *
 * Comme pour tous les cosmétiques, ne s'affiche QUE quand ils sont actifs pour ce
 * joueur, donc jamais en arène (getActiveTag renvoie alors null tout seul).
 */
public class CosmeticChatListener implements Listener {

    private final HikaBrainPlugin plugin;

    public CosmeticChatListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String tagText = plugin.getCosmeticManager().getActiveTag(sender.getUniqueId());
        if (tagText == null || tagText.isBlank()) return;

        Component tagComponent = MessageUtil.formatComponent(tagText);

        // Le texte du titre (voir CosmeticRegistry) est déjà écrit comme un SUFFIXE
        // (avec un espace au début, ex: " ★ Légende ★") : on l'ajoute donc juste après
        // le nom affiché de l'expéditeur, pour tous les destinataires du message.
        event.renderer((source, sourceDisplayName, message, viewer) ->
                sourceDisplayName.append(tagComponent).append(Component.text(": ")).append(message));
    }
}
