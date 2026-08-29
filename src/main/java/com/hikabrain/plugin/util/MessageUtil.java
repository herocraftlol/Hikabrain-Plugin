package com.hikabrain.plugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Petite classe utilitaire pour traduire les codes couleurs (&) et envoyer des messages formatés.
 */
public final class MessageUtil {

    private MessageUtil() {
    }

    public static String format(String raw) {
        if (raw == null) return "";
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Convertit un texte avec codes couleurs legacy (&c, &l...) en Component Adventure —
     * utile pour tout ce qui utilise l'API de chat moderne (voir CosmeticChatListener),
     * plutôt qu'un simple String.
     */
    public static Component formatComponent(String raw) {
        if (raw == null) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(format(raw));
    }

    public static void send(CommandSender target, String raw) {
        if (raw == null || raw.isEmpty()) return;
        target.sendMessage(format(raw));
    }
}
