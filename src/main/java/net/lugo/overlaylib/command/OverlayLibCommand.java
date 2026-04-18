package net.lugo.overlaylib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.test.OverlayTesting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

public class OverlayLibCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignoredCommandRegistryAccess) {
        OverlayTesting.bootstrap();

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
                .then(ClientCommands.literal("test-enable")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getTestIds(), builder))
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    if (!OverlayTesting.getTestIds().contains(id.toLowerCase())) {
                                        OverlayTesting.report("command", () -> "test-enable unknown id=" + id);
                                        context.getSource().sendFeedback(Component.literal("Unknown test id: " + id));
                                        return 0;
                                    }

                                    boolean enabled = OverlayTesting.enable(id);
                                    OverlayTesting.report("command", () -> "test-enable id=" + id + " result=" + enabled);
                                    context.getSource().sendFeedback(Component.literal(
                                            enabled ? "Enabled test: " + id : "Test is already enabled: " + id
                                    ));
                                    return enabled ? 1 : 0;
                                })
                        )
                )
                .then(ClientCommands.literal("test-disable")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getTestIds(), builder))
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    if (!OverlayTesting.getTestIds().contains(id.toLowerCase())) {
                                        OverlayTesting.report("command", () -> "test-disable unknown id=" + id);
                                        context.getSource().sendFeedback(Component.literal("Unknown test id: " + id));
                                        return 0;
                                    }

                                    boolean disabled = OverlayTesting.disable(id);
                                    OverlayTesting.report("command", () -> "test-disable id=" + id + " result=" + disabled);
                                    context.getSource().sendFeedback(Component.literal(
                                            disabled ? "Disabled test: " + id : "Test is already disabled: " + id
                                    ));
                                    return disabled ? 1 : 0;
                                })
                        )
                )
                .then(ClientCommands.literal("test-run")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getTestIds(), builder))
                                .then(ClientCommands.argument("function", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            return SharedSuggestionProvider.suggest(OverlayTesting.getFunctionIds(id), builder);
                                        })
                                        .executes(OverlayLibCommand::runTestFunction)
                                )
                        )
                )
                .then(ClientCommands.literal("test-reporting")
                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    OverlayTesting.setReportingEnabled(enabled);
                                    OverlayTesting.report("command", () -> "test-reporting set to " + enabled);
                                    context.getSource().sendFeedback(Component.literal(
                                            "Overlay test reporting " + (enabled ? "enabled" : "disabled")
                                    ));
                                    return 1;
                                })
                        )
                )
        );
    }

    private static int runTestFunction(CommandContext<FabricClientCommandSource> context) {
        String id = StringArgumentType.getString(context, "id");
        String function = StringArgumentType.getString(context, "function");

        Boolean result = OverlayTesting.run(id, function);
        if (result == null) {
            OverlayTesting.report("command", () -> "test-run unknown test/function id=" + id + ", function=" + function);
            context.getSource().sendFeedback(Component.literal("Unknown test/function: " + id + "/" + function));
            return 0;
        }

        OverlayTesting.report("command", () -> "test-run id=" + id + ", function=" + function + ", result=" + result);

        context.getSource().sendFeedback(Component.literal(
                "Ran " + id + "." + function + " -> " + (result ? "success" : "no-op")
        ));
        return result ? 1 : 0;
    }

    private static List<String> getTestIds() {
        return OverlayTesting.getTestIds();
    }
}
