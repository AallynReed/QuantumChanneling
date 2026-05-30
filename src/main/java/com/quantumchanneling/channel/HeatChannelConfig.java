package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Heat thermal-wire config. v1 master enable only — see {@link GasChannelConfig}. */
public class HeatChannelConfig {
    private boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        return tag;
    }
    public static HeatChannelConfig load(CompoundTag tag) {
        HeatChannelConfig c = new HeatChannelConfig();
        c.enabled = tag.getBoolean("Enabled");
        return c;
    }
    public void write(FriendlyByteBuf buf) { buf.writeBoolean(enabled); }
    public static HeatChannelConfig read(FriendlyByteBuf buf) {
        HeatChannelConfig c = new HeatChannelConfig();
        c.enabled = buf.readBoolean();
        return c;
    }
    public void copyFrom(HeatChannelConfig o) { if (o != null) this.enabled = o.enabled; }
}
