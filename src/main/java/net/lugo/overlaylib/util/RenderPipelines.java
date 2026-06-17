package net.lugo.overlaylib.util;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.lugo.overlaylib.OverlayLib;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class RenderPipelines {

    public static final BindGroupLayout SAMPLER_LAYOUT = BindGroupLayout.builder()
            .withSampler("Sampler0")
            .build();

    public static final RenderPipeline.Snippet POSITION_TEX_COLOR_FOG = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(SAMPLER_LAYOUT)
            .withLocation(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "pipeline/position_tex_color_fog"))
            .withVertexShader(ShaderHelper.POSITION_TEX_COLOR_FOG_VERTEX_SHADER)
            .withFragmentShader(ShaderHelper.POSITION_TEX_COLOR_FOG_FRAGMENT_SHADER)
            .withColorTargetState(0, new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(true)
            .buildSnippet();

    public static final RenderPipeline POSITION_TEX_COLOR_FOG_TRIANGLES = RenderPipeline.builder(POSITION_TEX_COLOR_FOG)
            .withLocation(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "pipeline/position_tex_color_fog_triangles"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    public static void registerWithIris() {
        IrisUtil.assignPipeline(POSITION_TEX_COLOR_FOG_TRIANGLES, IrisPipeline.TEXTURED);
    }
}