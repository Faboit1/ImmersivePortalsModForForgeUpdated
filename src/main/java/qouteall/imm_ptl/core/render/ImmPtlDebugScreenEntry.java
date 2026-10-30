package qouteall.imm_ptl.core.render;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.List;

/**
 * The portal rendering debug text in the F3 screen.
 * It can be toggled in the debug options screen (F3 + F6).
 */
public class ImmPtlDebugScreenEntry implements DebugScreenEntry {
    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("immersive_portals", "portal_rendering");
    
    public static void init() {
        DebugScreenEntries.register(ID, new ImmPtlDebugScreenEntry());
    }
    
    @Override
    public void display(
        DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel,
        @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk
    ) {
        List<String> debugText = RenderStates.collectDebugText();
        if (IPGlobal.moveDebugTextToTop) {
            debugText.forEach(displayer::addPriorityLine);
        }
        else {
            displayer.addToGroup(ID, debugText);
        }
    }
}
