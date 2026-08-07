package net.lugo.overlaylib;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.lugo.overlaylib.util.IrisFlickerFix;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.RetiredGpuBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
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
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final Set<OverlayRenderer> ACTIVE_RENDERERS = ConcurrentHashMap.newKeySet();
    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE);

    private final Minecraft MC = Minecraft.getInstance();

    protected boolean batchStarted = false;
    private RenderPass currentPass = null;
    private boolean drawPassUnavailable = false;

    private double cameraX;
    private double cameraY;
    private double cameraZ;

    private Frustum frustum;

    private SectionPos sectionOrigin;
    private boolean hasVertices;

    private MappableRingBuffer frameVertexBuffer;

    private final boolean doIrisFlickerFix;

    private Matrix4f cameraModelView = new Matrix4f();

    protected OverlayRenderer(RenderPipeline renderPipeline, Identifier texture) {
        this(renderPipeline, texture, true);
    }

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

    public record RenderResult(int consideredSections, int drawnSections, int builtSections, int missingSections) {
    }

    private record PendingMesh(SectionPos sectionPos, MeshData mesh) {
    }

    public RenderResult renderSections(OverlayManager manager, List<SectionPos> sections) {
        if (!batchStarted) {
            return new RenderResult(sections.size(), 0, 0, 0);
        }

        int consideredSections = 0;
        int drawnSections = 0;
        int missingSections = 0;

        List<PendingMesh> pendingMeshes = new ArrayList<>();
        int totalVertexBytes = 0;

        try {
            for (SectionPos sectionPos : sections) {
                if (isSectionNotVisible(sectionPos)) {
                    continue;
                }
                consideredSections++;
                OverlayRendererBlockData[] blocks = manager.getSectionBlocks(sectionPos);
                if (blocks == null) {
                    missingSections++;
                    continue;
                }
                beginSection(sectionPos);
                for (OverlayRendererBlockData blockData : blocks) {
                    addBlock(blockData);
                }
                MeshData built = endSection();
                if (built == null) {
                    continue;
                }
                totalVertexBytes += built.drawState().vertexCount() * built.drawState().format().getVertexSize();
                pendingMeshes.add(new PendingMesh(sectionPos, built));
            }

            if (!pendingMeshes.isEmpty()) {
                ensureFrameBufferCapacity(totalVertexBytes);

                long writeOffset = 0;
                for (PendingMesh pending : pendingMeshes) {
                    MeshData built = pending.mesh();
                    int meshVertexBytes = built.drawState().vertexCount() * built.drawState().format().getVertexSize();
                    GpuBufferSlice slice = uploadToFrameBuffer(built, writeOffset);
                    writeOffset += meshVertexBytes;
                    drawSection(pending.sectionPos(), slice, built.drawState().indexCount());
                    drawnSections++;
                }
            }
        } finally {
            for (PendingMesh pending : pendingMeshes) {
                pending.mesh().close();
            }
        }
        return new RenderResult(consideredSections, drawnSections, 0, missingSections);
    }

    public void startBatch(LevelRenderContext context) {
        if (batchStarted) return;

        cameraModelView = RenderSystem.getModelViewMatrixCopy();

        if (textureSetup == null) {
            GpuTextureView gpuTextureView = MC.getTextureManager().getTexture(textureId).getTextureView();
            GpuSampler gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            textureSetup = TextureSetup.singleTexture(gpuTextureView, gpuSampler);
        }

        Vec3 camPos = context.levelState().cameraRenderState.pos;
        cameraX = camPos.x;
        cameraY = camPos.y;
        cameraZ = camPos.z;

        currentPass = null;
        drawPassUnavailable = false;
        batchStarted = true;

        //noinspection resource
        frustum = new Frustum(context.gameRenderer().mainCamera().getCullFrustum());
        frustum.prepare(cameraX, cameraY, cameraZ);
        frustum.offsetToFullyIncludeCameraCube(8);
    }

    public void endBatch() {
        if (!batchStarted) return;
        batchStarted = false;
        if (currentPass != null) {
            currentPass.close();
            currentPass = null;
        }
        if (frameVertexBuffer != null) {
            frameVertexBuffer.rotate();
        }
    }

    public long getFrameStateToken() {
        return 0L;
    }

    public void clearCache() {
    }

    public void close() {
        ACTIVE_RENDERERS.remove(this);
        RenderSystem.assertOnRenderThread();
        if (frameVertexBuffer != null) {
            RetiredGpuBuffers.retire(frameVertexBuffer);
            frameVertexBuffer = null;
        }
        if (currentPass != null) {
            currentPass.close();
            currentPass = null;
        }
        batchStarted = false;
        textureSetup = null;
    }

    public final void beginSection(SectionPos sectionPos) {
        buffer = new BufferBuilder(allocator, renderPipeline.getPrimitiveTopology(), Objects.requireNonNull(renderPipeline.getVertexFormatBinding(0)));
        sectionOrigin = sectionPos;
        hasVertices = false;
    }

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

        drawSection(sectionPos, mesh.slice(), mesh.indexCount());
    }

    public final void drawSection(SectionPos sectionPos, GpuBufferSlice slice, int indexCount) {
        if (!batchStarted || slice == null || indexCount == 0 || drawPassUnavailable) return;
        if (isSectionNotVisible(sectionPos)) return;

        float translateY = (float) (-cameraY + sectionPos.minBlockY());
        if (doIrisFlickerFix) {
            translateY += IrisFlickerFix.getInstance().offset(distanceToSectionCenter(sectionPos));
        }
        drawMesh(slice, indexCount, new Vector3f(
                (float) (-cameraX + sectionPos.minBlockX()),
                translateY,
                (float) (-cameraZ + sectionPos.minBlockZ())
        ));
    }

    protected boolean isSectionNotVisible(SectionPos sectionPos) {
        if (frustum == null) return false;
        double minX = sectionPos.minBlockX();
        double minY = sectionPos.minBlockY();
        double minZ = sectionPos.minBlockZ();
        return !frustum.isVisible(new AABB(minX, minY, minZ, minX + 16, minY + 16, minZ + 16));
    }

    private float distanceToSectionCenter(SectionPos sectionPos) {
        float dx = (float) (sectionPos.minBlockX() + 8 - cameraX);
        float dy = (float) (sectionPos.minBlockY() + 8 - cameraY);
        float dz = (float) (sectionPos.minBlockZ() + 8 - cameraZ);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void ensureFrameBufferCapacity(int requiredBytes) {
        if (frameVertexBuffer != null && frameVertexBuffer.size() >= requiredBytes) {
            return;
        }
        if (frameVertexBuffer != null) {
            RetiredGpuBuffers.retire(frameVertexBuffer);
        }
        frameVertexBuffer = new MappableRingBuffer(
                () -> OverlayLib.MOD_ID + " overlay frame buffer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                Math.max(1, requiredBytes)
        );
    }

    private GpuBufferSlice uploadToFrameBuffer(MeshData built, long writeOffset) {
        GpuBufferSlice slice = frameVertexBuffer.currentBuffer().slice(writeOffset, built.vertexBuffer().remaining());
        try (GpuBufferSlice.MappedView mappedView = slice.map(false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), mappedView.data());
        }
        return slice;
    }

    private void drawMesh(GpuBufferSlice slice, int indexCount, Vector3f sectionOffset) {
        if (drawPassUnavailable) return;

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

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(cameraModelView, COLOR_MODULATOR, sectionOffset, TEXTURE_MATRIX);
        currentPass.setUniform("DynamicTransforms", dynamicTransforms);

        var shapeIndexBuffer = RenderSystem.getSequentialBuffer(renderPipeline.getPrimitiveTopology());
        currentPass.setVertexBuffer(0, slice);
        currentPass.setIndexBuffer(shapeIndexBuffer.getBuffer(indexCount), shapeIndexBuffer.type());
        currentPass.drawIndexed(indexCount, 1, 0, 0, 0);
    }

    protected abstract void addVertices(float x, float y, float z, OverlayRendererBlockData blockData);
}
