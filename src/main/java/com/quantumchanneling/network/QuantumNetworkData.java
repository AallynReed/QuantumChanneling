package com.quantumchanneling.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-save record of every quantum channel and the emitters/receivers bound to it. Stored on the
 * overworld's data folder so it persists across dimensions — channel members are {@link GlobalPos}
 * so any dimension can host any device on any channel.
 */
public class QuantumNetworkData extends SavedData {
    public static final String NAME = "quantumchanneling_network";

    private final Map<UUID, Set<GlobalPos>> channelMembers = new HashMap<>();
    private final Map<GlobalPos, UUID> memberChannel = new HashMap<>();

    public static QuantumNetworkData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(QuantumNetworkData::load, QuantumNetworkData::new, NAME);
    }

    public void addMember(UUID channelId, GlobalPos pos) {
        UUID existing = memberChannel.get(pos);
        if (channelId.equals(existing)) return;
        if (existing != null) removeMember(existing, pos);
        channelMembers.computeIfAbsent(channelId, k -> new HashSet<>()).add(pos);
        memberChannel.put(pos, channelId);
        setDirty();
    }

    public void removeMember(UUID channelId, GlobalPos pos) {
        Set<GlobalPos> members = channelMembers.get(channelId);
        if (members == null) return;
        if (members.remove(pos)) {
            memberChannel.remove(pos);
            if (members.isEmpty()) channelMembers.remove(channelId);
            setDirty();
        }
    }

    public Set<GlobalPos> getMembers(UUID channelId) {
        Set<GlobalPos> s = channelMembers.get(channelId);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    public @Nullable UUID getChannelOf(GlobalPos pos) {
        return memberChannel.get(pos);
    }

    public Set<UUID> getAllChannels() {
        return Collections.unmodifiableSet(channelMembers.keySet());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Set<GlobalPos>> entry : channelMembers.entrySet()) {
            CompoundTag channel = new CompoundTag();
            channel.putUUID("Id", entry.getKey());
            ListTag members = new ListTag();
            for (GlobalPos gp : entry.getValue()) {
                CompoundTag m = new CompoundTag();
                m.putString("Dim", gp.dimension().location().toString());
                m.putLong("Pos", gp.pos().asLong());
                members.add(m);
            }
            channel.put("Members", members);
            list.add(channel);
        }
        tag.put("Channels", list);
        return tag;
    }

    public static QuantumNetworkData load(CompoundTag tag) {
        QuantumNetworkData data = new QuantumNetworkData();
        ListTag list = tag.getList("Channels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag channel = list.getCompound(i);
            UUID id = channel.getUUID("Id");
            ListTag members = channel.getList("Members", Tag.TAG_COMPOUND);
            Set<GlobalPos> set = new HashSet<>(members.size());
            for (int j = 0; j < members.size(); j++) {
                CompoundTag m = members.getCompound(j);
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                        new ResourceLocation(m.getString("Dim")));
                GlobalPos gp = GlobalPos.of(dim, BlockPos.of(m.getLong("Pos")));
                set.add(gp);
                data.memberChannel.put(gp, id);
            }
            if (!set.isEmpty()) data.channelMembers.put(id, set);
        }
        return data;
    }
}
