package fr.cabintransport.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Référence brute vers un point (monde + coordonnées) qui ne résout le
 * monde Bukkit qu'au moment où on en a besoin, et non au chargement de la
 * configuration. Cela évite de perdre un trajet si son monde n'est pas
 * encore chargé au démarrage du plugin (ordre de chargement des plugins,
 * monde créé par un autre plugin type Multiverse, etc.).
 */
public class PointRef {

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PointRef(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static PointRef of(Location location) {
        World world = location.getWorld();
        return new PointRef(
                world != null ? world.getName() : "world",
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()
        );
    }

    /** Résout le monde et renvoie la Location, ou null si le monde n'est pas actuellement chargé. */
    public Location resolve() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
