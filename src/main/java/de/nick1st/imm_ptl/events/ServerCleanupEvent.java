package de.nick1st.imm_ptl.events;

import net.minecraft.server.MinecraftServer;


public class ServerCleanupEvent {
    public final MinecraftServer server;

    public ServerCleanupEvent(MinecraftServer server) {
        this.server = server;
    }
}
