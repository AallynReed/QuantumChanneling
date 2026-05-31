package com.quantumchanneling.item;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.CreateChannelPacket;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.QuantumChannel;
import net.minecraft.ChatFormatting;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Portable emitter-configuration blueprint.
 *
 * <ul>
 *   <li><b>Sneak + right-click</b> a Photon Emitter → captures its void filter and every owned
 *       item subchannel (name + filter) into the card.</li>
 *   <li><b>Right-click</b> a Photon Emitter → applies the captured config to that emitter. The
 *       void filter is overwritten; any saved subchannel whose name doesn't already exist on the
 *       target emitter is created with the saved filter.</li>
 * </ul>
 *
 * <p>Receivers don't host subchannels, so the card is a no-op there.
 */
public class PhotonConfigCard extends Item {
    private static final String NBT_ROOT = "ConfigData";
    private static final String NBT_VOID = "VoidFilter";
    private static final String NBT_SUBS = "Subchannels";
    private static final String NBT_SUB_NAME = "Name";
    private static final String NBT_SUB_FILTER = "Filter";

    public PhotonConfigCard(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (!(be instanceof PhotonEmitterBlockEntity em)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        if (player.isShiftKeyDown()) {
            capture(stack, em);
            player.displayClientMessage(
                    Component.translatable("message.quantumchanneling.config_card.captured")
                            .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        } else if (hasConfig(stack)) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            ApplyResult r = apply(stack, em, sl, sp);
            player.displayClientMessage(r.message().withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            return r.ok() ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    public static boolean hasConfig(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_ROOT, Tag.TAG_COMPOUND);
    }

    private static void capture(ItemStack stack, PhotonEmitterBlockEntity em) {
        CompoundTag data = new CompoundTag();
        data.put(NBT_VOID, em.voidFilter().save());
        ListTag subs = new ListTag();
        for (ItemSubchannel sub : em.itemSubchannels()) {
            CompoundTag t = new CompoundTag();
            t.putString(NBT_SUB_NAME, sub.name());
            t.put(NBT_SUB_FILTER, sub.filter().save());
            subs.add(t);
        }
        data.put(NBT_SUBS, subs);
        stack.getOrCreateTag().put(NBT_ROOT, data);
    }

    private record ApplyResult(boolean ok, MutableComponent message) {}

    private static ApplyResult apply(ItemStack stack, PhotonEmitterBlockEntity em,
                                     ServerLevel level, ServerPlayer player) {
        CompoundTag root = stack.getTag();
        if (root == null) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.empty"));
        CompoundTag data = root.getCompound(NBT_ROOT);
        if (data.isEmpty()) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.empty"));

        UUID channelId = em.getChannelId();
        QuantumChannel ch = channelId != null
                ? ChannelData.get(level.getServer()).getChannel(channelId) : null;
        if (ch == null) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.unbound"));
        if (!ch.canManage(player.getUUID())) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.no_permission"));

        // Void: always blacklist mode on the target, regardless of the source card's flags.
        if (data.contains(NBT_VOID, Tag.TAG_COMPOUND)) {
            em.voidFilter().copyFrom(ItemFilter.load(data.getCompound(NBT_VOID)));
            em.voidFilter().setWhitelist(false);
            em.bumpLocalEdit();
        }

        // Subchannels: skip any name that already exists on the emitter, otherwise create with
        // the saved filter. Avoids duplicating subs when the same card is applied twice.
        Set<String> existing = new HashSet<>();
        for (ItemSubchannel s : em.itemSubchannels()) existing.add(s.name());

        ListTag subs = data.getList(NBT_SUBS, Tag.TAG_COMPOUND);
        int applied = 0;
        for (int i = 0; i < subs.size(); i++) {
            CompoundTag t = subs.getCompound(i);
            String name = t.getString(NBT_SUB_NAME);
            if (existing.contains(name)) continue;
            ItemFilter filter = t.contains(NBT_SUB_FILTER, Tag.TAG_COMPOUND)
                    ? ItemFilter.load(t.getCompound(NBT_SUB_FILTER))
                    : new ItemFilter(true);
            UUID id = em.createItemSubchannel(name);
            if (id == null) break;   // hit the per-emitter cap
            ItemSubchannel created = em.itemSubchannel(id);
            if (created != null) created.filter().copyFrom(filter);
            existing.add(name);
            applied++;
        }

        ChannelData.get(level.getServer()).setDirty();
        CreateChannelPacket.sendListBackTo(player);
        return new ApplyResult(true, Component.translatable(
                "message.quantumchanneling.config_card.applied", applied));
    }

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
        tooltip.add(Component.translatable("tooltip.quantumchanneling.config_card.type",
                        Component.translatable("tooltip.quantumchanneling.config_card.type_emitter"))
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
        if (data.contains(NBT_VOID, Tag.TAG_COMPOUND)) {
            ItemFilter vf = ItemFilter.load(data.getCompound(NBT_VOID));
            tooltip.add(Component.translatable("tooltip.quantumchanneling.config_card.void_size", vf.size())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @SuppressWarnings("unused")  // kept for future receiver-side state if/when needed
    private static boolean isReceiver(ChannelBoundBlockEntity bound) { return false; }
}
