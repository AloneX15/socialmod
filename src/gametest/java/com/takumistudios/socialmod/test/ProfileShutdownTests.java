package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.theme.SeriesProfiles;
import com.takumistudios.socialmod.common.model.SeriesPack;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.List;
import java.util.concurrent.*;

/** A queued write and its dependent activation must finish while the client stops. */
final class ProfileShutdownTests {
    static void run(ClientGameTestContext context) {
        var release=new CountDownLatch(1);
        try {
            // Seed a valid snapshot independently of whichever editor profile ran before.
            var seed=context.computeOnClient(client->com.takumistudios.socialmod.client.theme.RowTemplates.save(com.takumistudios.socialmod.common.model.RowDesign.defaults()).thenCompose(v->com.takumistudios.socialmod.client.theme.LocalSeriesDesign.save(new com.takumistudios.socialmod.common.model.VisualDesign())));
            context.waitFor(client->seed.isDone());seed.join();
            var field=SeriesProfiles.class.getDeclaredField("IO"); field.setAccessible(true);
            var executor=(ExecutorService)field.get(null);
            var started=new CountDownLatch(1);
            executor.submit(()->{ started.countDown(); try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } });
            if(!started.await(5,TimeUnit.SECONDS)) throw new AssertionError("Profile worker did not start");
            var saved=context.computeOnClient(client->SeriesProfiles.save(null,"Shutdown regression","TakumiStudios","1",List.of()));
            var activated=saved.thenCompose(SeriesProfiles::activate);
            context.runOnClient(client->{ release.countDown(); SeriesProfiles.shutdown(); });
            if(!saved.isDone() || !activated.isDone()) throw new AssertionError("Pending profile work was not drained");
            var file=saved.join(); activated.join();
            if(!SeriesPack.active(context.computeOnClient(client->SeriesProfiles.root())).equals(file.getFileName().toString())) throw new AssertionError("Shutdown lost queued activation");
            SeriesPack.read(file);
        } catch(Exception e) { throw new AssertionError("Profile shutdown regression",e); }
        finally { release.countDown(); }
    }
    private ProfileShutdownTests() { }
}
