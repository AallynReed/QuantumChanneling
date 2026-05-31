package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Fluid analog of {@link ItemTransitHelper}. Same algorithm: emitter applies its void filter,
 * walks subscribed subchannels in priority order, routes the first matching subchannel's drain
 * to subscribed receivers' adjacent {@link IFluidHandler}.
 */
public final class FluidTransitHelper {
    private FluidTransitHelper() {}

    public static final class Decision {
        public enum Kind { VOID, ROUTE, REJECT }
        public static final Decision VOID = new Decision(Kind.VOID, null);
        public static final Decision REJECT = new Decision(Kind.REJECT, null);

        public final Kind kind;
        public final @Nullable UUID subchannelId;

        private Decision(Kind kind, @Nullable UUID subchannelId) {
            this.kind = kind;
            this.subchannelId = subchannelId;
        }
        public static Decision route(UUID id) { return new Decision(Kind.ROUTE, id); }
    }

    public static Decision evaluate(@Nullable MinecraftServer server,
                                    PhotonEmitterBlockEntity emitter,
                                    QuantumChannel channel,
                                    FluidStack stack) {
        if (stack == null || stack.isEmpty()) return Decision.REJECT;
        if (!emitter.fluidVoidFilter().matches(stack)) return Decision.VOID;
        if (emitter.fluidSubchannelCount() == 0) return Decision.REJECT;
        for (FluidSubchannel sub : emitter.fluidSubchannels()) {
            if (!sub.filter().matches(stack)) continue;
            if (server != null && !hasLoadedReceiverFor(server, channel, sub.id())) continue;
            return Decision.route(sub.id());
        }
        return Decision.REJECT;
    }

