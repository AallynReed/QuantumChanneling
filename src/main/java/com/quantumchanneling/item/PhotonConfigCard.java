package com.quantumchanneling.item;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.CreateChannelPacket;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.QuantumChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Portable device-configuration "blueprint" item.
 *
 * <ul>
 *   <li><b>Sneak + right-click</b> a Photon Emitter or Receiver → captures its current
 *       configuration into the card: per-emitter void filter (if applicable) plus the
 *       <em>contents</em> of each subscribed subchannel (name + filter).</li>
 *   <li><b>Right-click</b> (no sneak) on a Photon Emitter or Receiver → applies the captured
 *       configuration to the device. Subchannels are matched on the target's channel <em>by
 *       name</em>: existing matches reuse the existing UUID, missing names get created on the
 *       channel (using the saved filter). The device's subscriptions are reset to the card's
 *       saved list in order.</li>
 * </ul>
 *
 * <p>Applying requires {@code canManage} on the target's channel — the card can't be used to
 * create subchannels on a channel the player doesn't admin.
 */
public class PhotonConfigCard extends Item {
    private static final String NBT_ROOT = "ConfigData";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_VOID = "VoidFilter";
    private static final String NBT_SUBS = "Subchannels";
    private static final String NBT_SUB_NAME = "Name";
    private static final String NBT_SUB_FILTER = "Filter";

    private static final String TYPE_EMITTER = "EMITTER";
    private static final String TYPE_RECEIVER = "RECEIVER";

