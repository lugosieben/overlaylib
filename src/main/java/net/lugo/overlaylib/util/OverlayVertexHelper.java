package net.lugo.overlaylib.util;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class OverlayVertexHelper {
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
            float uEnd,   float vEnd
            ) {
        vertex(buffer, x1, y1, z1, r, g, b, uStart, vStart);
        vertex(buffer, x2, y2, z2, r, g, b, uStart, vEnd);
        vertex(buffer, x3, y3, z3, r, g, b, uEnd, vEnd);
    }

    public static void rectFromTriags(
            VertexConsumer buffer,
            float x1, float z1,
            float x2, float z2,
            float y,
            float r,  float g,  float b,
            float uStart, float vStart,
            float uEnd,   float vEnd
    ) {
        triangle(buffer, x1, y, z1, x1, y, z2, x2, y, z2, r, g, b, uStart, vStart, uEnd, vEnd);
        triangle(buffer, x2, y, z2, x2, y, z1, x1, y, z1, r, g, b, uEnd, vEnd, uStart, vStart);
    }

    public static void squareFromTriags(
            VertexConsumer buffer,
            float x, float y, float z,
            float sidelength,
            float r,  float g,  float b,
            float uStart, float vStart,
            float uEnd,   float vEnd
    ) {
        rectFromTriags(
                buffer,
                x, z, x + sidelength, z + sidelength, y,
                r, g, b, uStart, vStart, uEnd, vEnd
        );
    }
}
