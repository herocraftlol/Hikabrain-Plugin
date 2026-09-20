package fr.cabintransport.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Représente un trajet configurable entre un point A et un point B.
 * Les points sont stockés en référence brute (PointRef) et résolus vers
 * un monde Bukkit uniquement à l'usage, pour ne jamais perdre un trajet
 * si son monde n'est pas encore chargé au démarrage du plugin.
 */
public class Route {

    private final String id;
    private String displayName;
    private PointRef start;
    private PointRef end;
    private int durationTicks = 200; // 10s par défaut
    private double arcHeight = 20.0;
    private Particle particle = Particle.CLOUD;
    private Sound soundStart = Sound.ENTITY_ENDER_DRAGON_FLAP;
    private Sound soundLoop = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    private Sound soundEnd = Sound.ENTITY_PLAYER_LEVELUP;
    private boolean cabinVisual = true;
    private Material cabinBlock = Material.OAK_PLANKS;
    /** Si true, la caméra du joueur est orientée dans le sens du déplacement (effet "vehicule"). */
    private boolean lockCamera = true;
    private final List<CameraPoint> cameraPoints = new ArrayList<>();

    public Route(String id) {
        this.id = id;
        this.displayName = id;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public PointRef getStart() {
        return start;
    }

    public void setStart(PointRef start) {
        this.start = start;
    }

    public void setStart(Location location) {
        this.start = PointRef.of(location);
    }

    public PointRef getEnd() {
        return end;
    }

    public void setEnd(PointRef end) {
        this.end = end;
    }

    public void setEnd(Location location) {
        this.end = PointRef.of(location);
    }

    /** Résout le point de départ vers une Location, ou null si le monde n'est pas chargé. */
    public Location getStartLocation() {
        return start == null ? null : start.resolve();
    }

    /** Résout le point d'arrivée vers une Location, ou null si le monde n'est pas chargé. */
    public Location getEndLocation() {
        return end == null ? null : end.resolve();
    }

    public int getDurationTicks() {
        return hasCameraPath() ? getCameraPathDurationTicks() : durationTicks;
    }

    public void setDurationSeconds(double seconds) {
        int target = Math.max(1, (int) Math.round(seconds * 20.0));
        if (hasCameraPath()) {
            int current = getCameraPathDurationTicks();
            if (current > 0) for (CameraPoint point : cameraPoints) {
                point.setTransitionSeconds(point.getTransitionSeconds() * target / (double) current);
            }
        } else this.durationTicks = target;
    }

    public double getDurationSeconds() {
        return getDurationTicks() / 20.0;
    }

    public double getArcHeight() {
        return arcHeight;
    }

    public void setArcHeight(double arcHeight) {
        this.arcHeight = arcHeight;
    }

    public Particle getParticle() {
        return particle;
    }

    public void setParticle(Particle particle) {
        this.particle = particle;
    }

    public Sound getSoundStart() {
        return soundStart;
    }

    public void setSoundStart(Sound soundStart) {
        this.soundStart = soundStart;
    }

    public Sound getSoundLoop() {
        return soundLoop;
    }

    public void setSoundLoop(Sound soundLoop) {
        this.soundLoop = soundLoop;
    }

    public Sound getSoundEnd() {
        return soundEnd;
    }

    public void setSoundEnd(Sound soundEnd) {
        this.soundEnd = soundEnd;
    }

    public boolean isCabinVisual() {
        return cabinVisual;
    }

    public void setCabinVisual(boolean cabinVisual) {
        this.cabinVisual = cabinVisual;
    }

    public Material getCabinBlock() {
        return cabinBlock;
    }

    public void setCabinBlock(Material cabinBlock) {
        this.cabinBlock = cabinBlock;
    }

    public boolean isLockCamera() {
        return lockCamera;
    }

    public void setLockCamera(boolean lockCamera) {
        this.lockCamera = lockCamera;
    }

    public List<CameraPoint> getCameraPoints() { return Collections.unmodifiableList(cameraPoints); }
    public void clearCameraPoints() { cameraPoints.clear(); }
    public void addCameraPoint(CameraPoint point) { cameraPoints.add(point); }
    public boolean hasCameraPath() { return cameraPoints.size() >= 2; }
    public int getCameraPathDurationTicks() {
        return cameraPoints.stream().limit(Math.max(0, cameraPoints.size() - 1))
                .mapToInt(CameraPoint::getTransitionTicks).sum();
    }
    public org.bukkit.Location getCameraPointLocation(int index) {
        return cameraPoints.get(index).getPoint().resolve();
    }

    /** Interpolation douce (smoothstep) de la position et de l'angle entre deux points. */
    public org.bukkit.Location locationAt(double progress) {
        if (!hasCameraPath()) return null;
        progress = Math.max(0, Math.min(1, progress));
        double total = getCameraPathDurationTicks();
        double target = progress * total, cursor = 0;
        for (int i = 0; i < cameraPoints.size() - 1; i++) {
            CameraPoint a = cameraPoints.get(i), b = cameraPoints.get(i + 1);
            double duration = a.getTransitionTicks();
            if (target <= cursor + duration || i == cameraPoints.size() - 2) {
                double t = Math.max(0, Math.min(1, (target - cursor) / duration));
                t = t * t * (3.0 - 2.0 * t);
                org.bukkit.Location from = a.getPoint().resolve(), to = b.getPoint().resolve();
                if (from == null || to == null) return null;
                float yaw = interpolateAngle(from.getYaw(), to.getYaw(), t);
                float pitch = (float)(from.getPitch() + (to.getPitch() - from.getPitch()) * t);
                return new org.bukkit.Location(from.getWorld(),
                        from.getX() + (to.getX() - from.getX()) * t,
                        from.getY() + (to.getY() - from.getY()) * t,
                        from.getZ() + (to.getZ() - from.getZ()) * t, yaw, pitch);
            }
            cursor += duration;
        }
        return getCameraPointLocation(cameraPoints.size() - 1);
    }

    private float interpolateAngle(float from, float to, double t) {
        float delta = ((to - from + 540f) % 360f) - 180f;
        return from + (float)(delta * t);
    }

    /** true si le trajet a bien un départ et une arrivée définis (même si le monde n'est pas chargé). */
    public boolean isComplete() {
        return hasCameraPath() || (start != null && end != null);
    }

    /** true si le trajet est complet ET que son monde est actuellement chargé. */
    public boolean isReady() {
        if (hasCameraPath()) {
            for (CameraPoint point : cameraPoints) if (point.getPoint().resolve() == null) return false;
            return true;
        }
        return isComplete() && getStartLocation() != null && getEndLocation() != null;
    }
}
