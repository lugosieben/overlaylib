package net.lugo.overlaylib.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public class DistanceUtil {
    public static double getDistanceSquared(SectionPos sectionPos, BlockPos blockPos) {
        return getDistanceSquared(sectionPos, blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public static double getDistanceSquared(SectionPos sectionPos, int blockX, int blockY, int blockZ) {
        int centerX = SectionPos.sectionToBlockCoord(sectionPos.getX()) + 8;
        int centerY = SectionPos.sectionToBlockCoord(sectionPos.getY()) + 8;
        int centerZ = SectionPos.sectionToBlockCoord(sectionPos.getZ()) + 8;

        double dx = centerX - blockX;
        double dy = centerY - blockY;
        double dz = centerZ - blockZ;

        return dx * dx + dy * dy + dz * dz;
    }
}
