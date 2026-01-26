package net.lugo.overlaylib;

import net.fabricmc.api.ModInitializer;

import net.lugo.overlaylib.util.IrisUtil;
import net.lugo.overlaylib.util.RenderPipelines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverlayLib implements ModInitializer {
	public static final String MOD_ID = "overlaylib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        LOGGER.info("OverlayLib (" + MOD_ID + ") initializing.");

        if (IrisUtil.irisDetected()) {
            LOGGER.info("Iris detected.");
            LOGGER.info("Registering pipelines with Iris.");
            RenderPipelines.registerWithIris();
        }

        LOGGER.info("OverlayLib (" + MOD_ID + ") initialized.");
	}
}