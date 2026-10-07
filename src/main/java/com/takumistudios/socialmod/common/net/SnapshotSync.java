package com.takumistudios.socialmod.common.net;

import com.google.gson.*;
import java.util.*;

/** Bounded, atomic section updates. Protocol 4; local cache keeps plain SnapshotDto JSON. */
public final class SnapshotSync {
    public static final int CHUNK = 32_000, MAX_STATE = 8 * 1024 * 1024, MAX_CHUNKS = 300;
    public record Frame(long revision,boolean full,int index,int count,String data) { }
    public static JsonObject delta(JsonObject previous,JsonObject current) {
        var patch=new JsonObject();
        for(var entry:current.entrySet()) if(previous==null || !entry.getValue().equals(previous.get(entry.getKey()))) patch.add(entry.getKey(),entry.getValue());
        return patch;
    }
    public static List<String> frames(long revision,boolean full,JsonObject patch) {
        String json=patch.toString();
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_STATE) throw new IllegalArgumentException("Social state exceeds 8 MiB");
        var pieces=new ArrayList<String>();
        for(int offset=0;offset<json.length();) { int end=Math.min(json.length(),offset+CHUNK); if(end<json.length() && Character.isHighSurrogate(json.charAt(end-1))) end--; pieces.add(json.substring(offset,end)); offset=end; }
        if(pieces.size()>MAX_CHUNKS) throw new IllegalArgumentException("Too many snapshot chunks");
        var gson=new Gson(); var result=new ArrayList<String>();
        for(int i=0;i<pieces.size();i++) result.add(gson.toJson(new Frame(revision,full,i,pieces.size(),pieces.get(i))));
        return result;
    }
    public static final class Receiver {
        private JsonObject state;
        private long revision, incoming;
        private String[] pieces;
        private int next, bytes;
        private boolean full;
        public void reset() { state=null; revision=incoming=0; pieces=null; next=bytes=0; }
        public String accept(String json) {
            com.takumistudios.socialmod.common.model.JsonBudget.checkDepth(json);
            var f=new Gson().fromJson(json,Frame.class);
            if(f==null || f.revision()<=revision || f.count()<1 || f.count()>MAX_CHUNKS || f.index()<0 || f.index()>=f.count() || f.data()==null || f.data().length()>CHUNK) throw new IllegalArgumentException("Invalid snapshot frame");
            if(f.index()==0) {
                if(!f.full() && (state==null || f.revision()!=revision+1)) throw new IllegalArgumentException("Missing snapshot baseline");
                incoming=f.revision(); pieces=new String[f.count()]; next=bytes=0; full=f.full();
            }
            if(pieces==null || incoming!=f.revision() || next!=f.index() || pieces.length!=f.count() || full!=f.full()) throw new IllegalArgumentException("Out of order snapshot");
            bytes+=f.data().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(bytes>MAX_STATE) { pieces=null; throw new IllegalArgumentException("Snapshot exceeds client budget"); }
            pieces[next++]=f.data(); if(next<pieces.length) return null;
            String complete=String.join("",pieces);com.takumistudios.socialmod.common.model.JsonBudget.checkDepth(complete);
            var patch=JsonParser.parseString(complete).getAsJsonObject();
            var merged=full ? new JsonObject() : state.deepCopy();
            for(var entry:patch.entrySet()) merged.add(entry.getKey(),entry.getValue());
            if(merged.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_STATE) throw new IllegalArgumentException("Merged state exceeds budget");
            // Verify required sections before replacing the baseline.
            for(String key:List.of("self","visual","groups","friends","conversations")) if(!merged.has(key) || merged.get(key).isJsonNull()) throw new IllegalArgumentException("Incomplete state");
            state=merged; revision=incoming; pieces=null; return merged.toString();
        }
    }
    private SnapshotSync() { }
}
