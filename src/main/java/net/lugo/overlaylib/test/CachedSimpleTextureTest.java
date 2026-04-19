package net.lugo.overlaylib.test;

import net.lugo.overlaylib.Overlay;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.managers.CachedOverlayManager;
import net.lugo.overlaylib.renderers.CamFacingTextureOverlayRenderer;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.TextureSection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class CachedSimpleTextureTest {
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
	private static final String TEST_ID = "cached_simple_texture";
	private static final CachedSimpleTextureTestInstance INSTANCE = new CachedSimpleTextureTestInstance();
	private static final ThreadLocal<BlockPos.MutableBlockPos> ABOVE_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

	private CachedSimpleTextureTest() { }

	public static void register() {
		Map<String, Supplier<Boolean>> functions = Map.of(
				"clear-all", INSTANCE::clearAll,
				"clear-playerpos", INSTANCE::clearPlayerPos
		);
		INSTANCE.register(TEST_ID, functions);
	}

	private static OverlayRendererBlockData computeBlockData(BlockPos pos) {
		if (MC.level == null) return OverlayRendererBlockData.NO_RENDER;

		BlockState state = MC.level.getBlockState(pos);
		if (state.isAir()) return OverlayRendererBlockData.NO_RENDER;

		BlockPos.MutableBlockPos abovePos = ABOVE_POS.get();
		abovePos.set(pos.getX(), pos.getY() + 1, pos.getZ());
		if (!MC.level.getBlockState(abovePos).isAir()) return OverlayRendererBlockData.NO_RENDER;
		if (!state.isSolidRender()) return OverlayRendererBlockData.NO_RENDER;

		int hash = (pos.getX() * 31) ^ (pos.getY() * 13) ^ (pos.getZ() * 17);
		hash ^= (hash >>> 16);

		float r = 0.35f + ((hash & 0x3F) / 63.0f) * 0.65f;
		float g = 0.35f + (((hash >> 6) & 0x3F) / 63.0f) * 0.65f;
		float b = 0.35f + (((hash >> 12) & 0x3F) / 63.0f) * 0.65f;

		int textureIndex = ((hash & 1) | (((hash >> 1) & 1) << 1));
		return new OverlayRendererBlockData(pos, r, g, b, 0.0f, TEST_TEXTURE_OPTIONS.get(textureIndex));
	}

	private static final class CachedSimpleTextureTestInstance extends OverlayTesting.OverlayTest {
		private Overlay testOverlay;
		private boolean registered;

		@Override
		protected void onEnable() {
			if (testOverlay == null) {
				CachedOverlayManager manager = new CachedOverlayManager(CachedSimpleTextureTest::computeBlockData, 8192, 64);
				testOverlay = new Overlay(
						new CamFacingTextureOverlayRenderer(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "icon.png"), true),
						6,
						manager
				);
				report("created overlay and cache manager");
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

		private boolean clearAll() {
			CachedOverlayManager manager = getCachedOverlayManager();
			if (manager == null) return false;
			manager.clearAll();
			report("clear-all executed");
			return true;
		}

		private boolean clearPlayerPos() {
			if (MC.player == null) {
				return false;
			}

			CachedOverlayManager manager = getCachedOverlayManager();
			if (manager == null) return false;
			manager.clear(MC.player.blockPosition());
			report(() -> "clear-playerpos executed at " + MC.player.blockPosition().toShortString());
			return true;
		}

		private CachedOverlayManager getCachedOverlayManager() {
			if (testOverlay == null) return null;
			if (!(testOverlay.getOverlayManager() instanceof CachedOverlayManager manager)) return null;
			return manager;
		}
	}
}
