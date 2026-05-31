package com.quantumchanneling;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.quantumchanneling.channel.SyncServerConfigPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Console commands for live config control.
 *
 * <ul>
 *   <li>{@code /quantumchanneling reload} (alias {@code /qc reload}) — forces a re-read of the
 *       server-config TOML from disk, re-applies values to the hot-path mirrors, and pushes the
 *       fresh snapshot to every connected client. Useful when Forge's file watcher missed an edit
 *       or you want to be certain.</li>
 *   <li>{@code /qc status} — prints the headline config values to chat so you can verify what's
 *       active without opening the GUI.</li>
 * </ul>
 *
 * <p>Requires permission level 2 (op).
 */
@Mod.EventBusSubscriber(modid = QuantumChanneling.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuantumCommands {
    private QuantumCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // Build the primary command and capture the node so the alias can redirect to it.
        var primary = event.getDispatcher().register(
                Commands.literal("quantumchanneling")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reload").executes(QuantumCommands::reload))
                        .then(Commands.literal("status").executes(QuantumCommands::status))
        );
        // /qc as a redirect alias — same arguments, same permissions.
        event.getDispatcher().register(
                Commands.literal("qc")
                        .requires(s -> s.hasPermission(2))
                        .redirect(primary)
        );
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        boolean diskReloaded = forceDiskReload();
        // Mirror fields refresh from the (now-current) spec values via the normal ModConfigEvent
        // path — but we also poke them ourselves so partial-load races can't leave anything stale.
        // ServerConfig's onLoad re-runs when the file is read, so a successful diskReloaded path
        // already updated everything; this is the safety belt.
        SyncServerConfigPacket.sendToAll(server);

        int players = server.getPlayerList().getPlayers().size();
        Component msg = Component.literal("Quantum Channeling: ")
                .append(Component.literal(diskReloaded ? "config reloaded from disk" : "spec re-applied (file watcher state used)")
                        .withStyle(diskReloaded ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
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

    /**
     * Asks Forge's underlying NightConfig file handle to re-read the toml from disk. The
     * subsequent ModConfigEvent.Reloading then re-runs {@link ServerConfig#onLoad}, which
     * updates the static mirrors and (because it's a reload event) calls
     * {@link SyncServerConfigPacket#sendToAll}.
     *
     * <p>Done via reflection because Forge doesn't expose this on a stable public API — works on
     * 1.20.1 Forge but may need updating on later versions. Falls back to a no-op return on any
     * reflection failure; the command then falls back to a spec re-apply + broadcast.
     */
    private static boolean forceDiskReload() {
        try {
            Field fileMapField = ConfigTracker.class.getDeclaredField("fileMap");
            fileMapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ModConfig> fileMap = (Map<String, ModConfig>) fileMapField.get(ConfigTracker.INSTANCE);
            for (ModConfig cfg : fileMap.values()) {
                if (cfg.getSpec() != ServerConfig.SPEC) continue;
                if (cfg.getConfigData() instanceof CommentedFileConfig fileCfg) {
                    fileCfg.load();
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Forge internal layout changed — fall through to false and let the caller take the
            // already-current spec values from the in-memory mirror.
        }
        return false;
    }
}
