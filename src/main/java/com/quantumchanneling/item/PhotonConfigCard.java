package com.quantumchanneling.item;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.CreateChannelPacket;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.SubchannelBase;
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
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof PhotonEmitterBlockEntity em)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        ItemStack stack = ctx.getItemInHand();
        if (player.isShiftKeyDown()) {
            capture(stack, em);
            sp.sendOverlayMessage(Component.translatable("message.quantumchanneling.config_card.captured")
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.CONSUME;
        }
        CompoundTag data = stack.get(QuantumChanneling.CONFIG_CARD_DATA.get());
        if (data == null) return InteractionResult.PASS;
        ApplyResult r = apply(data, em, sl, sp);
        sp.sendOverlayMessage(r.message().withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return r.ok() ? InteractionResult.CONSUME : InteractionResult.FAIL;
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
        stack.set(QuantumChanneling.CONFIG_CARD_DATA.get(), data);
    }

    private record ApplyResult(boolean ok, MutableComponent message) {}

    private static ApplyResult apply(CompoundTag data, PhotonEmitterBlockEntity em,
                                     ServerLevel level, ServerPlayer player) {
        UUID channelId = em.getChannelId();
        QuantumChannel ch = channelId != null ? ChannelData.get(level.getServer()).getChannel(channelId) : null;
        if (ch == null) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.unbound"));
        if (!ch.canManage(player.getUUID())) return new ApplyResult(false, Component.translatable(
                "message.quantumchanneling.config_card.no_permission"));

        // Void: always blacklist mode on the target, regardless of the source card's flags.
        data.getCompound(NBT_VOID).ifPresent(t -> {
            em.voidFilter().copyFrom(ItemFilter.load(t));
            em.voidFilter().setWhitelist(false);
            em.bumpLocalEdit();
        });

        // Subchannels: skip any name that already exists on the emitter, otherwise create with
        // the saved filter. Avoids duplicating subs when the same card is applied twice.
        Set<String> existing = new HashSet<>();
        for (ItemSubchannel s : em.itemSubchannels()) existing.add(s.name());

        int applied = 0;
        for (Tag entry : data.getListOrEmpty(NBT_SUBS)) {
            if (!(entry instanceof CompoundTag t)) continue;
            String name = t.getStringOr(NBT_SUB_NAME, "");
            if (existing.contains(name)) continue;
            UUID id = em.createSubchannel(Kind.ITEM, name);
            if (id == null) break;   // hit the per-emitter cap
            SubchannelBase<?> created = em.subchannel(Kind.ITEM, id);
            if (created != null) t.getCompound(NBT_SUB_FILTER).ifPresent(f -> created.filter().copyFrom(ItemFilter.load(f)));
            existing.add(name);
            applied++;
        }

        ChannelData.get(level.getServer()).setDirty();
        CreateChannelPacket.sendListBackTo(player);
        return new ApplyResult(true, Component.translatable(
                "message.quantumchanneling.config_card.applied", applied));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = stack.get(QuantumChanneling.CONFIG_CARD_DATA.get());
        if (data == null) {
            tooltip.accept(Component.translatable("tooltip.quantumchanneling.config_card.empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.quantumchanneling.config_card.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.accept(Component.translatable("tooltip.quantumchanneling.config_card.type",
                        Component.translatable("tooltip.quantumchanneling.config_card.type_emitter"))
                .withStyle(ChatFormatting.AQUA));

        ListTag subs = data.getListOrEmpty(NBT_SUBS);
        tooltip.accept(Component.translatable("tooltip.quantumchanneling.config_card.sub_count", subs.size())
                .withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < Math.min(subs.size(), 5); i++) {
            String name = subs.getCompoundOrEmpty(i).getStringOr(NBT_SUB_NAME, "");
            tooltip.accept(Component.literal(" • " + name).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (subs.size() > 5) {
            tooltip.accept(Component.literal(" … +" + (subs.size() - 5)).withStyle(ChatFormatting.DARK_GRAY));
        }
        data.getCompound(NBT_VOID).ifPresent(t -> tooltip.accept(
                Component.translatable("tooltip.quantumchanneling.config_card.void_size", ItemFilter.load(t).size())
                        .withStyle(ChatFormatting.GRAY)));
    }
}
