package net.lugo.overlaylib.util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.lugo.overlaylib.OverlayLib;
import net.minecraft.resources.Identifier;

public final class RenderPipelines {
    public static final RenderPipeline POSITION_TEX_COLOR_FOG = RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "pipeline/position_tex_color_fog"))
            .withVertexShader(ShaderHelper.POSITION_TEX_COLOR_FOG_VERTEX_SHADER)
            .withFragmentShader(ShaderHelper.POSITION_TEX_COLOR_FOG_FRAGMENT_SHADER)
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(true)
            .withDepthWrite(false)
            .build();

    public static void registerWithIris() {
        IrisUtil.assignPipeline(POSITION_TEX_COLOR_FOG, IrisPipeline.TEXTURED);
    }
}