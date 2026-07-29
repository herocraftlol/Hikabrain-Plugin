package fr.spide.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpideMap {

    private final String name;
    private MapState state = MapState.MAINTENANCE;

    private Location pos1;
    private Location pos2;
    private boolean regionConfirmed = false;

    private final List<Team> teams = new ArrayList<>();
    private Location lobby;
    private int pointsToWin = 1;

    // 1 = un seul bloc détruit (défaut), N = cube NxNxN, -1 = pierce (traverse tout, rayon 1 fixe)
    private int radius = 1;
    private boolean pierce = false;

    // Spectateurs actuellement dans cette map (hors joueurs de la partie)
    private final List<UUID> spectators = new ArrayList<>();

    // -1 = non configuré -> on retombe sur getTotalRequiredPlayers() (comportement historique :
    // il faut remplir toutes les équipes pour démarrer).
    private int minPlayers = -1;
    private int maxPlayers = -1;

    // Photo de la zone au moment du dernier /sp <map> posconfirm, utilisée pour régénérer
    // l'arène à chaque manche. Volontairement non persistée (mémoire uniquement) : il suffit
    // de relancer posconfirm après un redémarrage du serveur si la map a été reconstruite.
    private transient fr.spide.ArenaSnapshot snapshot;

    public SpideMap(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public MapState getState() {
        return state;
    }

    public void setState(MapState state) {
        this.state = state;
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public boolean isRegionConfirmed() {
        return regionConfirmed;
    }

    public void setRegionConfirmed(boolean regionConfirmed) {
        this.regionConfirmed = regionConfirmed;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public Team getTeam(String color) {
        for (Team t : teams) {
            if (t.getColor().equalsIgnoreCase(color)) return t;
        }
        return null;
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
    }

    public int getPointsToWin() {
        return pointsToWin;
    }

    public void setPointsToWin(int pointsToWin) {
        this.pointsToWin = pointsToWin;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
        this.pierce = false;
    }

    public boolean isPierce() {
        return pierce;
    }

    public void setPierce(boolean pierce) {
        this.pierce = pierce;
        if (pierce) this.radius = 1;
    }

    public List<UUID> getSpectators() {
        return spectators;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    /** @return le minimum de joueurs pour démarrer le décompte, ou le total requis si non configuré. */
    public int getEffectiveMinPlayers() {
        return minPlayers > 0 ? minPlayers : getTotalRequiredPlayers();
    }

    /** @return le maximum de joueurs (démarre la partie immédiatement une fois atteint). */
    public int getEffectiveMaxPlayers() {
        return maxPlayers > 0 ? maxPlayers : getTotalRequiredPlayers();
    }

    public fr.spide.ArenaSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(fr.spide.ArenaSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public int getTotalRequiredPlayers() {
        int total = 0;
        for (Team t : teams) total += t.getRequiredPlayers();
        return total;
    }

    public int getTotalCurrentPlayers() {
        int total = 0;
        for (Team t : teams) total += t.getMembers().size();
        return total;
    }

    public boolean isLobbyFull() {
        if (teams.isEmpty()) return false;
        for (Team t : teams) {
            if (!t.isFull()) return false;
        }
        return true;
    }

    public boolean areAllSpawnsFilled() {
        if (teams.isEmpty()) return false;
        for (Team t : teams) {
            if (!t.isSpawnsFull()) return false;
        }
        return true;
    }

    /** Une map est complètement configurée quand tous les prérequis de /sp sont remplis. */
    public boolean isFullyConfigured() {
        return regionConfirmed && pos1 != null && pos2 != null
                && teams.size() >= 2 && areAllSpawnsFilled()
                && lobby != null && pointsToWin > 0;
    }

    /** Recalcule automatiquement l'état MAINTENANCE / AVAILABLE (ne touche pas à OCCUPIED). */
    public void refreshState() {
        if (state == MapState.OCCUPIED) return;
        state = isFullyConfigured() ? MapState.AVAILABLE : MapState.MAINTENANCE;
    }

    /** @return le Y le plus bas de la zone sélectionnée, ou null si la zone n'est pas définie. */
    public Double getMinY() {
        if (pos1 == null || pos2 == null) return null;
        return Math.min(pos1.getY(), pos2.getY());
    }

    public boolean isInside(Location loc) {
        if (pos1 == null || pos2 == null || loc == null) return true;
        World w = pos1.getWorld();
        if (!loc.getWorld().equals(w)) return false;
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    public void resetGame() {
        for (Team t : teams) {
            t.resetGame();
            t.resetScore();
        }
        spectators.clear();
    }
}
