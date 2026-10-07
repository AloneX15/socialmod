package com.takumistudios.socialmod.common;
import com.google.gson.*;
import com.takumistudios.socialmod.common.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SnapshotSyncTest {
    private JsonObject state() { return JsonParser.parseString(new Gson().toJson(new SnapshotDto())).getAsJsonObject(); }
    @Test void sectionsMergeAtomicallyAndUnchangedStateProducesNoDelta() {
        var base=state(); base.addProperty("large", "\u00e1\uD83D\uDE00".repeat(30000));
        var receiver=new SnapshotSync.Receiver(); var frames=SnapshotSync.frames(1,true,base);
        for(int i=0;i<frames.size()-1;i++) assertNull(receiver.accept(frames.get(i)));
        assertEquals(base,JsonParser.parseString(receiver.accept(frames.getLast())));
        var current=base.deepCopy(); current.addProperty("voice",true);
        var delta=SnapshotSync.delta(base,current); assertEquals(1,delta.size());
        assertEquals(current,JsonParser.parseString(receiver.accept(SnapshotSync.frames(2,false,delta).getFirst())));
        assertTrue(SnapshotSync.delta(current,current).isEmpty());
    }
    @Test void lostBaselineAndReorderedFramesRequireFullRecovery() {
        var receiver=new SnapshotSync.Receiver(); var base=state();
        assertThrows(IllegalArgumentException.class,()->receiver.accept(SnapshotSync.frames(2,false,base).getFirst()));
        assertNotNull(receiver.accept(SnapshotSync.frames(3,true,base).getFirst()));
        assertThrows(IllegalArgumentException.class,()->receiver.accept(SnapshotSync.frames(5,false,base).getFirst()));
        base.addProperty("large","x".repeat(40000)); var frames=SnapshotSync.frames(6,true,base);
        assertThrows(IllegalArgumentException.class,()->receiver.accept(frames.getLast()));
        assertNull(receiver.accept(frames.getFirst())); assertNotNull(receiver.accept(frames.getLast()));
    }
    @Test void oversizeIsRejectedBeforeSending() {
        var base=state();base.addProperty("large","x".repeat(SnapshotSync.MAX_STATE));
        assertThrows(IllegalArgumentException.class,()->SnapshotSync.frames(1,true,base));
    }
}
