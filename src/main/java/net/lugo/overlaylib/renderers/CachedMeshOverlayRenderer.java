package net.lugo.overlaylib.renderers;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.MeshData;
import net.lugo.overlaylib.OverlayManager;
import net.lugo.overlaylib.OverlayRenderer;
import net.lugo.overlaylib.SectionMeshCache;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;

import java.util.List;

public abstract class CachedMeshOverlayRenderer extends OverlayRenderer {
    private final SectionMeshCache meshCache;

    protected CachedMeshOverlayRenderer(RenderPipeline renderPipeline, Identifier texture) {
        this(renderPipeline, texture, true);
    }

    protected CachedMeshOverlayRenderer(RenderPipeline renderPipeline, Identifier texture, boolean doIrisFlickerFix) {
        super(renderPipeline, texture, doIrisFlickerFix);
        this.meshCache = new SectionMeshCache();
    }

    @Override
    public void clearCache() {
        meshCache.clearAll();
    }

    @Override
    public void close() {
        meshCache.clearAll();
        super.close();
    }

    @Override
    public RenderResult renderSections(OverlayManager manager, List<SectionPos> sections) {
        if (!batchStarted) {
            return new RenderResult(sections.size(), 0, 0, 0);
        }

        int consideredSections = 0;
        int drawnSections = 0;
        int builtSections = 0;
        int missingSections = 0;

        long frameToken = getFrameStateToken();

        for (SectionPos sectionPos : sections) {
            consideredSections++;
            long dataVersion = manager.getSectionVersion(sectionPos);
            if (!meshCache.isCurrent(sectionPos, dataVersion, frameToken)) {
                OverlayRendererBlockData[] blocks = manager.getSectionBlocks(sectionPos);
                if (blocks == null) {
                    missingSections++;
                    meshCache.remove(sectionPos);
                    continue;
                }
                long freshDataVersion = manager.getSectionVersion(sectionPos);
                buildMesh(sectionPos, freshDataVersion, blocks);
                builtSections++;
            }
            SectionMeshCache.SectionMesh mesh = meshCache.get(sectionPos, frameToken);
            if (mesh == null || mesh.isEmpty()) {
                continue;
            }
            drawSection(sectionPos, mesh);
            drawnSections++;
        }

        return new RenderResult(consideredSections, drawnSections, builtSections, missingSections);
    }

    private void buildMesh(SectionPos sectionPos, long dataVersion, OverlayRendererBlockData[] blocks) {
        beginSection(sectionPos);
        for (OverlayRendererBlockData blockData : blocks) {
            addBlock(blockData);
        }
        try (MeshData built = endSection()) {
            meshCache.store(sectionPos, dataVersion, getFrameStateToken(), built);
        }
    }
}
