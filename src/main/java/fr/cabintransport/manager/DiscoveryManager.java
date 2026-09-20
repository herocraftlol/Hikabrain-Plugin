package fr.cabintransport.manager;

import com.hikabrain.plugin.HikaBrainPlugin;
import fr.cabintransport.model.Route;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Lance la visite du lobby une seule fois par joueur. */
public class DiscoveryManager {
    private final HikaBrainPlugin plugin;
    private final Set<UUID> seen = new HashSet<>();
    private final File file;

    public DiscoveryManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discovery-seen.yml");
        load();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String value : yaml.getStringList("players")) {
            try { seen.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
    }

    public boolean enabled() { return plugin.getCabinConfig().getBoolean("discovery.enabled", true); }

    public void tryStart(Player player) {
        if (!enabled() || seen.contains(player.getUniqueId())) return;
        String routeId = plugin.getCabinConfig().getString("discovery.route", "lobby-tour");
        Route route = plugin.getRouteManager().get(routeId);
        if (route == null || !route.isReady()) return;
        if (plugin.getJourneyManager().startDiscovery(player, route)) {
            player.sendMessage(plugin.getCabinConfig().getString("discovery.message", "&bDécouverte du lobby..."));
        }
    }

    public void complete(Player player) {
        if (seen.add(player.getUniqueId())) save();
    }

    public boolean hasSeen(UUID uuid) { return seen.contains(uuid); }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("players", seen.stream().map(UUID::toString).toList());
        try { yaml.save(file); } catch (IOException e) { plugin.getLogger().warning("Impossible de sauvegarder discovery-seen.yml: " + e.getMessage()); }
    }
}
