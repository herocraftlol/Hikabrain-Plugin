package fr.spide;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Photo de tous les blocs d'une zone (pos1/pos2) à un instant T, prise via
 * /sp <map> posconfirm, et utilisée pour régénérer l'arène entre chaque manche
 * (voir GameManager#regenerateArena).
 *
 * Volontairement gardée en mémoire uniquement (pas persistée sur disque) : pour une
 * grosse arène, la taille deviendrait vite déraisonnable pour un fichier YAML,
 * et il suffit de relancer /sp <map> posconfirm après un redémarrage du serveur.
 */
public class ArenaSnapshot {

    private final World world;
    private final int minX, minY, minZ;
    private final int sizeX, sizeY, sizeZ;
    private final BlockData[] blocks;

    public ArenaSnapshot(World world, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, BlockData[] blocks) {
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = blocks;
    }

    /** Capture tous les blocs de la zone [pos1, pos2] (bornes incluses). */
    public static ArenaSnapshot capture(Location pos1, Location pos2) {
        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        BlockData[] blocks = new BlockData[sizeX * sizeY * sizeZ];
        int i = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks[i++] = world.getBlockAt(x, y, z).getBlockData().clone();
                }
            }
        }
        return new ArenaSnapshot(world, minX, minY, minZ, sizeX, sizeY, sizeZ, blocks);
    }

    /** Restaure tous les blocs de la zone tels qu'ils étaient au moment de la capture. */
    public void restore() {
        int i = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockData data = blocks[i++];
                    world.getBlockAt(minX + x, minY + y, minZ + z).setBlockData(data, false);
                }
            }
        }
    }
}
