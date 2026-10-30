package qouteall.imm_ptl.core.mc_utils;

import net.minecraftforge.event.TickEvent;

import de.nick1st.imm_ptl.events.ServerCleanupEvent;
import net.minecraft.server.MinecraftServer;
import qouteall.q_misc_util.event.IPEventBus;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.q_misc_util.my_util.MyTaskList;

public class ServerTaskList {
    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
            of(event.server()).processTasks();
        });

        IPEventBus.EVENT_BUS.addListener(ServerCleanupEvent.class, event -> {
            MinecraftServer server = event.server;
            of(server).forceClearTasks();
        });
    }

    // the tasks are executed after ticking. will be cleared when server closes
    public static MyTaskList of(MinecraftServer server) {
        return IPPerServerInfo.of(server).taskList;
    }
}
