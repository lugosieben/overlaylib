package net.lugo.overlaylib.util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.lugo.overlaylib.OverlayLib;
import net.minecraft.resources.Identifier;

public final class RenderPipelines {
    public static final RenderPipeline.Snippet POSITION_TEX_COLOR_FOG = RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "pipeline/position_tex_color_fog"))
            .withVertexShader(ShaderHelper.POSITION_TEX_COLOR_FOG_VERTEX_SHADER)
            .withFragmentShader(ShaderHelper.POSITION_TEX_COLOR_FOG_FRAGMENT_SHADER)
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(
                    BlendFunction.TRANSLUCENT
            ))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(true)
            .buildSnippet();

    public static final RenderPipeline POSITION_TEX_COLOR_FOG_QUADS = RenderPipeline.builder(POSITION_TEX_COLOR_FOG)
            .withLocation(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "pipeline/position_tex_color_fog_quads"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline POSITION_TEX_COLOR_FOG_TRIANGLES = RenderPipeline.builder(POSITION_TEX_COLOR_FOG)
            .withLocation(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "pipeline/position_tex_color_fog_triangles"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
            .build();

    public static void registerWithIris() {
        IrisUtil.assignPipeline(POSITION_TEX_COLOR_FOG_QUADS, IrisPipeline.TEXTURED);
        IrisUtil.assignPipeline(POSITION_TEX_COLOR_FOG_TRIANGLES, IrisPipeline.TEXTURED);
    }
}