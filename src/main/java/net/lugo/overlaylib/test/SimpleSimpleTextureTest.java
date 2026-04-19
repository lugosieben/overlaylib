package net.lugo.overlaylib.test;

import net.lugo.overlaylib.Overlay;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.managers.SimpleOverlayManager;
import net.lugo.overlaylib.renderers.SimpleTextureOverlayRenderer;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.TextureSection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

public class SimpleSimpleTextureTest {
	private static final Minecraft MC = Minecraft.getInstance();
	private static final TextureSection.TextureSectionData TEST_SECTIONS = new TextureSection.TextureSectionData(2, 2);
	private static final TextureSection[] TEST_TEXTURES = {
			new TextureSection(TEST_SECTIONS, 0, 0),
			new TextureSection(TEST_SECTIONS, 1, 0),
			new TextureSection(TEST_SECTIONS, 0, 1),
			new TextureSection(TEST_SECTIONS, 1, 1)
	};
	private static final List<TextureSection> TEST_TEXTURE_OPTIONS = List.of(
			TEST_TEXTURES[0],
			TEST_TEXTURES[1],
			TEST_TEXTURES[2],
			TEST_TEXTURES[3]
	);
	private static final String TEST_ID = "simple_simple_texture";
	private static final SimpleSimpleTextureTestInstance INSTANCE = new SimpleSimpleTextureTestInstance();
	private static final ThreadLocal<BlockPos.MutableBlockPos> ABOVE_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

	private SimpleSimpleTextureTest() { }

	public static void register() {
		INSTANCE.register(TEST_ID, Map.of());
	}

	private static OverlayRendererBlockData computeBlockData(BlockPos pos) {
		if (MC.level == null) return OverlayRendererBlockData.NO_RENDER;

		BlockState state = MC.level.getBlockState(pos);
		if (state.isAir()) return OverlayRendererBlockData.NO_RENDER;

		BlockPos.MutableBlockPos abovePos = ABOVE_POS.get();
		abovePos.set(pos.getX(), pos.getY() + 1, pos.getZ());
		if (!MC.level.getBlockState(abovePos).isAir()) return OverlayRendererBlockData.NO_RENDER;
		if (!state.isSolidRender()) return OverlayRendererBlockData.NO_RENDER;

		int hash = (pos.getX() * 17) ^ (pos.getY() * 31) ^ (pos.getZ() * 13);
		hash ^= (hash >>> 16);

		float r = 0.35f + (((hash >> 6) & 0x3F) / 63.0f) * 0.65f;
		float g = 0.35f + (((hash >> 12) & 0x3F) / 63.0f) * 0.65f;
		float b = 0.35f + ((hash & 0x3F) / 63.0f) * 0.65f;

		int textureIndex = ((hash & 1) | (((hash >> 1) & 1) << 1));
		return new OverlayRendererBlockData(pos, r, g, b, 0.0f, TEST_TEXTURE_OPTIONS.get(textureIndex));
	}

	private static final class SimpleSimpleTextureTestInstance extends OverlayTesting.OverlayTest {
		private Overlay testOverlay;
		private boolean registered;

		@Override
		protected void onEnable() {
			if (testOverlay == null) {
				SimpleOverlayManager manager = new SimpleOverlayManager(SimpleSimpleTextureTest::computeBlockData);
				testOverlay = new Overlay(
						new SimpleTextureOverlayRenderer(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "icon.png"), true),
						6,
						manager
				);
				report("created overlay and simple manager");
			}

			if (!registered) {
				testOverlay.register();
				registered = true;
				report("registered overlay render callback");
			}

			testOverlay.setActive(true);
			report("overlay active=true");
		}

		@Override
		protected void onDisable() {
			if (testOverlay == null) {
				report("disable called before overlay initialization");
				return;
			}

			testOverlay.setActive(false);
			report("overlay active=false");
		}

		private SimpleOverlayManager getSimpleOverlayManager() {
			if (testOverlay == null) return null;
			if (!(testOverlay.getOverlayManager() instanceof SimpleOverlayManager manager)) return null;
			return manager;
		}
	}
}
