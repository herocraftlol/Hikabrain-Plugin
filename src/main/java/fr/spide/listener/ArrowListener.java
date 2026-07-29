package fr.spide.listener;

import fr.spide.GameManager;
import fr.spide.Spide;
import fr.spide.model.SpideMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

public class ArrowListener implements Listener {

    private final Spide plugin;
    private final GameManager gameManager;
    private final NamespacedKey spideArrowKey;

    public ArrowListener(Spide plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.spideArrowKey = new NamespacedKey(plugin, "spide-arrow");
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (gameManager.getMapOfPlayer(player.getUniqueId()) == null) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        arrow.getPersistentDataContainer().set(spideArrowKey, PersistentDataType.BYTE, (byte) 1);
        arrow.setDamage(0);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        // Munitions infinies : l'enchantement Infinité (voir GameManager#giveLoadout) fait le
        // gros du travail ; on force en plus la non-consommation ici en sécurité/fallback.
        event.setConsumeItem(false);
    }

    @EventHandler
    public void onArrowHitsPlayer(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Arrow arrow)) return;
        if (!isSpideArrow(arrow)) return;
        // Les flèches Spide ne blessent jamais - elles ne font que casser des blocs
        event.setCancelled(true);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!isSpideArrow(arrow)) return;

        Block hitBlock = event.getHitBlock();
        if (hitBlock == null) {
            // A touché une entité ou de l'air (bord de map) : rien à casser.
            return;
        }

        SpideMap map = findMapForLocation(hitBlock.getLocation());
        int radius = map != null ? map.getRadius() : 1;
        boolean pierce = map != null && map.isPierce();

        breakAround(hitBlock, arrow.getLocation(), radius);

        if (pierce) {
            // On annule l'impact pour laisser la flèche continuer sa trajectoire au travers du bloc.
            event.setCancelled(true);
        } else {
            arrow.remove();
        }
    }

    private boolean isSpideArrow(Arrow arrow) {
        Byte tag = arrow.getPersistentDataContainer().get(spideArrowKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    private SpideMap findMapForLocation(Location loc) {
        for (SpideMap m : gameManager.allMapsOrdered()) {
            if (m.isInside(loc)) return m;
        }
        return null;
    }

    /**
     * Casse le bloc touché, plus les blocs environnants selon le rayon configuré.
     * Rayon 1 (défaut) : uniquement le bloc touché.
     * Rayon N : cube NxN xN centré (approximativement) sur le bloc touché.
     * Gère aussi le cas où la flèche touche pile la frontière entre 2 ou 4 blocs :
     * dans ce cas les blocs adjacents concernés sont cassés en plus, même en rayon 1.
     */
    private void breakAround(Block hitBlock, Location impact, int radius) {
        int half = radius / 2;
        int startOffset = -half;

        for (int dx = 0; dx < radius; dx++) {
            for (int dy = 0; dy < radius; dy++) {
                for (int dz = 0; dz < radius; dz++) {
                    Block b = hitBlock.getRelative(startOffset + dx, startOffset + dy, startOffset + dz);
                    if (!b.isEmpty()) {
                        b.setType(org.bukkit.Material.AIR);
                    }
                }
            }
        }

        if (radius == 1) {
            breakExactEdgeNeighbours(hitBlock, impact);
        }
    }

    /**
     * Approxime la règle "si la flèche touche pile entre 2 ou 4 blocs, ils sont tous cassés".
     * On regarde si le point d'impact est très proche d'une frontière de bloc sur X et/ou Z,
     * et on casse alors les blocs adjacents concernés.
     */
    private void breakExactEdgeNeighbours(Block hitBlock, Location impact) {
        double epsilon = 0.06;
        double fx = impact.getX() - Math.floor(impact.getX());
        double fz = impact.getZ() - Math.floor(impact.getZ());

        boolean edgeX = fx < epsilon || fx > 1 - epsilon;
        boolean edgeZ = fz < epsilon || fz > 1 - epsilon;

        if (edgeX) {
            int dir = fx < epsilon ? -1 : 1;
            breakIfSolid(hitBlock.getRelative(dir, 0, 0));
        }
        if (edgeZ) {
            int dir = fz < epsilon ? -1 : 1;
            breakIfSolid(hitBlock.getRelative(0, 0, dir));
        }
        if (edgeX && edgeZ) {
            int dirX = fx < epsilon ? -1 : 1;
            int dirZ = fz < epsilon ? -1 : 1;
            breakIfSolid(hitBlock.getRelative(dirX, 0, dirZ));
        }
    }

    private void breakIfSolid(Block b) {
        if (!b.isEmpty()) {
            b.setType(org.bukkit.Material.AIR);
        }
    }
}
