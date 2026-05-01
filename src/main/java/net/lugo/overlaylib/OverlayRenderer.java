package net.lugo.overlaylib;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.lugo.overlaylib.test.OverlayTesting;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class OverlayRenderer {
    private final RenderPipeline renderPipeline;

    public BufferBuilder buffer;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    public MappableRingBuffer vertexBuffer;

    private final Identifier textureId;
    private TextureSetup textureSetup;

    private static final Set<OverlayRenderer> ACTIVE_RENDERERS = ConcurrentHashMap.newKeySet();

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE);

    private final Minecraft MC = Minecraft.getInstance();

    private boolean batchStarted = false;
    private boolean hasVertices = false;
    private int batchedBlocks;
    private int uploadSummaryCounter;
    private int emptyBatchCounter;
    private int skippedDrawCounter;
    private int playerBlockX;
    private int playerBlockY;
    private int playerBlockZ;
    private float cameraX;
    private float cameraY;
    private float cameraZ;

    private static final float IRIS_OFFSET_Y = 3E-2f;
    private static final double NEARBY_BLOCK_DISTANCE_SQ = 12d * 12d;

    private final boolean doIrisFlickerFix;

    protected OverlayRenderer(RenderPipeline renderPipeline, Identifier texture, boolean doIrisFlickerFix) {
        this.renderPipeline = renderPipeline;
        this.textureId = texture;
        this.doIrisFlickerFix = doIrisFlickerFix;
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

        if (MC.player != null) {
            playerBlockX = MC.player.getBlockX();
            playerBlockY = MC.player.getBlockY();
            playerBlockZ = MC.player.getBlockZ();
        }

        hasVertices = false;
        batchedBlocks = 0;
        if (buffer == null) {
            buffer = new BufferBuilder(allocator, renderPipeline.getVertexFormatMode(),  renderPipeline.getVertexFormat());
        }

        batchStarted = true;
    }

    public final void addBlock(OverlayRendererBlockData data) {
        if (!batchStarted) return;
        if (!data.shouldRender()) return;

        BlockPos pos = data.pos();

        float x = pos.getX();
        float y = pos.getY() + data.yOffset();
        float z = pos.getZ();

        if (doIrisFlickerFix) {
            double dx = pos.getX() - playerBlockX;
            double dy = pos.getY() - playerBlockY;
            double dz = pos.getZ() - playerBlockZ;
            if ((dx * dx + dy * dy + dz * dz) >= NEARBY_BLOCK_DISTANCE_SQ) {
                y += IRIS_OFFSET_Y;
            }
        }

        addVertices(x - cameraX, y - cameraY, z - cameraZ, data);
        hasVertices = true;
        batchedBlocks++;
    }

    public final void endBatch() {
        if (!batchStarted) return;
        batchStarted = false;
    }

    public final void uploadThenDraw() {
        if (!hasVertices || buffer == null) {
            if (OverlayTesting.shouldReport() && (++emptyBatchCounter % 120 == 0)) {
                OverlayTesting.report("renderer", "skipped upload/draw because batch has no vertices");
            }
            return;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("buildBuffer");

        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParams = builtBuffer.drawState();
        VertexFormat format = drawParams.format();

        if (OverlayTesting.shouldReport() && (++uploadSummaryCounter % 120 == 0)) {
            int blockCount = batchedBlocks;
            int vertexCount = drawParams.vertexCount();
            int indexCount = drawParams.indexCount();
            OverlayTesting.report("renderer", () -> "batchSummary blocks=" + blockCount
                + ", vertices=" + vertexCount + ", indices=" + indexCount);
        }

        profiler.popPush("upload");
        GpuBuffer vertices = upload(builtBuffer, drawParams, format);
        profiler.popPush("draw");
        draw(builtBuffer, drawParams, vertices);

        vertexBuffer.rotate();
        buffer = null;
        profiler.pop();
    }

    private GpuBuffer upload(MeshData builtBuffer, MeshData.DrawState drawParams, VertexFormat format) {
        int vertexBufferSize = drawParams.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }

            vertexBuffer = new MappableRingBuffer(() -> OverlayLib.MOD_ID + " overlay pipeline", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        return vertexBuffer.currentBuffer();
    }

    private void draw(MeshData builtBuffer, MeshData.DrawState drawParams, GpuBuffer vertices) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (MC.getMainRenderTarget().getColorTextureView() == null) {
            if (OverlayTesting.shouldReport() && (++skippedDrawCounter % 120 == 0)) {
                OverlayTesting.report("renderer", "skipped draw because main color target is null");
            }
            builtBuffer.close();
            return;
        }

        if (renderPipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
            indices = renderPipeline.getVertexFormat().uploadImmediateIndexBuffer(Objects.requireNonNull(builtBuffer.indexBuffer()));
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(renderPipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParams.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> OverlayLib.MOD_ID + " overlay pipeline rendering", MC.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), MC.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(renderPipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.bindTexture("Sampler0", textureSetup.texure0(), textureSetup.sampler0());

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            renderPass.drawIndexed(0, 0, drawParams.indexCount(), 1);
        }

        builtBuffer.close();
    }

    protected abstract void addVertices(float worldX, float worldY, float worldZ, OverlayRendererBlockData blockData);
}
