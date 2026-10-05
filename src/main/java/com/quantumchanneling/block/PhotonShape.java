package com.quantumchanneling.block;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.compat.mekanism.ChemicalCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;

import java.util.EnumMap;
import java.util.Map;

/**
 * Shared geometry + connection logic for the four wall-mountable channel devices
 * (emitter, receiver, storage, manager).
 *
 * <p>The device is a centered 6×6×6 "core" blob with a small back plate that touches the
 * mounting surface (the {@link #FACING} face's opposite side). For every adjacent block that
 * exposes an energy, item, fluid or chemical handler, the block displays — and is bounded by — a
 * small connector "arm" extending toward that face.
 *
 * <p>Using a centered hitbox guarantees the player can always click/break the device
 * regardless of placement orientation (the old flat-plate shape made the device unbreakable
 * on certain wall placements because the cursor passed through to the supporting block).
 */
public final class PhotonShape {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public static final BooleanProperty CONN_UP    = BooleanProperty.create("conn_up");
    public static final BooleanProperty CONN_DOWN  = BooleanProperty.create("conn_down");
    public static final BooleanProperty CONN_NORTH = BooleanProperty.create("conn_north");
    public static final BooleanProperty CONN_SOUTH = BooleanProperty.create("conn_south");
    public static final BooleanProperty CONN_EAST  = BooleanProperty.create("conn_east");
    public static final BooleanProperty CONN_WEST  = BooleanProperty.create("conn_west");

    /**
     * Centered 8×8×8 "core" blob hitbox — slightly larger than the visual 6×6×6 so the click
     * target stays generous (also matches the connector arm's 4×4 cross-section centered at 6..10).
     */
    private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);

    /** Per-direction connector arm (4×4×5 stub from core toward the named face). */
    private static final Map<Direction, VoxelShape> ARMS = Util.make(new EnumMap<>(Direction.class), m -> {
        m.put(Direction.UP,    Block.box(6, 11, 6, 10, 16, 10));
        m.put(Direction.DOWN,  Block.box(6,  0, 6, 10,  5, 10));
        m.put(Direction.NORTH, Block.box(6,  6, 0, 10, 10,  5));
        m.put(Direction.SOUTH, Block.box(6,  6, 11, 10, 10, 16));
        m.put(Direction.EAST,  Block.box(11, 6, 6, 16, 10, 10));
        m.put(Direction.WEST,  Block.box(0,  6, 6,  5, 10, 10));
    });

    private PhotonShape() {}

    public static BooleanProperty connProp(Direction d) {
        return switch (d) {
            case UP    -> CONN_UP;
            case DOWN  -> CONN_DOWN;
            case NORTH -> CONN_NORTH;
            case SOUTH -> CONN_SOUTH;
            case EAST  -> CONN_EAST;
            case WEST  -> CONN_WEST;
        };
    }

    public static void registerProps(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONN_UP, CONN_DOWN, CONN_NORTH, CONN_SOUTH, CONN_EAST, CONN_WEST);
    }

    public static BlockState defaultStateOff(BlockState state) {
        return state.setValue(FACING, Direction.UP)
                .setValue(CONN_UP, false).setValue(CONN_DOWN, false)
                .setValue(CONN_NORTH, false).setValue(CONN_SOUTH, false)
                .setValue(CONN_EAST, false).setValue(CONN_WEST, false);
    }

    /**
     * Build the union VoxelShape for a state: centered core + arms for every connected face.
     * No backplate — the device "floats" centered in the block without a visible mounting plate.
     */
    public static VoxelShape shape(BlockState state) {
        VoxelShape s = CORE;
        for (Direction d : Direction.values()) {
            if (state.getValue(connProp(d))) s = Shapes.or(s, ARMS.get(d));
        }
        return s;
    }

    /**
     * Whether the neighbour on side {@code dir} exposes anything a device can route: energy, or a
     * non-empty item / fluid / chemical handler.
     *
     * <p><b>Channel-bound devices never connect to each other.</b> Routing across the channel goes
     * through {@link com.quantumchanneling.channel.QuantumChannel#members()}, not adjacency — the
     * arm only hints "this side talks to a non-channel neighbour". Arms between adjacent devices
     * would suggest a direct local link that doesn't exist.
     */
    public static boolean detectConnection(Level level, BlockPos pos, Direction dir) {
        BlockPos neighborPos = pos.relative(dir);
        if (level.getBlockEntity(neighborPos) instanceof ChannelBoundBlockEntity) return false;
        Direction face = dir.getOpposite();
        if (level.getCapability(Capabilities.Energy.BLOCK, neighborPos, face) != null) return true;
        if (hasSlots(level, Capabilities.Item.BLOCK, neighborPos, face)) return true;
        if (hasSlots(level, Capabilities.Fluid.BLOCK, neighborPos, face)) return true;
        return ChemicalCompat.isAvailable() && hasSlots(level, ChemicalCompat.BLOCK, neighborPos, face);
    }

    private static boolean hasSlots(Level level, BlockCapability<? extends ResourceHandler<?>, Direction> cap,
                                    BlockPos pos, Direction face) {
        ResourceHandler<?> handler = level.getCapability(cap, pos, face);
        return handler != null && handler.size() > 0;
    }

    /**
     * Update every CONN_* on {@code state} by scanning the world. The device is a symmetric,
     * faceless blob — it can sink to or source from any of its 6 neighbours regardless of FACING.
     */
    public static BlockState refreshConnections(Level level, BlockPos pos, BlockState state) {
        BlockState out = state;
        for (Direction d : Direction.values()) {
            boolean conn = detectConnection(level, pos, d);
            BooleanProperty prop = connProp(d);
            if (out.getValue(prop) != conn) out = out.setValue(prop, conn);
        }
        return out;
    }
}
