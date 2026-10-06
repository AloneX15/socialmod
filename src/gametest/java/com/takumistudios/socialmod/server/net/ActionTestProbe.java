package com.takumistudios.socialmod.server.net;

import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.server.SocialServer;
import net.minecraft.server.level.ServerPlayer;

/** Test-only access to the exact production C2S permission dispatcher. */
public final class ActionTestProbe {
    public static void dispatch(SocialServer social, ServerPlayer player, Payloads.ActionC2S payload) {
        ServerNet.handleAction(social, player, payload);
    }
}