    public PhotonConfigCard(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;     // client predicts success
        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (!(be instanceof ChannelBoundBlockEntity bound)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        if (player.isShiftKeyDown()) {
            capture(stack, bound, sl);
            player.displayClientMessage(
                    Component.translatable("message.quantumchanneling.config_card.captured")
                            .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        } else if (hasConfig(stack)) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            ApplyResult r = apply(stack, bound, sl, sp);
            player.displayClientMessage(r.message().withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            return r.ok() ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    public static boolean hasConfig(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_ROOT, Tag.TAG_COMPOUND);
    }

    /* ---- capture ---- */

    private static void capture(ItemStack stack, ChannelBoundBlockEntity bound, ServerLevel level) {
        CompoundTag data = new CompoundTag();
        boolean isEmitter = bound instanceof PhotonEmitterBlockEntity;
        data.putString(NBT_TYPE, isEmitter ? TYPE_EMITTER : TYPE_RECEIVER);

        if (isEmitter) {
            PhotonEmitterBlockEntity em = (PhotonEmitterBlockEntity) bound;
            data.put(NBT_VOID, em.voidFilter().save());
        }

        UUID channelId = bound.getChannelId();
        QuantumChannel ch = channelId != null
                ? ChannelData.get(level.getServer()).getChannel(channelId) : null;
        ListTag subs = new ListTag();
        if (ch != null) {
            for (UUID subId : bound.getSubscribedSubchannels()) {
                ItemSubchannel sub = ch.itemConfig().subchannel(subId);
                if (sub == null) continue;
                CompoundTag t = new CompoundTag();
                t.putString(NBT_SUB_NAME, sub.name());
                t.put(NBT_SUB_FILTER, sub.filter().save());
                subs.add(t);
            }
        }
        data.put(NBT_SUBS, subs);
        stack.getOrCreateTag().put(NBT_ROOT, data);
    }

    /* ---- apply ---- */

    private record ApplyResult(boolean ok, MutableComponent message) {}

    private static ApplyResult apply(ItemStack stack, ChannelBoundBlockEntity bound,
                                     ServerLevel level, ServerPlayer player) {
        CompoundTag root = stack.getTag();
        if (root == null) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.empty"));
        CompoundTag data = root.getCompound(NBT_ROOT);
        if (data.isEmpty()) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.empty"));

        UUID channelId = bound.getChannelId();
        QuantumChannel ch = channelId != null
                ? ChannelData.get(level.getServer()).getChannel(channelId) : null;
        if (ch == null) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.unbound"));
        if (!ch.canManage(player.getUUID())) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.no_permission"));

        boolean isEmitter = bound instanceof PhotonEmitterBlockEntity;

        // Apply void (emitter only). Force blacklist mode after copy — void is hard-locked to
        // "items in list → voided" semantics regardless of what the source card stored.
        if (isEmitter && data.contains(NBT_VOID, Tag.TAG_COMPOUND)) {
            PhotonEmitterBlockEntity em = (PhotonEmitterBlockEntity) bound;
            em.voidFilter().copyFrom(ItemFilter.load(data.getCompound(NBT_VOID)));
            em.voidFilter().setWhitelist(false);
            em.bumpLocalEdit();
        }

        // Clear current subscriptions (and reverse index entries if emitter).
        GlobalPos here = GlobalPos.of(level.dimension(), bound.getBlockPos());
        Set<UUID> oldSubs = new LinkedHashSet<>(bound.getSubscribedSubchannels());
        for (UUID id : oldSubs) bound.removeSubscribedSubchannel(id);
        if (isEmitter) ch.unregisterAllForEmitter(here);

        // Walk templates, match-by-name or create.
        Map<String, ItemSubchannel> byName = new HashMap<>();
        for (ItemSubchannel s : ch.itemConfig().subchannels()) byName.put(s.name(), s);

        ListTag subs = data.getList(NBT_SUBS, Tag.TAG_COMPOUND);
        int applied = 0;
        for (int i = 0; i < subs.size(); i++) {
            CompoundTag t = subs.getCompound(i);
            String name = t.getString(NBT_SUB_NAME);
            ItemFilter filter = t.contains(NBT_SUB_FILTER, Tag.TAG_COMPOUND)
                    ? ItemFilter.load(t.getCompound(NBT_SUB_FILTER))
                    : new ItemFilter(true);

            UUID subId;
            ItemSubchannel existing = byName.get(name);
            if (existing != null) {
                subId = existing.id();
            } else {
                subId = ch.itemConfig().createSubchannel(name);
                if (subId != null) {
                    ItemSubchannel created = ch.itemConfig().subchannel(subId);
                    if (created != null) created.filter().copyFrom(filter);
                    byName.put(name, ch.itemConfig().subchannel(subId));
                }
            }
            if (subId != null) {
                bound.addSubscribedSubchannel(subId);
                if (isEmitter) ch.registerEmitterSubscription(here, subId);
                applied++;
            }
        }

        ChannelData.get(level.getServer()).setDirty();
        CreateChannelPacket.sendListBackTo(player);
        return new ApplyResult(true, Component.translatable(
                "message.quantumchanneling.config_card.applied", applied));
    }

    /* ---- tooltip ---- */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(NBT_ROOT, Tag.TAG_COMPOUND)) {
            tooltip.add(Component.translatable("tooltip.quantumchanneling.config_card.empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.quantumchanneling.config_card.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        CompoundTag data = root.getCompound(NBT_ROOT);
        String type = data.getString(NBT_TYPE);
        tooltip.add(Component.translatable("tooltip.quantumchanneling.config_card.type",
                type.equals(TYPE_EMITTER)
                        ? Component.translatable("tooltip.quantumchanneling.config_card.type_emitter")
                        : Component.translatable("tooltip.quantumchanneling.config_card.type_receiver"))
                .withStyle(ChatFormatting.AQUA));

        ListTag subs = data.getList(NBT_SUBS, Tag.TAG_COMPOUND);
        tooltip.add(Component.translatable("tooltip.quantumchanneling.config_card.sub_count", subs.size())
                .withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < Math.min(subs.size(), 5); i++) {
            tooltip.add(Component.literal(" • " + subs.getCompound(i).getString(NBT_SUB_NAME))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (subs.size() > 5) {
            tooltip.add(Component.literal(" … +" + (subs.size() - 5))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (TYPE_EMITTER.equals(type) && data.contains(NBT_VOID, Tag.TAG_COMPOUND)) {
            ItemFilter vf = ItemFilter.load(data.getCompound(NBT_VOID));
            tooltip.add(Component.translatable("tooltip.quantumchanneling.config_card.void_size", vf.size())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
