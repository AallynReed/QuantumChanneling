package com.quantumchanneling;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.SyncServerConfigPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Console commands for live config control.
 *
 * <ul>
 *   <li>{@code /quantumchanneling reload} (alias {@code /qc reload}) — re-applies the config values
 *       (NeoForge's file watcher keeps them in sync with the TOML on disk) to the hot-path mirrors
 *       and channel data, then pushes the fresh snapshot to every connected client.</li>
 *   <li>{@code /qc status} — prints the headline config values to chat so you can verify what's
 *       active without opening the GUI.</li>
 * </ul>
 *
 * <p>Requires permission level 2 (op).
 */
@EventBusSubscriber(modid = QuantumChanneling.MODID)
public final class QuantumCommands {
    private QuantumCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // Build the primary command and capture the node so the alias can redirect to it.
        var primary = event.getDispatcher().register(
                Commands.literal("quantumchanneling")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reload").executes(QuantumCommands::reload))
                        .then(Commands.literal("status").executes(QuantumCommands::status))
        );
        // /qc as a redirect alias — same arguments, same permissions.
        event.getDispatcher().register(
                Commands.literal("qc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .redirect(primary)
        );
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        ServerConfig.reapplyLocal();
        ChannelData.get(server).applyChargingSlotConfig();
        SyncServerConfigPacket.sendToAll(server);

        int players = server.getPlayerList().getPlayers().size();
        Component msg = Component.literal("Quantum Channeling: ")
                .append(Component.literal("config re-applied").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" · " + players + " player(s) notified"));
        source.sendSuccess(() -> msg, true);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        line(source, "Cross-dimension",  String.valueOf(ServerConfig.allowCrossDimension));
        line(source, "Items routing",    ServerConfig.itemsRoutingEnabled
                + "  batch≤" + ServerConfig.itemsMaxBatch
                + "  emitter≤" + ServerConfig.itemsMaxSubsPerEmitter
                + "  receiver≤" + ServerConfig.itemsMaxSubsPerReceiver
                + "  channel≤" + ServerConfig.itemsMaxSubsPerChannel);
        line(source, "Fluids routing",   ServerConfig.fluidsRoutingEnabled
                + "  batch≤" + ServerConfig.fluidsMaxBatch
                + "  emitter≤" + ServerConfig.fluidsMaxSubsPerEmitter
                + "  receiver≤" + ServerConfig.fluidsMaxSubsPerReceiver
                + "  channel≤" + ServerConfig.fluidsMaxSubsPerChannel);
        line(source, "Gases routing",    ServerConfig.gasesRoutingEnabled
                + "  batch≤" + ServerConfig.gasesMaxBatch
                + "  emitter≤" + ServerConfig.gasesMaxSubsPerEmitter
                + "  receiver≤" + ServerConfig.gasesMaxSubsPerReceiver
                + "  channel≤" + ServerConfig.gasesMaxSubsPerChannel);
        line(source, "Heat routing",     String.valueOf(ServerConfig.heatRoutingEnabled));
        line(source, "Wireless charge",  ServerConfig.wirelessEnabled
                + "  hand=" + ServerConfig.slotHandEnabled
                + "  hotbar=" + ServerConfig.slotHotbarEnabled
                + "  inv=" + ServerConfig.slotInventoryEnabled
                + "  armor=" + ServerConfig.slotArmorEnabled
                + "  curios=" + ServerConfig.slotCuriosEnabled);
        return Command.SINGLE_SUCCESS;
    }

    private static void line(CommandSourceStack source, String key, String value) {
        Component c = Component.literal(key + ": ").withStyle(ChatFormatting.GRAY)
                .copy().append(Component.literal(value).withStyle(ChatFormatting.WHITE));
        source.sendSuccess(() -> c, false);
    }
}
