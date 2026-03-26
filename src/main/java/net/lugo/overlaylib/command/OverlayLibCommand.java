package net.lugo.overlaylib.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.test.SimpleOverlayTest;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class OverlayLibCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignoredCommandRegistryAccess) {
        dispatcher.register(ClientCommands.literal("overlaylib")
                .then(ClientCommands.literal("version")
                        .executes(context -> {
                            String version = FabricLoader.getInstance()
                                    .getModContainer(OverlayLib.MOD_ID)
                                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                                    .orElse("unknown");
                            context.getSource().sendFeedback(Component.translatable("commands.overlaylib.version", version));
                            return 1;
                        })
                )
                .then(ClientCommands.literal("tests-enable")
                        .executes(context -> {
                            boolean changed = !SimpleOverlayTest.isEnabled();
                            SimpleOverlayTest.enable();
                            context.getSource().sendFeedback(Component.translatable(
                                    changed ? "commands.overlaylib.tests.enabled" : "commands.overlaylib.tests.already_enabled"
                            ));
                            return 1;
                        })
                )
                .then(ClientCommands.literal("tests-disable")
                .executes(context -> {
                            boolean changed = SimpleOverlayTest.disable();
                            context.getSource().sendFeedback(Component.translatable(
                                    changed ? "commands.overlaylib.tests.disabled" : "commands.overlaylib.tests.not_initialized"
                            ));
                            return 1;
                        })
                )
                .then(ClientCommands.literal("tests-run")
                        .then(ClientCommands.literal("clear")
                                .then(ClientCommands.literal("all")
                                        .executes(context -> {
                                            boolean changed = SimpleOverlayTest.clearAll();
                                            context.getSource().sendFeedback(Component.translatable(
                                                    changed ? "commands.overlaylib.tests.cleared_all" : "commands.overlaylib.tests.not_initialized"
                                            ));
                                            return 1;
                                        })
                                )
                                .then(ClientCommands.literal("playerpos")
                                        .executes(context -> {
                                            BlockPos blockPos = context.getSource().getPlayer().blockPosition();
                                            boolean changed = SimpleOverlayTest.clearFromBlockPos(blockPos);
                                            context.getSource().sendFeedback(Component.translatable(
                                                    changed ? "commands.overlaylib.tests.cleared_pos" : "commands.overlaylib.tests.not_initialized",
                                                    blockPos.toShortString()
                                            ));
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
}
