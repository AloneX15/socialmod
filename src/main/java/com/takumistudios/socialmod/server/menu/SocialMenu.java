package com.takumistudios.socialmod.server.menu;

import com.takumistudios.socialmod.common.model.GroupIcon;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.server.Lang;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Menú social de cofre para jugadores sin el mod (Java vanilla y Bedrock con Geyser), PLAN 9: {@code /social}.
 * Es un menú vanilla del servidor: no necesita Polymer ni nada en el cliente. Los ítems no se pueden coger; cada
 * clic hace una acción (cambiar estado, escribir a un amigo, ver un grupo) igual que los comandos.
 * <pre>
 * fila 0: [estado] · [buzón] · · [ayuda] · · · [cerrar]
 * filas 1-3: amigos (verde = en línea, amarillo = ausente, rojo = no molestar, gris = desconectado)
 * fila 4: grupos y party
 * fila 5: (vacía)
 * </pre>
 */
public final class SocialMenu extends ChestMenu {
    private static final int SIZE = 54;

    private final SocialServer social;
    private final ServerPlayer viewer;
    private final SimpleContainer container;
    private final Map<Integer, Consumer<ServerPlayer>> actions = new HashMap<>();

    private SocialMenu(int id, Inventory inventory, SocialServer social, ServerPlayer viewer, SimpleContainer container) {
        super(MenuType.GENERIC_9x6, id, inventory, container, 6);
        this.social = social;
        this.viewer = viewer;
        this.container = container;
        fill();
    }

