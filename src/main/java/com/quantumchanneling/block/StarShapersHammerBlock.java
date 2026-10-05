package com.quantumchanneling.block;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.channel.LightBurstPacket;
import com.quantumchanneling.channel.ModMessages;
import com.quantumchanneling.compat.ftbchunks.ClaimGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Anvil-style placeable that converts items as it falls.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Player places the hammer above an air gap.</li>
 *   <li>The hammer falls (vanilla {@link FallingBlock} physics).</li>
 *   <li>When it lands, the block scans the entire vertical column it fell through for item
 *       entities and converts every matching stack in one go:
 *       <ul>
 *         <li>Nether Star → 2 × White Dwarf per input item.</li>
 *         <li>White Dwarf → 1 × Uncontained Black Hole per input item.</li>
 *       </ul>
 *       Entire stacks transform — a 64-pack of nether stars becomes 128 white dwarfs in one
 *       landing.</li>
 * </ol>
 *
 * <p>A placed hammer can also be right-clicked to crush items resting in the block directly below
 * it, so it doubles as a manual press without a fall.
 *
 * <p>Mining: a netherite-tier pickaxe is required for drops (enforced in code; vanilla has no
 * {@code needs_netherite_tool} tag).
 */
public class StarShapersHammerBlock extends FallingBlock {
    /** Anvil-like silhouette: wide head + narrow neck + wide base. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0,  5, 14,  4, 11),   // base
            Block.box(6, 4,  6, 10, 12, 10),   // neck
            Block.box(2, 12, 5, 14, 16, 11));  // head

    public StarShapersHammerBlock(Properties properties) {
        super(properties);
    }

    /** Endgame infrastructure — only a netherite-tier pickaxe yields the block back. Vanilla's
     *  {@code needs_*_tool} tag system tops out at diamond, so the check lives here in code. */
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        // A netherite pickaxe specifically — not just any netherite TieredItem (sword/axe count as
        // TieredItem and would otherwise harvest an anvil-like block, which reads wrong).
        return player.getMainHandItem().getItem() instanceof PickaxeItem pick
                && pick.getTier().getLevel() >= Tiers.NETHERITE.getLevel();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * Vanilla calls this after the falling block has been re-placed at the landing position.
     * The {@link FallingBlockEntity} carries its origin via {@link FallingBlockEntity#getStartPos},
     * so we can sweep the full column the hammer fell through and convert every item it crossed —
     * items at the same Y as the landing position included.
     *
     * <p>Conversion is whole-stack: each input item in a found stack contributes {@code n} output
     * items ({@code n} = 2 for nether stars, 1 for white dwarfs). The resulting output gets split
     * into normal-sized stacks if it would exceed the item's max stack size.
     */
    @Override
    public void onLand(Level level, BlockPos pos, BlockState fallingState, BlockState landedState,
                       FallingBlockEntity entity) {
        super.onLand(level, pos, fallingState, landedState, entity);
        if (level.isClientSide) return;

        BlockPos start = entity.getStartPos();
        // Inclusive column from the highest of (start, pos) down to the landing position. Add a
        // 0.5-block pad above the start and below the landing pos so items that bobbed slightly
        // outside the strict block bounds still get caught.
        double minY = Math.min(start.getY(), pos.getY()) - 0.5;
        double maxY = Math.max(start.getY(), pos.getY()) + 1.5;
        AABB column = new AABB(
                pos.getX(),     minY, pos.getZ(),
                pos.getX() + 1, maxY, pos.getZ() + 1);

        CrushResult result = crushItems(level, column);
        if (level instanceof ServerLevel sl) {
            if (result.totalCrushed() > 0) emitCrushEffects(sl, pos);
            if (result.whiteDwarfsCrushed() > 0) {
                // The dwarf-in-water signal OR the hammer having fallen through water triggers the
                // amplified burst. Item entities are skipped by the burst, so the fresh black holes
                // survive their own creation.
                boolean amplifiedByWater = result.dwarfInWater() || fellThroughWater(sl, start, pos);
                triggerCollapseBurst(sl, pos, amplifiedByWater);
            }
        }
    }

    /**
     * Manual press — right-clicking a placed hammer crushes convertible item entities lying in the
     * block directly below it, so the hammer works as a stationary station and not only by falling.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.CONSUME;
        BlockPos below = pos.below();
        AABB box = new AABB(below.getX() - 0.1, below.getY() - 0.1, below.getZ() - 0.1,
                below.getX() + 1.1, below.getY() + 1.1, below.getZ() + 1.1);
        CrushResult result = crushItems(sl, box);
        if (result.totalCrushed() == 0) return InteractionResult.PASS;
        emitCrushEffects(sl, pos);
        if (result.whiteDwarfsCrushed() > 0) triggerCollapseBurst(sl, pos, result.dwarfInWater());
        return InteractionResult.CONSUME;
    }

    /** One crush sweep: total items crushed, how many were White Dwarfs (they fire the collapse
     *  burst), and whether any dwarf sat in water (amplifies the burst). */
    private record CrushResult(int totalCrushed, int whiteDwarfsCrushed, boolean dwarfInWater) {}

    /**
     * Crushes every convertible item entity inside {@code scan}, spawning product stacks at each
     * source item's position (Nether Star → 2 White Dwarf; White Dwarf → 1 Uncontained Black Hole),
     * split into max-size stacks. Water state is captured before discarding since {@link Entity#isInWater}
     * reports the previous tick's flag, which survives the hammer replacing the water beneath the item.
     */
    private static CrushResult crushItems(Level level, AABB scan) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, scan);
        int totalCrushed = 0;
        int whiteDwarfsCrushed = 0;
        boolean dwarfWasInWater = false;
        for (ItemEntity ie : items) {
            if (!ie.isAlive()) continue;
            ItemStack stack = ie.getItem();
            if (stack.isEmpty()) continue;
            ItemStack template = productFor(stack);
            if (template.isEmpty()) continue;

            int multiplier = template.getCount();          // per-input output count
            int totalOutput = stack.getCount() * multiplier;
            boolean isDwarfCrush = stack.is(QuantumChanneling.WHITE_DWARF.get());
            if (isDwarfCrush && !dwarfWasInWater && isItemInWater(level, ie)) dwarfWasInWater = true;

            ie.discard();
            totalCrushed += stack.getCount();
            if (isDwarfCrush) whiteDwarfsCrushed += stack.getCount();

            int maxStack = template.getItem().getMaxStackSize();
            int remaining = totalOutput;
            while (remaining > 0) {
                int batch = Math.min(remaining, maxStack);
                ItemEntity out = new ItemEntity(level, ie.getX(), ie.getY(), ie.getZ(),
                        new ItemStack(template.getItem(), batch));
                out.setDefaultPickUpDelay();
                level.addFreshEntity(out);
                remaining -= batch;
            }
        }
        return new CrushResult(totalCrushed, whiteDwarfsCrushed, dwarfWasInWater);
    }

    /** Crit + smoke particles and the anvil-land sound — the "something got crushed" feedback. */
    private static void emitCrushEffects(ServerLevel sl, BlockPos pos) {
        sl.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                20, 0.4, 0.2, 0.4, 0.1);
        sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                12, 0.3, 0.1, 0.3, 0.02);
        sl.levelEvent(1029, pos, 0);   // Forge level event 1029 = anvil land
    }

    /**
     * True when this item entity is submerged in water (or was, at last tick) — the canonical
     * "white dwarf is in water" signal for amplifying the collapse burst.
     *
     * <p>{@link Entity#isInWater} reads {@code wasTouchingWater}, which is set during the entity's
     * baseTick from {@code updateInWaterStateAndDoFluidPushing}. Crucially, this state survives
     * the moment the hammer's falling-block entity replaces the water source at the item's
     * position — the entity's flag persists from the previous tick even though the fluid is no
     * longer there to query directly. The three fluid-state fallbacks below catch edge cases
     * where the item is sitting at the water line or just above a submerged block.
     */
    private static boolean isItemInWater(Level level, ItemEntity ie) {
        if (ie.isInWater()) return true;
        BlockPos p = ie.blockPosition();
        if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        if (level.getFluidState(p.below()).is(FluidTags.WATER)) return true;
        if (level.getFluidState(p.above()).is(FluidTags.WATER)) return true;
        return false;
    }

    /**
     * True when the hammer landed in (or close enough to) water for the collapse burst to use
     * water as a catalyst. Three independent signals get checked — any single hit returns true:
     *
     * <ol>
     *   <li><b>Fall column</b> — the entire vertical column at (landing.X, landing.Z) from one
     *       block below the start position to one block above the landing position. Catches deep
     *       water columns where water above the landing block survives the placement.</li>
     *   <li><b>5 × 3 × 5 cube around the landing position</b> — water adjacent / above / below
     *       the hammer's resting block. This is the big one: when the hammer falls into a single-
     *       tile puddle, the water at the landing position is replaced by the hammer block (so
     *       it's no longer visible at the strict landing pos), but the surrounding blocks +
     *       blocks above/below typically still hold water that was either the rest of the puddle
     *       or seeping back in.</li>
     *   <li><b>Hammer's current waterloggable neighbours</b> — flowing water from a nearby source
     *       creeping toward the hammer.</li>
     * </ol>
     */
    private static boolean fellThroughWater(ServerLevel level, BlockPos start, BlockPos landing) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // 1) Fall column with a one-block pad on each end so a hammer that landed at the water
        // surface still catches the source above it (and a fall that ended one block past the
        // intended floor still reads).
        int minY = Math.min(start.getY(), landing.getY()) - 1;
        int maxY = Math.max(start.getY(), landing.getY()) + 1;
        for (int y = minY; y <= maxY; y++) {
            cursor.set(landing.getX(), y, landing.getZ());
            if (level.getFluidState(cursor).is(FluidTags.WATER)) return true;
        }

        // 2) 5 × 3 × 5 around the landing pos. 75 fluid checks — cheap, and broad enough to catch
        // the "single-tile puddle the hammer just replaced" case where the water signature is
        // ONLY in the blocks the puddle didn't fill (no fall-column water, no immediately-cardinal
        // water, but water somewhere in the neighbourhood).
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    cursor.set(landing.getX() + dx, landing.getY() + dy, landing.getZ() + dz);
                    if (level.getFluidState(cursor).is(FluidTags.WATER)) return true;
                }
            }
        }

        return false;
    }

    /** Pure white — the explosion phase is the "supernova flash" of a collapsing white dwarf:
     *  brilliant, blinding, all spectra collapsing toward white. The implosion phase that follows
     *  is shader-colored (dark + dark purple) independently of this RGB, so the two phases read
     *  as a hard tonal contrast: white burst → dark purple swirl. Radius, peak damage, and the
     *  water multiplier are all in {@link ServerConfig} (endgame.starShaper.*). */
    private static final int COLLAPSE_COLOR_RGB = 0xFFFFFF;

    /**
     * Damage every living entity within the configured radius of the hammer (linear falloff;
     * deliberately NOT a vanilla {@link Level#explode}, which would destroy item entities and
     * fling blocks around) and broadcast a {@link LightBurstPacket} to every nearby client so
     * the shader burst renders synchronously with the damage.
     *
     * <p>Item entities are filtered out of the damage list entirely — the freshly-spawned
     * uncontained black holes from the crush are sitting at the burst center and must survive.
     *
     * <p>Water never dampens the damage — the {@link Entity#hurt} path doesn't ray-cast through
     * blocks the way {@link Level#explode} does, so submerged entities take the same falloff as
     * dry ones. Beyond that, when the hammer landed in / fell through water, both the radius and
     * the peak damage are multiplied by {@code endgame.starShaper.waterAmplify}.
     */
    private static void triggerCollapseBurst(ServerLevel level, BlockPos hammerPos, boolean waterAmplified) {
        if (!ServerConfig.starCollapseEnabled) return;
        float amp = (float) ServerConfig.starCollapseWaterAmp;
        float radius = ServerConfig.starCollapseRadius * (waterAmplified ? amp : 1.0f);
        float peakDamage = (float) ServerConfig.starCollapsePeakDamage * (waterAmplified ? amp : 1.0f);
        if (radius <= 0.0f) return;

        Vec3 center = Vec3.atCenterOf(hammerPos);
        AABB damageBox = new AABB(center, center).inflate(radius);
        DamageSource source = level.damageSources().explosion(null);

        // ---- entity damage ----
        // Filter at iteration time — only LivingEntities take damage; ItemEntities (and other
        // non-living things) are left alone. Skips invulnerable / dead entities cheaply.
        List<Entity> nearby = level.getEntities((Entity) null, damageBox, e -> e instanceof LivingEntity && e.isAlive());
        for (Entity e : nearby) {
            double dist = e.position().distanceTo(center);
            if (dist >= radius) continue;
            float falloff = 1.0f - (float) (dist / radius);
            float damage = peakDamage * falloff;
            if (damage < 0.5f) continue;
            e.hurt(source, damage);

            // Light knockback away from the burst origin — sells the "blast" without ragdolling.
            // Scaled with the amplification so the water case feels appropriately stronger.
            double pushScale = (waterAmplified ? 1.8 : 0.6) * falloff;
            Vec3 push = e.position().subtract(center).normalize().scale(pushScale);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.25 * falloff, push.z));
            e.hurtMarked = true;
        }

        // ---- block breakage ----
        if (ServerConfig.starCollapseBreakBlocks) breakBlocksInRadius(level, hammerPos, radius);

        // ---- item + XP wipe ----
        // Run AFTER block-breaking. Block-breaking no longer drops loot (perf — see method docs),
        // so this pass mostly catches pre-existing dropped items and XP orbs. Uncontained Black
        // Holes are exempt.
        wipeLooseEntitiesInRadius(level, hammerPos, radius);

        // ---- UCB hover lock ----
        // Hold every surviving Uncontained Black Hole in place for the duration of the explosion
        // + implosion animations. The implosion is visually a vortex pulling matter inward; loose
        // items dropping to the ground mid-animation would break the illusion.
        freezeBlackHolesInRadius(level, hammerPos, radius);

        // ---- client-side burst ----
        // Broadcast the burst to every player whose tracking range includes the hammer position.
        // The packet target radius scales with the visual radius so distant viewers of the bigger
        // water-amplified burst still receive the trigger (the visual is ~30 blocks wide; people
        // within ~60 blocks should see it land).
        double networkRadius = Math.max(96.0, radius * 2.5);
        PacketDistributor.TargetPoint target = new PacketDistributor.TargetPoint(
                center.x, center.y, center.z, networkRadius, level.dimension());
        ModMessages.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> target),
                new LightBurstPacket(center.x, center.y, center.z, radius, COLLAPSE_COLOR_RGB,
                        level.dimension().location().toString()));
    }

    /**
     * Sweeps every loose item entity and XP orb inside the burst radius and discards them.
     * Uncontained Black Hole stacks are exempt — they're the canonical "explosion-immune item"
     * of the mod, both for in-fiction reasons (a contained singularity isn't going to be erased
     * by a flash) and so the player can plant a stack inside the blast zone and recover it after.
     *
     * <p>Serves two purposes:
     * <ul>
     *   <li><b>Performance</b> — a 30-block water-amplified burst sweeps hundreds of chunks. Any
     *       items / XP orbs left behind would tick for 5 minutes before despawning, dragging
     *       server tick + chunk-render. Sweeping them up front collapses that load to zero.</li>
     *   <li><b>Realism</b> — a singularity-class detonation incinerating only mobs and terrain
     *       but politely leaving a pile of cobblestone and a glittering XP cloud reads as a bug.
     *       Vaporising loose entities matches the visual.</li>
     * </ul>
     *
     * <p>The Uncontained Black Hole exemption is enforced here AND on the item class via
     * {@code canBeHurtBy} (which catches TNT / creeper / wither explosions). Both checks exist
     * because our collapse bypasses the damage system entirely (it discards entities wholesale
     * rather than calling hurt), so the damage-tag-based immunity wouldn't fire here on its own.
     */
    /** Total game-tick duration the UCB items remain hovering: 15 ticks of supernova flash (0.75s)
     *  + 24 ticks of implosion (1.2s) + 2 ticks slack so the fade-out completes before gravity
     *  returns. Matches the client renderer's EXPLOSION_LIFETIME + IMPLOSION_LIFETIME (1.95s). */
    private static final long UCB_HOVER_TICKS = 41L;

    /** Wraps {@link BlackHoleHoverManager#freeze} around every UCB ItemEntity in the burst. The
     *  freshly-spawned UCBs from the crush loop are also caught (they were added to the world
     *  before {@code triggerCollapseBurst} ran). */
    private static void freezeBlackHolesInRadius(ServerLevel level, BlockPos center, float radius) {
        Vec3 c = Vec3.atCenterOf(center);
        AABB box = new AABB(c, c).inflate(radius);
        double rSq = radius * (double) radius;
        var blackHole = QuantumChanneling.UNCONTAINED_BLACK_HOLE.get();
        long unlockTick = level.getGameTime() + UCB_HOVER_TICKS;

        List<ItemEntity> ucbs = level.getEntitiesOfClass(ItemEntity.class, box,
                ie -> ie.getItem().is(blackHole) && ie.isAlive());
        for (ItemEntity ie : ucbs) {
            if (ie.position().distanceToSqr(c) > rSq) continue;
            BlackHoleHoverManager.freeze(level, ie, unlockTick);
        }
    }

    private static void wipeLooseEntitiesInRadius(ServerLevel level, BlockPos center, float radius) {
        Vec3 c = Vec3.atCenterOf(center);
        AABB box = new AABB(c, c).inflate(radius);
        double rSq = radius * (double) radius;
        var blackHole = QuantumChanneling.UNCONTAINED_BLACK_HOLE.get();

        // Single entity query covering both classes — `getEntities` filters internally by the
        // predicate, so the UCB exemption check skips adding those instances to the result list
        // entirely. Faster than two separate queries (each one walks the chunk entity sections).
        List<Entity> entities = level.getEntities((Entity) null, box, e ->
                (e instanceof ItemEntity ie && !ie.getItem().is(blackHole))
                        || e instanceof ExperienceOrb);
        for (Entity e : entities) {
            if (!e.isAlive()) continue;
            if (e.position().distanceToSqr(c) > rSq) continue;   // strict sphere
            e.discard();
        }
    }

    /**
     * Spherical block-break pass. The collapse burst is OP — it overrides vanilla explosion
     * resistance entirely. Every block inside {@code radius} gets removed regardless of how
     * resistant it would normally be, with two exceptions:
     *
     * <ul>
     *   <li><b>Bedrock</b> — the world's structural floor / nether ceiling stays put. Letting the
     *       burst chew through bedrock would let players fall into the void.</li>
     *   <li><b>Star Alloy Block</b> — the same material the hammer is forged from. Thematically,
     *       the alloy is what contains the collapse. Practically, it lets the player build a
     *       containment chamber for the blast.</li>
     * </ul>
     *
     * <h3>Fluid handling</h3>
     * <p>Uses direct {@code setBlock(pos, AIR, 3)} rather than {@link Level#removeBlock}. The
     * difference matters for fluids: {@code removeBlock} replaces the position with the
     * fluidstate's legacy block, so removing a water source sets the position to a fresh water
     * source — a no-op. Setting to air explicitly clears the block AND the fluid (waterlogged
     * blocks lose both their block and their water; pure water/lava sources become air). The
     * radius now genuinely "vaporises" water and lava, leaving an air bubble that surrounding
     * fluid will then flow back into over many ticks.
     *
     * <h3>Performance notes</h3>
     * <ul>
     *   <li><b>No drops</b> — no {@code dropResources} call, no ItemEntity spawn. The wipe pass
     *       would discard them anyway.</li>
     *   <li><b>No per-block destroy event</b> — the central collapse burst handles audio and VFX.
     *       50 k individual block-destroy sounds + particle clouds would be debug-spam-as-feature.</li>
     *   <li><b>Flag 3 (NEIGHBOR_UPDATES | BLOCK_UPDATE)</b> — same as vanilla destroyBlock. The
     *       neighbor updates ARE needed so water at the radius edge schedules its fluid tick and
     *       starts flowing back into the cleared region; without them the void would stay dry
     *       forever.</li>
     * </ul>
     *
     * <p>The hammer block's own position is skipped so the device that triggered the blast
     * survives the blast it caused.
     */
    private static void breakBlocksInRadius(ServerLevel level, BlockPos center, float radius) {
        int rInt = (int) Math.ceil(radius);
        double rSq = radius * (double) radius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Block starAlloy = QuantumChanneling.STAR_ALLOY_BLOCK.get();
        BlockState air = Blocks.AIR.defaultBlockState();
        // Spare FTB-claimed builds. Claims are per-chunk, so the reflective check is memoized per
        // chunk instead of run per block. Null cache = protection off (config or no FTB Chunks).
        java.util.HashMap<Long, Boolean> claimCache =
                ServerConfig.starCollapseRespectClaims ? new java.util.HashMap<>() : null;

        for (int dx = -rInt; dx <= rInt; dx++) {
            for (int dy = -rInt; dy <= rInt; dy++) {
                for (int dz = -rInt; dz <= rInt; dz++) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > rSq) continue;
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (cursor.equals(center)) continue;
                    if (claimCache != null) {
                        long chunkKey = net.minecraft.world.level.ChunkPos.asLong(cursor.getX() >> 4, cursor.getZ() >> 4);
                        Boolean claimed = claimCache.get(chunkKey);
                        if (claimed == null) {
                            claimed = ClaimGate.isChunkClaimed(level, cursor);
                            claimCache.put(chunkKey, claimed);
                        }
                        if (claimed) continue;
                    }
                    BlockState bs = level.getBlockState(cursor);
                    if (bs.isAir()) continue;
                    if (bs.is(Blocks.BEDROCK) || bs.is(starAlloy)) continue;

                    // Direct setBlock-to-air. Removes the block AND any fluid (water source, lava
                    // source, waterlogged fluid) in a single operation. Flag 3 schedules neighbor
                    // updates so surrounding fluid will eventually flow back into the void.
                    level.setBlock(cursor.immutable(), air, 3);
                }
            }
        }
    }

    /** Returns a template stack whose {@code count} encodes the per-input-item multiplier.
     *  Nether Star → 2 White Dwarfs. White Dwarf → 1 Uncontained Black Hole (the collapse loses
     *  half its mass to the burst — singularities aren't free). */
    private static ItemStack productFor(ItemStack input) {
        if (input.is(Items.NETHER_STAR)) {
            return new ItemStack(QuantumChanneling.WHITE_DWARF.get(), 2);
        }
        if (input.is(QuantumChanneling.WHITE_DWARF.get())) {
            return new ItemStack(QuantumChanneling.UNCONTAINED_BLACK_HOLE.get(), 1);
        }
        return ItemStack.EMPTY;
    }
}
