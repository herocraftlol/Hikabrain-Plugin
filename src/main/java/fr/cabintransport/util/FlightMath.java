package fr.cabintransport.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Calculs de trajectoire pour le déplacement de la cabine.
 * La trajectoire suit une parabole entre le point de départ et
 * le point d'arrivée : y(t) = lerp(y0,y1,t) + arcHeight * 4t(1-t)
 * ce qui donne l'effet "grand saut" / "voyage sur un nuage".
 */
public final class FlightMath {

    private FlightMath() {
    }

    /**
     * Position interpolée le long de l'arc au temps t (0..1), avec
     * yaw/pitch orientés dans le sens du déplacement.
     */
    public static Location positionAt(Location start, Location end, double arcHeight, double t) {
        t = clamp(t, 0.0, 1.0);
        double x = lerp(start.getX(), end.getX(), t);
        double z = lerp(start.getZ(), end.getZ(), t);
        double y = lerp(start.getY(), end.getY(), t) + arcHeight * 4.0 * t * (1.0 - t);

        // point légèrement plus loin sur la courbe pour déduire la direction (tangente)
        double t2 = clamp(t + 0.01, 0.0, 1.0);
        double x2 = lerp(start.getX(), end.getX(), t2);
        double z2 = lerp(start.getZ(), end.getZ(), t2);
        double y2 = lerp(start.getY(), end.getY(), t2) + arcHeight * 4.0 * t2 * (1.0 - t2);

        Location loc = new Location(start.getWorld(), x, y, z);
        Vector direction = new Vector(x2 - x, y2 - y, z2 - z);
        if (direction.lengthSquared() > 1.0E-6) {
            loc.setDirection(direction);
        } else {
            loc.setYaw(start.getYaw());
            loc.setPitch(0f);
        }
        return loc;
    }

    /**
     * Convertit un décalage local (côté / haut / avant) en décalage monde,
     * en fonction du yaw courant, afin que la cabine reste orientée dans
     * le sens du déplacement.
     */
    public static Vector localToWorldOffset(double side, double up, double forward, float yawDegrees) {
        double yawRad = Math.toRadians(yawDegrees);
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double worldX = side * rightX + forward * dirX;
        double worldZ = side * rightZ + forward * dirZ;
        return new Vector(worldX, up, worldZ);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
