package io.github.west3436.zas.hotspot;

import java.util.function.Predicate;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Finds the gas fire blocks around a point, as they are now, for their sound (FIRE-036). It reads
 * only chunks already loaded, and it never loads one, so a chunk that has left shows no fire.
 *
 * <p>Most sections have no fire, and asking a section's palette takes a few comparisons, so only a
 * section that may hold one has its blocks read. It has no client classes, so a GameTest can run it
 * on a server's level.
 */
public final class GasFireScan {
    /** Told each block found. */
    @FunctionalInterface
    public interface Found {
        void at(int x, int y, int z);
    }

    private GasFireScan() {}

    /**
     * Finds the gas fire blocks in a cube.
     *
     * @param level the level
     * @param x the cube's middle
     * @param y the cube's middle
     * @param z the cube's middle
     * @param radius how far the cube reaches on each side, in blocks
     * @param found told each block
     */
    public static void forEach(Level level, double x, double y, double z, double radius, Found found) {
        Block fire = HotspotBlocks.GAS_FIRE.get();
        Predicate<BlockState> isFire = state -> state.is(fire);
        int minX = Mth.floor(x - radius);
        int minY = Math.max(Mth.floor(y - radius), level.getMinBuildHeight());
        int minZ = Mth.floor(z - radius);
        int maxX = Mth.floor(x + radius);
        int maxY = Math.min(Mth.floor(y + radius), level.getMaxBuildHeight() - 1);
        int maxZ = Mth.floor(z + radius);
        for (int cx = SectionPos.blockToSectionCoord(minX); cx <= SectionPos.blockToSectionCoord(maxX); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(minZ); cz <= SectionPos.blockToSectionCoord(maxZ); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    LevelChunkSection section = sections[chunk.getSectionIndexFromSectionY(sy)];
                    if (section.hasOnlyAir() || !section.maybeHas(isFire)) {
                        continue;
                    }
                    readSection(section, cx, sy, cz, fire, minX, minY, minZ, maxX, maxY, maxZ, found);
                }
            }
        }
    }

    private static void readSection(
            LevelChunkSection section,
            int cx,
            int sy,
            int cz,
            Block fire,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            Found found) {
        int originX = SectionPos.sectionToBlockCoord(cx);
        int originY = SectionPos.sectionToBlockCoord(sy);
        int originZ = SectionPos.sectionToBlockCoord(cz);
        for (int by = Math.max(minY - originY, 0); by <= Math.min(maxY - originY, 15); by++) {
            for (int bz = Math.max(minZ - originZ, 0); bz <= Math.min(maxZ - originZ, 15); bz++) {
                for (int bx = Math.max(minX - originX, 0); bx <= Math.min(maxX - originX, 15); bx++) {
                    if (section.getBlockState(bx, by, bz).is(fire)) {
                        found.at(originX + bx, originY + by, originZ + bz);
                    }
                }
            }
        }
    }
}
