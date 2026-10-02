package com.takumistudios.socialmod.client;

import com.takumistudios.socialmod.api.client.SocialModClientAPI;
import com.takumistudios.socialmod.api.client.ToastData;
import com.takumistudios.socialmod.client.hud.ToastHud;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.net.SnapshotDto;

import java.util.UUID;

/** Implementación de {@link SocialModClientAPI}. */
public final class ClientApiImpl implements SocialModClientAPI {
    public static final ClientApiImpl INSTANCE = new ClientApiImpl();

    private ClientApiImpl() {
    }

    @Override
    public void openPanel() {
        SocialModClient.openPanel(null);
    }

    @Override
    public void openPrivateChat(UUID target) {
        UUID self = ClientState.get().selfId();
        if (self != null && !self.equals(target)) {
            SocialModClient.openPanel(ConversationId.direct(self, target).key());
        }
    }

    @Override
    public void openGroupChat(String groupId) {
        SnapshotDto.GroupView group = ClientState.get().group(groupId);
        if (group != null) {
            String channel = group.channels.isEmpty() ? "general" : group.channels.getFirst().name;
            SocialModClient.openPanel(ConversationId.group(groupId, channel).key());
        }
    }

    @Override
    public void showToast(ToastData toast) {
        ToastHud.push(toast);
    }

    @Override
    public boolean isConnected() {
        return ClientState.get().connected();
    }
}
