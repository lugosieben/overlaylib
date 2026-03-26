package net.lugo.overlaylib.test;

import net.lugo.overlaylib.Overlay;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.managers.CachedOverlayManager;
import net.lugo.overlaylib.renderers.SimpleTextureOverlayRenderer;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.TextureSection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class SimpleOverlayTest {
	private static final Minecraft MC = Minecraft.getInstance();
	private static final TextureSection.TextureSectionData TEST_SECTIONS = new TextureSection.TextureSectionData(2, 2);

	private static Overlay testOverlay;
	private static boolean registered;
	private static boolean enabled;

	private SimpleOverlayTest() {
	}

	public static void enable() {
		if (testOverlay == null) {
			CachedOverlayManager manager = new CachedOverlayManager(SimpleOverlayTest::computeBlockData, 8192, 64);
			testOverlay = new Overlay(
					new SimpleTextureOverlayRenderer(Identifier.fromNamespaceAndPath(OverlayLib.MOD_ID, "icon.png"), true),
					6,
					manager
			);
		}

		if (!registered) {
			testOverlay.register();
			registered = true;
		}

		testOverlay.setActive(true);
		enabled = true;
    }

	public static boolean disable() {
		if (testOverlay == null) {
			enabled = false;
			return false;
		}

		testOverlay.setActive(false);
		enabled = false;
		return true;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean clearAll() {
		CachedOverlayManager manager = getCachedOverlayManager();
		if (manager == null) return false;
		manager.clearAll();
		return true;
	}

	public static boolean clearFromBlockPos(BlockPos pos) {
		CachedOverlayManager manager = getCachedOverlayManager();
		if (manager == null) return false;
		manager.clearFromBlockPos(pos);
		return true;
	}

	private static CachedOverlayManager getCachedOverlayManager() {
		if (testOverlay == null) return null;
		if (!(testOverlay.getOverlayManager() instanceof CachedOverlayManager manager)) return null;
		return manager;
	}

	private static OverlayRendererBlockData computeBlockData(BlockPos pos) {
		if (MC.level == null) return OverlayRendererBlockData.NO_RENDER;

		BlockState state = MC.level.getBlockState(pos);
		if (state.isAir()) return OverlayRendererBlockData.NO_RENDER;

		BlockPos abovePos = pos.above();
		if (!MC.level.getBlockState(abovePos).isAir()) return OverlayRendererBlockData.NO_RENDER;
		if (!state.isSolidRender()) return OverlayRendererBlockData.NO_RENDER;

		int hash = Math.abs((pos.getX() * 31) ^ (pos.getY() * 13) ^ (pos.getZ() * 17));

		float r = 0.35f + ((hash & 0x3F) / 63.0f) * 0.65f;
		float g = 0.35f + (((hash >> 6) & 0x3F) / 63.0f) * 0.65f;
		float b = 0.35f + (((hash >> 12) & 0x3F) / 63.0f) * 0.65f;

		TextureSection textureSection = new TextureSection(TEST_SECTIONS, hash & 1, (hash >> 1) & 1);
		return new OverlayRendererBlockData(pos, r, g, b, 0.0f, Optional.of(textureSection));
	}
}
