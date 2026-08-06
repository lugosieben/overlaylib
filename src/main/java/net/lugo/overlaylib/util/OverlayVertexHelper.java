package net.lugo.overlaylib.util;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class OverlayVertexHelper {
    public enum FixedAxis {
        X,
        Y,
        Z
    }

    public static float[] rotateUV(
            float uStart, float vStart,
            float uEnd,   float vEnd,
            UVRotation rotation
    ) {
        return switch (rotation) {
            case NONE -> new float[]{uStart, vStart, uEnd, vEnd};
            case CW_90 -> new float[]{uStart, vEnd, uEnd, vStart};
            case CW_180 -> new float[]{uEnd, vEnd, uStart, vStart};
            case CW_270 -> new float[]{uEnd, vStart, uStart, vEnd};
        };
    }

    public static void vertex(
            VertexConsumer buffer,
            float x, float y, float z,
            float r, float g, float b,
            float u, float v
    ) {
        buffer.addVertex(x, y, z).setColor(r, g, b, 1f).setUv(u, v);
    }

    public static void triangle(
            VertexConsumer buffer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float r,  float g,  float b,
            float uStart, float vStart,
            float uEnd,   float vEnd,
            UVRotation rotation
            ) {
        float[] uv = rotateUV(uStart, vStart, uEnd, vEnd, rotation);

        vertex(buffer, x1, y1, z1, r, g, b, uv[0], uv[1]);
        if (rotation == UVRotation.CW_90 || rotation == UVRotation.CW_270) {
            // 90/270 needs swapped edges to prevent mirroring
            vertex(buffer, x2, y2, z2, r, g, b, uv[2], uv[1]);
            vertex(buffer, x3, y3, z3, r, g, b, uv[2], uv[3]);
        } else {
            vertex(buffer, x2, y2, z2, r, g, b, uv[0], uv[3]);
            vertex(buffer, x3, y3, z3, r, g, b, uv[2], uv[3]);
        }
    }

    public static void rect(
            VertexConsumer buffer,
            FixedAxis fixedAxis,
            float fixedCoord,
            float firstAxisStart, float secondAxisStart,
            float firstAxisEnd, float secondAxisEnd,
            float r,  float g,  float b,
            float uStart, float vStart,
            float uEnd,   float vEnd,
            UVRotation rotation
    ) {
        float[] p1 = pointOnPlane(fixedAxis, fixedCoord, firstAxisStart, secondAxisStart);
        float[] p2 = pointOnPlane(fixedAxis, fixedCoord, firstAxisStart, secondAxisEnd);
        float[] p3 = pointOnPlane(fixedAxis, fixedCoord, firstAxisEnd, secondAxisEnd);
        float[] p4 = pointOnPlane(fixedAxis, fixedCoord, firstAxisEnd, secondAxisStart);

        triangle(buffer, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], r, g, b, uStart, vStart, uEnd, vEnd, rotation);
        triangle(buffer, p3[0], p3[1], p3[2], p4[0], p4[1], p4[2], p1[0], p1[1], p1[2], r, g, b, uEnd, vEnd, uStart, vStart, rotation);
    }

    public static void square(
            VertexConsumer buffer,
            FixedAxis fixedAxis,
            float fixedCoord,
            float firstAxisStart, float secondAxisStart,
            float sideLength,
            float r, float g, float b,
            float uStart, float vStart,
            float uEnd,   float vEnd,
            UVRotation rotation
    ) {
        rect(
                buffer,
                fixedAxis,
                fixedCoord,
                firstAxisStart, secondAxisStart,
                firstAxisStart + sideLength, secondAxisStart + sideLength,
                r, g, b,
                uStart, vStart,
                uEnd, vEnd,
                rotation
        );
    }

    public static void square(
            VertexConsumer buffer,
            FixedAxis fixedAxis,
            float fixedCoord,
            float firstAxisStart, float secondAxisStart,
            float r, float g, float b,
            float uStart, float vStart,
            float uEnd,   float vEnd,
            UVRotation rotation
    ) {
        square(buffer, fixedAxis, fixedCoord, firstAxisStart, secondAxisStart, 1f, r, g, b, uStart, vStart, uEnd, vEnd, rotation);
    }

    public static void texturedSquare(
            VertexConsumer buffer,
            FixedAxis fixedAxis,
            float fixedCoord,
            float firstAxisStart, float secondAxisStart,
            float r, float g, float b,
            TextureSection section,
            UVRotation rotation
    ) {
        square(
                buffer,
                fixedAxis, fixedCoord,
                firstAxisStart, secondAxisStart,
                r, g, b,
                section.uStart(), section.vStart(),
                section.uEnd(), section.vEnd(),
                rotation
        );
    }

    private static float[] pointOnPlane(FixedAxis fixedAxis, float fixedCoord, float first, float second) {
        return switch (fixedAxis) {
            case X -> new float[]{fixedCoord, first, second};
            case Y -> new float[]{first, fixedCoord, second};
            case Z -> new float[]{first, second, fixedCoord};
        };
    }
}
