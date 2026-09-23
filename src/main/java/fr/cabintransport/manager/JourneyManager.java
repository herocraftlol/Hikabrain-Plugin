package fr.cabintransport.manager;

import com.hikabrain.plugin.HikaBrainPlugin;
import fr.cabintransport.journey.Journey;
import fr.cabintransport.model.CabinPart;
import fr.cabintransport.model.Route;
import fr.cabintransport.util.FlightMath;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gère le déplacement en temps réel des joueurs le long des trajets.
 *
 * Le joueur est téléporté directement à chaque tick le long de la
 * trajectoire (et non "monté" sur un véhicule) : c'est la méthode fiable
 * pour que le client affiche un mouvement continu et suivi en temps réel -
 * un ArmorStand-véhicule téléporté à chaque tick ne synchronise pas
 * toujours correctement la position du passager côté client.
 */
public class JourneyManager {

    private final HikaBrainPlugin plugin;
    private final Map<UUID, Journey> activeJourneys = new HashMap<>();
    /** Mémorise si le joueur pouvait voler avant le trajet, pour restaurer son état ensuite. */
    private final Map<UUID, Boolean> previousAllowFlight = new HashMap<>();
    private final Map<UUID, Boolean> previousFlying = new HashMap<>();
    private final Map<UUID, Boolean> previousInvisible = new HashMap<>();
    /**
     * Position du joueur juste AVANT le début d'un voyage de découverte (voir
     * startDiscovery) — utilisée pour l'y ramener exactement à la fin, plutôt que de le
     * laisser au dernier point caméra du trajet (voir #finish). C'était le comportement
     * manquant : sans ce suivi, "finish" téléportait systématiquement vers la fin du
     * trajet, quel que soit le type de voyage.
     */
    private final Map<UUID, Location> discoveryOriginLocation = new HashMap<>();
    private final java.util.Set<UUID> discoveryJourneys = new java.util.HashSet<>();

    public JourneyManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isTraveling(Player player) {
        return activeJourneys.containsKey(player.getUniqueId());
    }

    public Journey getJourney(Player player) {
        return activeJourneys.get(player.getUniqueId());
    }

    public boolean start(Player player, Route route) {
        return startInternal(player, route, false);
    }

    public boolean startDiscovery(Player player, Route route) {
        return startInternal(player, route, true);
    }

    private boolean startInternal(Player player, Route route, boolean discovery) {
        if (isTraveling(player)) {
            return false;
        }
        if (!route.isComplete()) {
            return false;
        }
        Location start = route.hasCameraPath() ? route.locationAt(0) : route.getStartLocation();
        Location end = route.hasCameraPath() ? route.locationAt(1) : route.getEndLocation();
        if (start == null || end == null
                || !start.getWorld().equals(end.getWorld())) {
            return false;
        }

        Journey journey = new Journey(player, route);

        // Autorise le vol le temps du trajet pour éviter toute interférence
        // avec la gravité / les vérifications de mouvement anti-triche.
        previousAllowFlight.put(player.getUniqueId(), player.getAllowFlight());
        previousFlying.put(player.getUniqueId(), player.isFlying());
        previousInvisible.put(player.getUniqueId(), player.isInvisible());
        if (discovery) {
            discoveryJourneys.add(player.getUniqueId());
            discoveryOriginLocation.put(player.getUniqueId(), player.getLocation().clone());
            player.setInvisible(true);
            player.setInvulnerable(true);
            player.setCollidable(false);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));

        player.leaveVehicle();

        Location firstFrame = route.hasCameraPath() ? route.locationAt(0) : FlightMath.positionAt(start, end, route.getArcHeight(), 0.0);
        if (!route.isLockCamera()) {
            firstFrame.setYaw(player.getLocation().getYaw());
            firstFrame.setPitch(player.getLocation().getPitch());
        }
        player.teleport(firstFrame);

        if (route.isCabinVisual()) {
            spawnCabinDecor(journey, firstFrame, route.getCabinBlock());
        }

        activeJourneys.put(player.getUniqueId(), journey);

        player.playSound(firstFrame, route.getSoundStart(), 1.0f, 1.0f);

