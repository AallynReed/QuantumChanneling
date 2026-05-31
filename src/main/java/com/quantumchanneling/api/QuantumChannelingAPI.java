package com.quantumchanneling.api;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.FluidSubchannel;
import com.quantumchanneling.channel.GasSubchannel;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.QuantumChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public entry point for third-party mods that want to read Quantum Channeling state — OpenComputers
 * drivers, ComputerCraft peripherals, in-world map mods, dashboard mods, etc. Everything here is
 * server-thread safe and read-only. Calls from the client thread return empty values.
 *
 * <p>Stability: the method shapes here are the part this mod promises not to break across minor
 * versions. Internal classes ({@code QuantumChannel}, {@code PhotonEmitterBlockEntity}, etc.) may
 * shift around freely — external integrators should hold {@link IQuantumChannelView} references
 * instead of trying to dig into those.
 */
public final class QuantumChannelingAPI {
    private QuantumChannelingAPI() {}

    /** Every channel currently known to {@code server}. Defensive copy. */
    public static List<UUID> listChannels(@Nullable MinecraftServer server) {
        if (server == null) return List.of();
        return new ArrayList<>(ChannelData.get(server).getChannels().keySet());
    }

    /** Look up a channel by id. Empty when {@code id} is unknown or {@code server} is null. */
    public static Optional<IQuantumChannelView> getChannel(@Nullable MinecraftServer server, @Nullable UUID id) {
        if (server == null || id == null) return Optional.empty();
        QuantumChannel ch = ChannelData.get(server).getChannel(id);
        return ch == null ? Optional.empty() : Optional.of(new ChannelViewImpl(server, ch));
    }

