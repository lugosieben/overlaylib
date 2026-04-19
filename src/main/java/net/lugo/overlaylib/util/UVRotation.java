package net.lugo.overlaylib.util;

import net.minecraft.core.Direction;

public enum UVRotation {
    NONE,
    CW_90,
    CW_180,
    CW_270;

    public static UVRotation of(Direction direction) {
        return switch (direction) {
            case NORTH -> NONE;
            case EAST -> CW_90;
            case SOUTH -> CW_180;
            case WEST -> CW_270;
            default -> throw new IllegalArgumentException("Invalid direction for UV rotation: " + direction);
        };
    }
}
