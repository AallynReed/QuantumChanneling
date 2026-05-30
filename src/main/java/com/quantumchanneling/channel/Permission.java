package com.quantumchanneling.channel;

public enum Permission {
    USER,   // can use the channel: bind devices to it, charge from it
    ADMIN;  // can use + manage members and settings (rename, delete, set permissions, charging mode)

    public boolean canManage() { return this == ADMIN; }
}