    public static void open(SocialServer social, ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SocialMenu(id, inventory, social, player, new SimpleContainer(SIZE)),
                Lang.tr(player, "socialmod.menu.title")));
    }

    private void fill() {
        container.clearContent();
        actions.clear();
        PlayerRecord self = social.record(viewer);

        PresenceStatus status = self.status == null ? PresenceStatus.ONLINE : self.status;
        set(0, statusItem(status), Lang.tr(viewer, "socialmod.menu.status", Lang.tr(viewer, "socialmod.status." + status.id())),
                List.of(Lang.tr(viewer, "socialmod.menu.status_hint")), player -> {
                    PresenceStatus next = PresenceStatus.byOrdinal((status.ordinal() + 1) % 4);
                    social.presence().setStatus(player, next);
                    fill();
                    broadcastChanges();
                });
        int unread = self.unread.values().stream().mapToInt(Integer::intValue).sum();
        set(2, unread > 0 ? Items.WRITABLE_BOOK : Items.BOOK, Lang.tr(viewer, "socialmod.menu.mailbox", unread),
                List.of(Lang.tr(viewer, "socialmod.menu.mailbox_hint")), player -> runCommand(player, "socialmod inbox"));
        set(4, Items.KNOWLEDGE_BOOK, Lang.tr(viewer, "socialmod.menu.help"),
                List.of(Lang.tr(viewer, "socialmod.menu.help_line1"), Lang.tr(viewer, "socialmod.menu.help_line2")), null);
        set(8, Items.BARRIER, Lang.tr(viewer, "socialmod.menu.close"), List.of(), ServerPlayer::closeContainer);

        // Amigos: primero los conectados
        List<UUID> friends = new ArrayList<>(self.friends);
        friends.sort((a, b) -> Boolean.compare(social.online(b) != null, social.online(a) != null));
        int slot = 9;
        for (UUID friend : friends) {
            if (slot > 35) {
                break;
            }
            PlayerRecord record = social.storage().player(friend);
            String name = record == null ? "?" : record.name;
            PresenceStatus friendStatus = social.presence().visibleStatus(viewer.getUUID(), friend);
            List<Component> lore = new ArrayList<>();
            lore.add(Lang.tr(viewer, "socialmod.status." + friendStatus.id()).withStyle(ChatFormatting.GRAY));
            if (record != null && record.customStatus != null && !record.customStatus.isEmpty() && friendStatus != PresenceStatus.OFFLINE) {
                lore.add(Component.literal("\"" + record.customStatus + "\"").withStyle(ChatFormatting.DARK_GRAY));
            }
            lore.add(Lang.tr(viewer, "socialmod.menu.friend_hint"));
            set(slot++, statusItem(friendStatus), Component.literal(name), lore, player -> {
                player.closeContainer();
                player.sendSystemMessage(suggest(Lang.tr(player, "socialmod.menu.write_to", name), "/pm " + name + " "));
            });
        }
        if (friends.isEmpty()) {
            set(22, Items.PAPER, Lang.tr(viewer, "socialmod.menu.no_friends"), List.of(Lang.tr(viewer, "socialmod.menu.no_friends_hint")), null);
        }

        // Grupos y party
        slot = 36;
        for (Group group : social.groups().groupsOf(viewer.getUUID())) {
            if (slot > 44) {
                break;
            }
            int online = (int) group.members.keySet().stream().filter(id -> social.online(id) != null).count();
            String glyph = GroupIcon.glyphOf(group.icon);
            Component title = group.party ? Component.literal("Party").withStyle(ChatFormatting.BLUE)
                    : Component.literal((glyph.isEmpty() ? "" : glyph + " ") + "[" + group.tag + "] " + group.name).withColor(group.color & 0xFFFFFF);
            List<Component> lore = new ArrayList<>();
            lore.add(Lang.tr(viewer, "socialmod.menu.group_members", online, group.members.size()).withStyle(ChatFormatting.GRAY));
            lore.add(Lang.tr(viewer, "socialmod.menu.group_role", Lang.tr(viewer, "socialmod.role." + group.roleOf(viewer.getUUID()).id())).withStyle(ChatFormatting.GRAY));
            lore.add(Lang.tr(viewer, "socialmod.menu.group_hint"));
            String command = group.party ? "/p " : "/g to " + group.tag + " " + group.defaultChannel() + " ";
            set(slot++, group.party ? item("blue_banner") : woolFor(group.color), title, lore, player -> {
                player.closeContainer();
                player.sendSystemMessage(suggest(Lang.tr(player, "socialmod.menu.write_group"), command));
            });
        }
    }

    private void set(int slot, Item item, Component name, List<Component> lore, Consumer<ServerPlayer> action) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(style -> style.withItalic(false)));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (Component line : lore) {
                lines.add(line.copy().withStyle(style -> style.withItalic(false)));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        container.setItem(slot, stack);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    private static MutableComponent suggest(Component label, String command) {
        return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    private static void runCommand(ServerPlayer player, String command) {
        player.closeContainer();
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    /** Ítem vanilla por id: en 26.3 los ítems de colores pasaron a {@code ColorCollection}, así vale para todas. */
    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
    }

    private static Item statusItem(PresenceStatus status) {
        return item(switch (status) {
            case ONLINE -> "lime_dye";
            case AWAY -> "yellow_dye";
            case DND -> "red_dye";
            case INVISIBLE -> "light_gray_dye";
            default -> "gray_dye";
        });
    }

    /** Lana del tinte más parecido al color del grupo. */
    private static Item woolFor(int rgb) {
        DyeColor best = DyeColor.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (DyeColor color : DyeColor.values()) {
            int c = color.getTextureDiffuseColor();
            long dr = ((rgb >> 16) & 0xFF) - ((c >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((c >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (c & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = color;
            }
        }
        return item(best.getSerializedName() + "_wool");
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        // Nunca se mueven ítems: ni del menú ni del inventario del jugador mientras está abierto
        if (slot >= 0 && slot < SIZE && player instanceof ServerPlayer serverPlayer) {
            Consumer<ServerPlayer> action = actions.get(slot);
            if (action != null) {
                action.accept(serverPlayer);
            }
        }
        broadcastFullState();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == viewer && player.isAlive();
    }
}