        journey.setTask(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(journey), 1L, 1L));

        return true;
    }

    private void spawnCabinDecor(Journey journey, Location origin, Material material) {
        List<CabinPart> parts = buildCabinParts(material);
        for (CabinPart part : parts) {
            BlockDisplay display = origin.getWorld().spawn(origin, BlockDisplay.class, bd -> {
                bd.setBlock(part.material().createBlockData());
                bd.setPersistent(false);
                Transformation t = new Transformation(
                        new Vector3f(-part.scaleX() / 2f, -part.scaleY() / 2f, -part.scaleZ() / 2f),
                        new AxisAngle4f(0f, 0f, 1f, 0f),
                        new Vector3f(part.scaleX(), part.scaleY(), part.scaleZ()),
                        new AxisAngle4f(0f, 0f, 1f, 0f)
                );
                bd.setTransformation(t);
                bd.setInterpolationDuration(2);
                bd.setInterpolationDelay(0);
                bd.setTeleportDuration(2);
            });
            journey.getCabinEntities().add(display);
        }
    }

    /** Construit une petite plateforme avec 4 poteaux d'angle : la "cabine". */
    private List<CabinPart> buildCabinParts(Material floorMaterial) {
        List<CabinPart> parts = new ArrayList<>();
        // plateforme sous les pieds du joueur
        parts.add(new CabinPart(0, -1.3, 0, 2.2f, 0.25f, 2.2f, floorMaterial));
        // poteaux d'angle
        Material post = Material.OAK_FENCE;
        double off = 0.95;
        parts.add(new CabinPart(off, -0.75, off, 0.2f, 1.1f, 0.2f, post));
        parts.add(new CabinPart(-off, -0.75, off, 0.2f, 1.1f, 0.2f, post));
        parts.add(new CabinPart(off, -0.75, -off, 0.2f, 1.1f, 0.2f, post));
        parts.add(new CabinPart(-off, -0.75, -off, 0.2f, 1.1f, 0.2f, post));
        return parts;
    }

    private void tick(Journey journey) {
        Player player = plugin.getServer().getPlayer(journey.getPlayerUuid());
        if (player == null || !player.isOnline()) {
            cleanup(journey, true);
            return;
        }

        Route route = journey.getRoute();
        double t = journey.getCurrentTick() / (double) route.getDurationTicks();

        if (t >= 1.0) {
            finish(journey, player);
            return;
        }

        Location startLoc = route.hasCameraPath() ? route.locationAt(0) : route.getStartLocation();
        Location endLoc = route.hasCameraPath() ? route.locationAt(1) : route.getEndLocation();
        if (startLoc == null || endLoc == null) {
            // le monde a été déchargé en cours de route : on annule proprement
            cleanup(journey, true);
            return;
        }

        Location current = route.hasCameraPath() ? route.locationAt(t) : FlightMath.positionAt(startLoc, endLoc, route.getArcHeight(), t);
        if (!route.isLockCamera()) {
            // conserve la direction de regard du joueur, on ne force que la position
            current.setYaw(player.getLocation().getYaw());
            current.setPitch(player.getLocation().getPitch());
        }

        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(current);

        // repositionne le décor de la cabine autour du joueur
        List<CabinPart> parts = buildCabinParts(route.getCabinBlock());
        List<BlockDisplay> entities = journey.getCabinEntities();
        for (int i = 0; i < entities.size() && i < parts.size(); i++) {
            CabinPart part = parts.get(i);
            Vector offset = FlightMath.localToWorldOffset(part.side(), part.up(), part.forward(), current.getYaw());
            entities.get(i).teleport(current.clone().add(offset));
        }

        // particules le long du trajet
        current.getWorld().spawnParticle(route.getParticle(), current, 6, 0.4, 0.2, 0.4, 0.01);

        if (journey.getCurrentTick() % 15 == 0) {
            player.playSound(current, route.getSoundLoop(), 0.6f, 1.0f);
        }

        journey.incrementTick();
    }

    private void finish(Journey journey, Player player) {
        Route route = journey.getRoute();
        boolean discovery = discoveryJourneys.contains(player.getUniqueId());

        // Pour un voyage de DÉCOUVERTE (visite automatique du lobby), on ramène le
        // joueur exactement là où il était AVANT le début de la visite — jamais au
        // dernier point caméra du trajet, qui n'a aucune raison d'être un endroit sûr/
        // pertinent pour laisser le joueur (voir startDiscovery / discoveryOriginLocation).
        Location end = discovery
                ? discoveryOriginLocation.get(player.getUniqueId())
                : (route.hasCameraPath() ? route.locationAt(1) : route.getEndLocation());
        if (end == null) {
            cleanup(journey, true);
            return;
        }
        if (!discovery && !route.isLockCamera()) {
            end.setYaw(player.getLocation().getYaw());
            end.setPitch(player.getLocation().getPitch());
        }

        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(end);
        player.playSound(end, route.getSoundEnd(), 1.0f, 1.0f);
        end.getWorld().spawnParticle(route.getParticle(), end, 25, 0.6, 0.4, 0.6, 0.02);

        cleanup(journey, true);
    }

    /** Annule un voyage en cours (commande /transport cancel, déconnexion...). */
    public void cancel(Player player) {
        Journey journey = activeJourneys.get(player.getUniqueId());
        if (journey == null) return;
        boolean discovery = discoveryJourneys.remove(player.getUniqueId());
        Boolean invisible = previousInvisible.get(player.getUniqueId());
        cleanup(journey, true);
        if (discovery) {
            player.setInvisible(invisible != null && invisible);
            player.setInvulnerable(false);
            player.setCollidable(true);
        }
    }

    private void cleanup(Journey journey, boolean removeFromMap) {
        if (journey.getTask() != null) {
            journey.getTask().cancel();
        }
        for (BlockDisplay display : journey.getCabinEntities()) {
            if (!display.isDead()) {
                display.remove();
            }
        }
        Player player = plugin.getServer().getPlayer(journey.getPlayerUuid());
        if (player != null) {
            Boolean allowFlight = previousAllowFlight.remove(journey.getPlayerUuid());
            Boolean flying = previousFlying.remove(journey.getPlayerUuid());
            Boolean invisible = previousInvisible.remove(journey.getPlayerUuid());
            discoveryOriginLocation.remove(journey.getPlayerUuid());
            boolean discovery = discoveryJourneys.remove(journey.getPlayerUuid());
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(flying != null && flying);
                player.setAllowFlight(allowFlight != null && allowFlight);
            }
            if (discovery) {
                player.setInvisible(invisible != null && invisible);
                player.setInvulnerable(false);
                player.setCollidable(true);
            }
            if (discovery && removeFromMap) plugin.getDiscoveryManager().complete(player);
        } else {
            previousAllowFlight.remove(journey.getPlayerUuid());
            previousFlying.remove(journey.getPlayerUuid());
            previousInvisible.remove(journey.getPlayerUuid());
            discoveryOriginLocation.remove(journey.getPlayerUuid());
            discoveryJourneys.remove(journey.getPlayerUuid());
        }
        if (removeFromMap) {
            activeJourneys.remove(journey.getPlayerUuid());
        }
    }

    public void shutdown() {
        for (Journey journey : new ArrayList<>(activeJourneys.values())) {
            cleanup(journey, false);
        }
        activeJourneys.clear();
    }
}
