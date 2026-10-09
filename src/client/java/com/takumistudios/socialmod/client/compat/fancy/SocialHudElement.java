package com.takumistudios.socialmod.client.compat.fancy;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.client.*;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.hud.ToastHud;
import com.takumistudios.socialmod.client.theme.RowTemplates;
import com.takumistudios.socialmod.common.model.RowDesign;
import de.keksuccino.fancymenu.customization.element.*;
import de.keksuccino.fancymenu.customization.element.editor.AbstractEditorElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import de.keksuccino.fancymenu.util.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Actual SocialMod data inside FancyMenu's HUD layout, without additional network traffic. */
public final class SocialHudElement extends AbstractElement {
    final String kind;
    boolean failed;
    private long hudFrames;
    private long checkedTick = -1, rowsRevision = -1;
    private int stateVersion = -1, measuredWidth = -1;
    private boolean cachedPreview;
    private Object partyMembers;
    private List<RowTemplates.Data> cachedRows = List.of();
    private int[] rowHeights = new int[0];
    private String measuredKind;
    private static final String[] ARROWS = {"\u2191","\u2197","\u2192","\u2198","\u2193","\u2199","\u2190","\u2196"};
    public final Property.StringProperty rowKind;
    SocialHudElement(Builder builder) {
        super(builder); kind = builder.kind;
        rowKind = putProperty(Property.stringProperty("socialmod_row", switch(kind) { case "toasts" -> "toast"; case "social" -> "conversation"; case "pings" -> "message"; default -> "party"; }, false, false, "socialmod.advanced.row_kind"));
        baseWidth = 180; baseHeight = 40;
    }
    @Override public boolean shouldRender() {
        boolean preview = isEditor();
        if (failed || !FancyBridge.spiffy() || ClientCompat.hudHidden()) return false;
        if (!preview && (!ClientState.get().connected() || (!kind.equals("toasts") && FancyBackend.gameScreenOpen))) return false;
        if (kind.equals("social") && !ClientConfig.get().hud.enabled) return false;
        if (kind.equals("party") && !ClientConfig.get().hud.partyHealth) return false;
        if (kind.equals("pings") && !ClientConfig.get().ping.enabled) return false;
        if (kind.equals("toasts") && !ClientConfig.get().toasts.enabled) return false;
        return super.shouldRender();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        if (!shouldRender()) return;
        boolean preview = isEditor();
        boolean clipped = false;
        try {
            var rows = cachedRows(preview);
            if(!preview && rows.isEmpty())return;
            if (!preview && !rows.isEmpty()) hudFrames++;
            int x = getAbsoluteX(), y = getAbsoluteY(), w = Math.max(24, getAbsoluteWidth());
            graphics.enableScissor(x, y, x + w, y + Math.max(12, getAbsoluteHeight())); clipped = true;

            var template = RowTemplates.template(rowKind.get());
            if (template == null) template = RowTemplates.template("party");
            String selectedKind = rowKind.get();
            if (measuredWidth != w || rowsRevision != RowTemplates.revision() || !Objects.equals(measuredKind, selectedKind)) {
                rowHeights = new int[rows.size()];
                for (int i = 0; i < rows.size(); i++) rowHeights[i] = RowTemplates.height(template, w, rows.get(i));
                measuredWidth = w; rowsRevision = RowTemplates.revision(); measuredKind = selectedKind;
            }
            for (int i = 0; i < rows.size(); i++) {
                RowTemplates.draw(graphics, template, x, y, w, rows.get(i), rowHeights[i]);
                y += rowHeights[i];
            }

        } catch (RuntimeException | LinkageError e) { failed = true; SocialMod.LOGGER.warn("Custom HUD component failed; restoring the basic component: " + kind, e); }
        finally { if (clipped) graphics.disableScissor(); }
    }
    private List<RowTemplates.Data> cachedRows(boolean preview) {
        long tick = FancyBackend.hudTick;
        if (checkedTick == tick && cachedPreview == preview) return cachedRows;
        checkedTick = tick;
        var state = ClientState.get();
        boolean changed = cachedPreview != preview || stateVersion != state.version()
                || (kind.equals("party") && partyMembers != PartyClient.members())
                || kind.equals("pings") || kind.equals("toasts");
        if (changed || stateVersion == -1) {
            var next = rows(preview);
            if (!next.equals(cachedRows)) { cachedRows = List.copyOf(next); measuredWidth = -1; }
            stateVersion = state.version(); partyMembers = PartyClient.members(); cachedPreview = preview;
        }
        return cachedRows;
    }
    private List<RowTemplates.Data> rows(boolean preview) {
        var state = ClientState.get();
        List<RowTemplates.Data> rows = new ArrayList<>();
        if (kind.equals("social")) {
            String team = state.snapshot().teams.stream().filter(t -> t.id.equals(state.snapshot().self.teamId)).map(t -> t.name).findFirst().orElse("");
            rows.add(new RowTemplates.Data(state.selfId(), Map.of("name", Component.literal(team), "team", Component.literal(team), "text", Component.translatable("socialmod.advanced.social_summary", Component.translatable("socialmod.status."+state.snapshot().self.status), state.totalUnread()), "status", Component.translatable("socialmod.status."+state.snapshot().self.status), "unread", Component.literal(Integer.toString(state.totalUnread()))), -1, false, false));
        } else if (kind.equals("party")) {
            for (var member : PartyClient.members()) rows.add(new RowTemplates.Data(member.player(), Map.of("name", Component.literal(member.name()), "text", Component.literal(member.health() < 0 ? "?" : Math.round(member.health()) + " / " + Math.round(member.maxHealth()))), member.maxHealth() > 0 ? member.health() / member.maxHealth() : -1, false, false));
        } else if (kind.equals("pings")) {
            for (var active : PartyClient.pings()) {
                var ping = active.ping(); var minecraft = Minecraft.getInstance(); var player = minecraft.player;
                String where = ping.x() + " " + ping.y() + " " + ping.z();
                String arrow = "";
                if (player != null && minecraft.level != null && ping.dimension().equals(minecraft.level.dimension().identifier().toString())) {
                    double dx=ping.x()+.5-player.getX(), dz=ping.z()+.5-player.getZ();
                    int distance=(int)Math.round(Math.sqrt(dx*dx+dz*dz));
                    String[] arrows={"\u2191","\u2197","\u2192","\u2198","\u2193","\u2199","\u2190","\u2196"};
                    arrow=ARROWS[Math.floorMod((int)Math.round((Math.toDegrees(Math.atan2(-dx,dz))-player.getYRot())/45),8)];
                    where=arrow+" "+distance+" m | "+where;
                }
                rows.add(new RowTemplates.Data(null,Map.of("name",Component.literal(ping.name()),"text",Component.literal(where),"icon",Component.literal(arrow)), -1,false,false));
            }
        } else for (var toast : ToastHud.customizationRows()) rows.add(RowTemplates.Data.text(null, toast.title(), toast.body()));
        if (preview && rows.isEmpty()) rows.add(RowTemplates.Data.text(Minecraft.getInstance().getUser().getProfileId(), "SocialMod", "TEAM Forest | 3 | 18:30"));
        return rows;
    }
    public static final class Builder extends ElementBuilder<SocialHudElement, Editor> {
        final String kind;
        public Builder(String kind) { super("socialmod_hud_" + kind); this.kind = kind; }
        @Override public SocialHudElement buildDefaultInstance() { return new SocialHudElement(this); }
        @Override public SocialHudElement deserializeElement(SerializedElement serialized) { return buildDefaultInstance(); }
        @Override protected SerializedElement serializeElement(SocialHudElement element, SerializedElement serialized) { return serialized; }
        @Override public Editor wrapIntoEditorElement(SocialHudElement element, LayoutEditorScreen editor) { return new Editor(element, editor); }
        @Override public Component getDisplayName(AbstractElement element) { return Component.translatable("socialmod.advanced.hud." + kind); }
        @Override public Component[] getDescription(AbstractElement element) { return new Component[]{Component.translatable("socialmod.advanced.hud.description")}; }
        @Override public boolean shouldShowUpInEditorElementMenu(LayoutEditorScreen editor) { return FancyBridge.spiffy() && editor.layoutTargetScreen instanceof de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen; }
    }
    public static final class Editor extends AbstractEditorElement<Editor, SocialHudElement> {
        Editor(SocialHudElement element, LayoutEditorScreen editor) { super(element, editor); }
    }
}
