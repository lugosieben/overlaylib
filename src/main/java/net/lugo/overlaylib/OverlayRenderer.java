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
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
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

public abstract class OverlayRenderer {
    private PoseStack poseStack;

    private final RenderPipeline renderPipeline;

    public BufferBuilder buffer;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    public MappableRingBuffer vertexBuffer;

    private final Identifier textureId;
    private TextureSetup textureSetup;

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);

    private final Minecraft MC = Minecraft.getInstance();

    private boolean batchStarted = false;
    private boolean hasVertices = false;

    private final boolean doIrisFlickerFix;

    protected OverlayRenderer(RenderPipeline renderPipeline, Identifier texture, boolean doIrisFlickerFix) {
        this.renderPipeline = renderPipeline;
        this.textureId = texture;
        this.doIrisFlickerFix = doIrisFlickerFix;
    }

    public final void startBatch(WorldRenderContext context) {
        if (batchStarted) return;

        if (textureSetup == null) {
            GpuTextureView gpuTextureView = MC.getTextureManager().getTexture(textureId).getTextureView();
            GpuSampler gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            textureSetup = TextureSetup.singleTexture(gpuTextureView, gpuSampler);
        }

        poseStack = context.matrices();
        Vec3 camPos = context.worldState().cameraRenderState.pos;

        getPoseStack().pushPose();
        getPoseStack().translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);

        hasVertices = false;
        if (buffer == null) {
            buffer = new BufferBuilder(allocator, renderPipeline.getVertexFormatMode(),  renderPipeline.getVertexFormat());
        }

        batchStarted = true;
        onStartBatch();
    }

    public final void addBlock(OverlayRendererBlockData data) {
        if (!batchStarted || MC.player == null) return;

        boolean isNearby = data.pos().closerThan(MC.player.blockPosition(), 12d);

        getPoseStack().pushPose();
        getPoseStack().translate(data.pos().getX(), data.pos().getY() + data.yOffset(), data.pos().getZ());

        Matrix4f positionMatrix = getPoseStack().last().pose();
        if (!isNearby && doIrisFlickerFix) {
            getPoseStack().translate(0, 3E-2, 0);
        }

        addVertices(buffer, positionMatrix, data);
        hasVertices = true;
        getPoseStack().popPose();
    }

    public final void endBatch() {
        if (!batchStarted) return;
        getPoseStack().popPose();
        batchStarted = false;
        onEndBatch();
    }

    public final void uploadThenDraw() {
        if (!hasVertices || buffer == null) {
            buffer = null;
            return;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("buildBuffer");

        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParams = builtBuffer.drawState();
        VertexFormat format = drawParams.format();

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

    protected abstract void onStartBatch();

    protected abstract void addVertices(VertexConsumer buffer, Matrix4f positionMatrix, OverlayRendererBlockData blockData);

    protected abstract void onEndBatch();

    protected PoseStack getPoseStack() {
        return poseStack;
    }
}
