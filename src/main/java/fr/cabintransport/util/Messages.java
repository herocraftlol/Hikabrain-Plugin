package fr.cabintransport.util;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class Messages {

    private final HikaBrainPlugin plugin;

    public Messages(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    private String raw(String key) {
        return plugin.getCabinConfig().getString("messages." + key, key);
    }

    private String prefix() {
        return raw("prefix");
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null, null);
    }

    public void send(CommandSender sender, String key, String placeholder, String value) {
        String msg = raw(key);
        if (placeholder != null) {
            msg = msg.replace(placeholder, value);
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix() + msg));
    }
}
