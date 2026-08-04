package net.lugo.overlaylib;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class OverlayRenderer {
    private final RenderPipeline renderPipeline;

    protected BufferBuilder buffer;

    private final Identifier textureId;
    private TextureSetup textureSetup;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final Set<OverlayRenderer> ACTIVE_RENDERERS = ConcurrentHashMap.newKeySet();
    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE);

    private final Minecraft MC = Minecraft.getInstance();

    private boolean batchStarted = false;
    private RenderPass currentPass = null;
    private boolean drawPassUnavailable = false;

    private float cameraX;
    private float cameraY;
    private float cameraZ;

    private SectionPos sectionOrigin;
    private boolean hasVertices;

    protected OverlayRenderer(RenderPipeline renderPipeline, Identifier texture) {
        this.renderPipeline = renderPipeline;
        this.textureId = texture;
        ACTIVE_RENDERERS.add(this);
    }

    public static void resetTextureSetups() {
        for (OverlayRenderer renderer : ACTIVE_RENDERERS) {
            renderer.textureSetup = null;
        }
    }

    public void startBatch(LevelRenderContext context) {
        if (batchStarted) return;

        if (textureSetup == null) {
            GpuTextureView gpuTextureView = MC.getTextureManager().getTexture(textureId).getTextureView();
            GpuSampler gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            textureSetup = TextureSetup.singleTexture(gpuTextureView, gpuSampler);
        }

        Vec3 camPos = context.levelState().cameraRenderState.pos;
        cameraX = (float) camPos.x;
        cameraY = (float) camPos.y;
        cameraZ = (float) camPos.z;

        currentPass = null;
        drawPassUnavailable = false;
        batchStarted = true;
    }

    public final void beginSection(SectionPos sectionPos) {
        buffer = new BufferBuilder(allocator, renderPipeline.getPrimitiveTopology(), Objects.requireNonNull(renderPipeline.getVertexFormatBinding(0)));
        sectionOrigin = sectionPos;
        hasVertices = false;
    }

    /** Adds one block's geometry to the section mesh currently being built. */
    public final void addBlock(OverlayRendererBlockData data) {
        if (buffer == null || sectionOrigin == null || !data.shouldRender()) return;

        BlockPos pos = data.pos();
        float x = pos.getX() - sectionOrigin.minBlockX();
        float y = pos.getY() - sectionOrigin.minBlockY() + data.yOffset();
        float z = pos.getZ() - sectionOrigin.minBlockZ();

        addVertices(x, y, z, data);
        hasVertices = true;
    }

    public final MeshData endSection() {
        MeshData built = null;
        if (hasVertices) {
            built = buffer.buildOrThrow();
        }
        buffer = null;
        sectionOrigin = null;
        hasVertices = false;
        return built;
    }

    public final void drawSection(SectionPos sectionPos, SectionMeshCache.SectionMesh mesh) {
        if (!batchStarted || mesh == null || mesh.isEmpty() || drawPassUnavailable) return;

        if (currentPass == null) {
            if (MC.gameRenderer.mainRenderTarget().getColorTextureView() == null) {
                drawPassUnavailable = true;
                return;
            }

            //noinspection DataFlowIssue
            currentPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                            () -> OverlayLib.MOD_ID + " overlay pipeline rendering",
                            MC.gameRenderer.mainRenderTarget().getColorTextureView(),
                            Optional.empty(),
                            MC.gameRenderer.mainRenderTarget().getDepthTextureView(),
                            OptionalDouble.empty()
                    );

            currentPass.setPipeline(renderPipeline);
            RenderSystem.bindDefaultUniforms(currentPass);
            currentPass.bindTexture("Sampler0", textureSetup.texure0(), textureSetup.sampler0());
        }

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy())
                .mul(new Matrix4f().translation(
                        -cameraX + sectionPos.minBlockX(),
                        -cameraY + sectionPos.minBlockY(),
                        -cameraZ + sectionPos.minBlockZ()
                ));

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelView, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        currentPass.setUniform("DynamicTransforms", dynamicTransforms);

        var shapeIndexBuffer = RenderSystem.getSequentialBuffer(renderPipeline.getPrimitiveTopology());
        currentPass.setVertexBuffer(0, mesh.slice());
        currentPass.setIndexBuffer(shapeIndexBuffer.getBuffer(mesh.indexCount()), shapeIndexBuffer.type());
        currentPass.drawIndexed(mesh.indexCount(), 1, 0, 0, 0);
    }

    public final void endBatch() {
        if (!batchStarted) return;
        batchStarted = false;
        if (currentPass != null) {
            currentPass.close();
            currentPass = null;
        }
    }

    public long getFrameStateToken() {
        return 0L;
    }

    protected abstract void addVertices(float x, float y, float z, OverlayRendererBlockData blockData);
}
