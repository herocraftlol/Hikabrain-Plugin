package com.hikabrain.plugin.tournament.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.tournament.Tournament;
import com.hikabrain.plugin.tournament.TournamentTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hologramme affichant le podium (champion / 2e / 3e) du dernier tournoi terminé
 * à un endroit configurable via /tournament sethologram.
 *
 * Un seul hologramme actif à la fois : chaque nouveau tournoi terminé remplace
 * l'affichage précédent.
 */
public class TournamentHologramManager {

    private static final double LINE_GAP = 0.27;

    private final HikaBrainPlugin plugin;
    private final File file;
    private final NamespacedKey pdcKey;

    private Location location;
    private final List<UUID> lineEntities = new ArrayList<>();

    public TournamentHologramManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "tournaments");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "hologram.yml");
        this.pdcKey = new NamespacedKey(plugin, "tournament_hologram");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isSet("location.world")) return;
        World world = plugin.getServer().getWorld(config.getString("location.world"));
        if (world == null) return;
        location = new Location(world, config.getDouble("location.x"), config.getDouble("location.y"), config.getDouble("location.z"));
        purgeOrphans();
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        if (location != null) {
            config.set("location.world", location.getWorld().getName());
            config.set("location.x", location.getX());
            config.set("location.y", location.getY());
            config.set("location.z", location.getZ());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder l'emplacement de l'hologramme de tournoi : " + e.getMessage());
        }
    }

    /** Définit (ou déplace) l'emplacement de l'hologramme et affiche le podium actuel si connu. */
    public void setLocation(Location loc) {
        despawn();
        this.location = loc.clone();
        save();
    }

    public void remove() {
        despawn();
        this.location = null;
        save();
    }

    public boolean hasLocation() {
        return location != null;
    }

    /** Reconstruit l'hologramme pour afficher le podium du tournoi qui vient de se terminer. */
    public void display(Tournament tournament) {
        if (location == null) return;
        despawn();

        List<String> lines = new ArrayList<>();
        lines.add("§6§l🏆 " + tournament.getName().toUpperCase());
        lines.add("§7Format : §f" + tournament.getFormat().getLabel());
        lines.add(" ");
        lines.add("§6🏆 Champion : §f" + safeName(tournament.getChampion()));
        if (tournament.getRunnerUp() != null) {
            lines.add("§f🥈 " + safeName(tournament.getRunnerUp()));
        }
        for (TournamentTeam t : tournament.getThirdPlace()) {
            lines.add("§c🥉 " + safeName(t));
        }

        double y = location.getY() + (lines.size() - 1) * LINE_GAP;
        for (String line : lines) {
            Location lineLoc = location.clone();
            lineLoc.setY(y);
            spawnLine(lineLoc, line);
            y -= LINE_GAP;
        }
    }

    private String safeName(TournamentTeam team) {
        return team == null ? "?" : team.getDisplayName();
    }

    private void spawnLine(Location loc, String text) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setCustomNameVisible(true);
            as.customName(Component.text(text));
            AttributeInstance scaleAttr = as.getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttr != null) scaleAttr.setBaseValue(1.0);
            as.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
        });
        lineEntities.add(stand.getUniqueId());
    }

    private void despawn() {
        for (UUID uuid : lineEntities) {
            org.bukkit.entity.Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null) entity.remove();
        }
        lineEntities.clear();
    }

    /** Supprime tout ArmorStand orphelin (issu d'un redémarrage) marqué par ce plugin autour de la location. */
    private void purgeOrphans() {
        if (location == null || location.getWorld() == null) return;
        for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location, 5, 5, 5)) {
            if (entity instanceof ArmorStand && entity.getPersistentDataContainer().has(pdcKey, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    public void shutdown() {
        despawn();
    }
}