    public static boolean hasLoadedReceiverFor(MinecraftServer server, QuantumChannel channel, UUID subchannelId) {
        if (server == null || subchannelId == null) return false;
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = server.getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            if (!rcv.isResourceGate(rcv.isFluidsEnabled())) continue;
            if (rcv.isSubscribedToFluid(subchannelId)) return true;
        }
        return false;
    }

    public static List<PhotonReceiverBlockEntity> loadedReceiversFor(MinecraftServer server,
                                                                     QuantumChannel channel,
                                                                     UUID subchannelId) {
        if (server == null || subchannelId == null) return List.of();
        List<PhotonReceiverBlockEntity> out = new ArrayList<>();
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = server.getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            if (!rcv.isResourceGate(rcv.isFluidsEnabled())) continue;
            if (!rcv.isSubscribedToFluid(subchannelId)) continue;
            out.add(rcv);
        }
        out.sort(Comparator
                .comparingInt((PhotonReceiverBlockEntity r) -> -r.getPriority())
                .thenComparingLong(r -> r.getBlockPos().asLong()));
        return out;
    }

    /** Decide → execute. VOID consumes the whole stack; REJECT returns it; ROUTE pushes. */
    public static FluidStack route(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                   QuantumChannel channel, FluidStack stack, IFluidHandler.FluidAction action) {
        Decision d = emitter.decideFluid(channel, stack);
        switch (d.kind) {
            case VOID: return FluidStack.EMPTY;
            case REJECT: return stack;
            case ROUTE: {
                FluidStack leftover = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, stack, action);
                if (action.execute()) {
                    int moved = stack.getAmount() - leftover.getAmount();
                    if (moved > 0) {
                        FluidSubchannel sub = emitter.fluidSubchannel(d.subchannelId);
                        if (sub != null) sub.recordRouted(moved);
                    }
                }
                return leftover;
            }
            default: return stack;
        }
    }

    private static FluidStack pushToReceivers(MinecraftServer server,
                                              PhotonEmitterBlockEntity emitter,
                                              QuantumChannel channel,
                                              UUID subchannelId, FluidStack stack, IFluidHandler.FluidAction action) {
        List<PhotonReceiverBlockEntity> targets = loadedReceiversFor(server, channel, subchannelId);
        if (targets.isEmpty()) return stack;
        if (emitter.getFluidDispatch() == com.quantumchanneling.channel.DispatchStrategy.ROUND_ROBIN
                && targets.size() > 1) {
            int start = action.execute()
                    ? emitter.takeFluidRoundRobinIndex(targets.size())
                    : Math.floorMod(emitter.takeFluidRoundRobinIndex(targets.size()) - 1, targets.size());
            List<PhotonReceiverBlockEntity> rotated = new ArrayList<>(targets.size());
            for (int i = 0; i < targets.size(); i++) rotated.add(targets.get((start + i) % targets.size()));
            targets = rotated;
        }
        long now = (emitter.getLevel() != null) ? emitter.getLevel().getGameTime() : 0L;
        FluidStack remaining = stack.copy();
        for (PhotonReceiverBlockEntity rcv : targets) {
            if (remaining.isEmpty()) break;
            long key = rcv.getBlockPos().asLong();
            if (emitter.isFluidReceiverCooledDown(key, now)) continue;
            int before = remaining.getAmount();
            remaining = pushToAdjacentTanks(rcv, remaining, action);
            if (action.execute()) {
                int delivered = before - remaining.getAmount();
                if (delivered > 0) emitter.clearFluidReceiverCooldown(key);
                else               emitter.markFluidReceiverRejected(key, now);
            }
        }
        return remaining;
    }

    public static FluidStack pushToAdjacentTanks(ChannelBoundBlockEntity origin, FluidStack stack, IFluidHandler.FluidAction action) {
        if (stack.isEmpty()) return stack;
        var level = origin.getLevel();
        if (level == null) return stack;
        Direction[] sides = Direction.values();
        int startIdx = (origin.getFluidDispatch() == com.quantumchanneling.channel.DispatchStrategy.ROUND_ROBIN
                && action.execute())
                ? origin.takeFluidRoundRobinIndex(sides.length)
                : 0;
        FluidStack remaining = stack.copy();
        for (int i = 0; i < sides.length; i++) {
            if (remaining.isEmpty()) break;
            Direction side = sides[(startIdx + i) % sides.length];
            if (!origin.isFluidSideArmed(side)) continue;
            BlockEntity neighbor = level.getBlockEntity(origin.getBlockPos().relative(side));
            if (neighbor == null) continue;
            IFluidHandler dest = neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).orElse(null);
            if (dest == null) continue;
            int filled = dest.fill(remaining, action);
            if (filled > 0) {
                remaining = remaining.copy();
                remaining.shrink(filled);
            }
        }
        return remaining;
    }

    /** Active-pull cycle: scan adjacency for a fluid neighbor and pull mB up to {@code budget}. */
    public static int pullFromAdjacentAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                               QuantumChannel channel, int budget) {
        if (budget <= 0) return 0;
        if (!emitter.isFluidsEnabled()) return 0;
        BlockPos origin = emitter.getBlockPos();
        for (Direction side : Direction.values()) {
            if (!emitter.isFluidSideArmed(side)) continue;
            BlockEntity neighbor = level.getBlockEntity(origin.relative(side));
            if (neighbor == null) continue;
            IFluidHandler src = neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).orElse(null);
            if (src == null) continue;
            int moved = scanAndRoute(level, emitter, channel, src, budget);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private static int scanAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                    QuantumChannel channel, IFluidHandler src, int budget) {
        for (int tank = 0; tank < src.getTanks(); tank++) {
            FluidStack inTank = src.getFluidInTank(tank);
            if (inTank.isEmpty()) continue;
            FluidStack peek = src.drain(new FluidStack(inTank, Math.min(budget, inTank.getAmount())),
                    IFluidHandler.FluidAction.SIMULATE);
            if (peek.isEmpty()) continue;
            Decision d = emitter.decideFluid(channel, peek);
            switch (d.kind) {
                case REJECT -> { continue; }
                case VOID -> {
                    FluidStack taken = src.drain(peek, IFluidHandler.FluidAction.EXECUTE);
                    return taken.getAmount();
                }
                case ROUTE -> {
                    FluidStack simulated = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, peek, IFluidHandler.FluidAction.SIMULATE);
                    int wouldMove = peek.getAmount() - simulated.getAmount();
                    if (wouldMove <= 0) continue;
                    FluidStack taken = src.drain(new FluidStack(peek, wouldMove), IFluidHandler.FluidAction.EXECUTE);
                    if (taken.isEmpty()) continue;
                    FluidStack leftover = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, taken, IFluidHandler.FluidAction.EXECUTE);
                    // Drained-but-not-deposited would be a bug, but log-free path: just lose it.
                    int moved = taken.getAmount() - leftover.getAmount();
                    if (moved > 0) {
                        FluidSubchannel sub = emitter.fluidSubchannel(d.subchannelId);
                        if (sub != null) sub.recordRouted(moved);
                    }
                    return moved;
                }
            }
        }
        return 0;
    }

    public static ResourceLocation idOf(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.FLUID.getKey(stack.getFluid());
    }
}
