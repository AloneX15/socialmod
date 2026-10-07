package com.takumistudios.socialmod.test;
import com.takumistudios.socialmod.server.storage.*;
import com.google.gson.JsonElement;
import java.util.*;

/** Measures the real main-thread preparation with a large historical index. */
final class StoragePerformanceProbe {
    static void measure() {
        for(int active:List.of(50,200)) {
            StorageBackend backend=new StorageBackend() {
                public String id(){return "benchmark";} public JsonElement read(String b,String k){return null;}
                public void write(String b,String k,JsonElement v){} public void delete(String b,String k){}
                public List<String> keys(String b){return List.of();} public void appendLine(String b,String v){} public void close(){}
            };
            var storage=new SocialStorage(backend,Runnable::run);
            for(int i=0;i<10000+active;i++)storage.getOrCreate(UUID.randomUUID(),"history"+i,1);
            long max=0,total=0;int calls=0;
            try {
                long begin=System.nanoTime();storage.flush();max=System.nanoTime()-begin;total=max;calls++;
                for(int i=0;i<Math.ceil((10000+active)/16.0)+2;i++) {
                    begin=System.nanoTime();storage.tick();long elapsed=System.nanoTime()-begin;max=Math.max(max,elapsed);total+=elapsed;calls++;
                }
                com.takumistudios.socialmod.SocialMod.LOGGER.info("[SocialMod] Storage preparation benchmark: active={}, historical=10000, calls={}, maxMs={}, totalMs={}",active,calls,max/1_000_000.0,total/1_000_000.0);
                if(max>1_000_000_000L)throw new AssertionError("Storage preparation call exceeded 1 second CI budget");
            } finally { storage.close(); }
        }
    }
    private StoragePerformanceProbe(){}
}