    /** Look up the bound device (if any) at {@code pos} in {@code level}. */
    public static Optional<IQuantumDeviceView> deviceAt(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return Optional.empty();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ChannelBoundBlockEntity bound)) return Optional.empty();
        return Optional.of(new DeviceViewImpl(bound));
    }

    /* ---- adapters ---- */

    private record ChannelViewImpl(MinecraftServer server, QuantumChannel ch) implements IQuantumChannelView {
        @Override public UUID id() { return ch.id(); }
        @Override public String name() { return ch.name(); }
        @Override public UUID ownerId() { return ch.ownerId(); }
        @Override public String ownerName() { return ch.ownerName(); }
        @Override public int memberCount() { return ch.memberCount(); }
        @Override public boolean isPublic() { return ch.isPublic(); }
        @Override public int color() { return ch.color(); }
        @Override public List<GlobalPos> members() { return new ArrayList<>(ch.members()); }
        @Override public int totalEmitterInputFE() {
            int total = 0;
            for (GlobalPos gp : ch.members()) {
                ServerLevel lvl = server.getLevel(gp.dimension());
                if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
                BlockEntity be = lvl.getBlockEntity(gp.pos());
                if (be instanceof PhotonEmitterBlockEntity em) total += em.getLastTickThroughput();
            }
            return total;
        }
        @Override public int totalReceiverOutputFE() {
            int total = 0;
            for (GlobalPos gp : ch.members()) {
                ServerLevel lvl = server.getLevel(gp.dimension());
                if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
                BlockEntity be = lvl.getBlockEntity(gp.pos());
                if (be instanceof PhotonReceiverBlockEntity r) total += r.getLastTickThroughput();
            }
            return total;
        }
    }

    private record DeviceViewImpl(ChannelBoundBlockEntity bound) implements IQuantumDeviceView {
        @Override public GlobalPos pos() { return bound.globalPos(); }
        @Override public Kind kind() {
            if (bound instanceof PhotonEmitterBlockEntity)  return Kind.EMITTER;
            if (bound instanceof PhotonReceiverBlockEntity) return Kind.RECEIVER;
            if (bound instanceof PhotonStorageBlockEntity)  return Kind.STORAGE;
            if (bound instanceof PhotonManagerBlockEntity)  return Kind.MANAGER;
            return Kind.UNKNOWN;
        }
        @Override public UUID channelId() { return bound.getChannelId(); }
        @Override public String customName() { return bound.getCustomName(); }
        @Override public int priority() { return bound.getPriority(); }
        @Override public int throughputCap() { return bound.getThroughputCap(); }
        @Override public boolean isOverdrive() { return bound.isSurgeMode(); }
        @Override public int lastTickThroughputFE() {
            if (bound instanceof PhotonEmitterBlockEntity em)  return em.getLastTickThroughput();
            if (bound instanceof PhotonReceiverBlockEntity rc) return rc.getLastTickThroughput();
            return 0;
        }
        @Override public boolean isItemsEnabled()  { return bound.isItemsEnabled(); }
        @Override public boolean isFluidsEnabled() { return bound.isFluidsEnabled(); }
        @Override public boolean isGasEnabled()    { return bound.isGasEnabled(); }
        @Override public List<IQuantumSubchannelView> itemSubchannels() {
            if (!(bound instanceof PhotonEmitterBlockEntity em)) return List.of();
            List<IQuantumSubchannelView> out = new ArrayList<>();
            for (ItemSubchannel s : em.itemSubchannels()) out.add(new ItemSubView(s));
            return out;
        }
        @Override public List<IQuantumSubchannelView> fluidSubchannels() {
            if (!(bound instanceof PhotonEmitterBlockEntity em)) return List.of();
            List<IQuantumSubchannelView> out = new ArrayList<>();
            for (FluidSubchannel s : em.fluidSubchannels()) out.add(new FluidSubView(s));
            return out;
        }
        @Override public List<IQuantumSubchannelView> gasSubchannels() {
            if (!(bound instanceof PhotonEmitterBlockEntity em)) return List.of();
            List<IQuantumSubchannelView> out = new ArrayList<>();
            for (GasSubchannel s : em.gasSubchannels()) out.add(new GasSubView(s));
            return out;
        }
    }

    private record ItemSubView(ItemSubchannel s) implements IQuantumSubchannelView {
        @Override public UUID id() { return s.id(); }
        @Override public String name() { return s.name(); }
        @Override public Kind kind() { return Kind.ITEM; }
        @Override public int color() { return s.color(); }
        @Override public boolean isWhitelist() { return s.filter().isWhitelist(); }
        @Override public Set<ResourceLocation> entryIds() { return Collections.unmodifiableSet(s.filter().items()); }
        @Override public Set<ResourceLocation> entryTags() { return Collections.unmodifiableSet(s.filter().tags()); }
        @Override public long routedTotal() { return s.routedTotal(); }
        @Override public int routedLastWindow() { return s.routedLastWindow(); }
    }
    private record FluidSubView(FluidSubchannel s) implements IQuantumSubchannelView {
        @Override public UUID id() { return s.id(); }
        @Override public String name() { return s.name(); }
        @Override public Kind kind() { return Kind.FLUID; }
        @Override public int color() { return s.color(); }
        @Override public boolean isWhitelist() { return s.filter().isWhitelist(); }
        @Override public Set<ResourceLocation> entryIds() { return Collections.unmodifiableSet(s.filter().fluids()); }
        @Override public Set<ResourceLocation> entryTags() { return Collections.unmodifiableSet(s.filter().tags()); }
        @Override public long routedTotal() { return s.routedTotal(); }
        @Override public int routedLastWindow() { return s.routedLastWindow(); }
    }
    private record GasSubView(GasSubchannel s) implements IQuantumSubchannelView {
        @Override public UUID id() { return s.id(); }
        @Override public String name() { return s.name(); }
        @Override public Kind kind() { return Kind.GAS; }
        @Override public int color() { return s.color(); }
        @Override public boolean isWhitelist() { return s.filter().isWhitelist(); }
        @Override public Set<ResourceLocation> entryIds() { return Collections.unmodifiableSet(s.filter().gases()); }
        @Override public Set<ResourceLocation> entryTags() { return Collections.unmodifiableSet(s.filter().tags()); }
        @Override public long routedTotal() { return s.routedTotal(); }
        @Override public int routedLastWindow() { return s.routedLastWindow(); }
    }
}
