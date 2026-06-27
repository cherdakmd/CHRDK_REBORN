package ru.example.vkchatstreams;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class StreamCommand implements CommandExecutor, Listener {
    private final VKChatStreamsPlugin plugin;
    private final StreamManager manager;
    private final String TITLE = ChatColor.DARK_PURPLE + "🎥 Стримерский центр";

    public StreamCommand(VKChatStreamsPlugin plugin, StreamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && sender.hasPermission("vkchat.streams.admin")) {
            if (args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                manager.loadData();
                sender.sendMessage(ChatColor.GREEN + "VKChatStreams перезагружен.");
                return true;
            }
            if (args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(ChatColor.GOLD + "Активных стримов: " + manager.getActiveStreams().size());
                return true;
            }
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("/stream reload | /stream list");
            return true;
        }
        Player p = (Player) sender;
        if (args.length > 0 && (args[0].equalsIgnoreCase("apply") || args[0].equalsIgnoreCase("заявка"))) {
            if (args.length < 4) {
                p.sendMessage(ChatColor.RED + "Использование: /stream apply <twitch|vk|youtube|manual> <канал> <ссылка> [описание]");
                sendApplySuggest(p);
                return true;
            }
            String desc = args.length >= 5 ? join(args, 4) : "";
            manager.submitApplicationFromMinecraft(p, args[1], args[2], args[3], desc);
            return true;
        }
        openGui(p);
        return true;
    }

    public void openGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fill(inv);
        List<StreamManager.LiveStream> active = manager.getActiveStreams();
        StreamManager.StreamerProfile me = manager.getProfileByPlayer(p);

        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "🎥 Стримерский центр",
                ChatColor.GRAY + "Активные стримы, заявки, награды,",
                ChatColor.GRAY + "голосования, промокоды и правила.",
                ChatColor.GRAY + "Активных стримов: " + ChatColor.GREEN + active.size()));

        inv.setItem(10, item(Material.REDSTONE_TORCH, ChatColor.GREEN + "🟢 Активные стримы",
                ChatColor.GRAY + "Список live-стримеров на сервере.",
                ChatColor.YELLOW + "Нажми на карточку стрима, чтобы получить ссылку."));

        inv.setItem(12, item(Material.WRITABLE_BOOK, ChatColor.AQUA + "📝 Подать заявку стримера",
                ChatColor.GRAY + "Теперь заявку можно подать из игры.",
                ChatColor.YELLOW + "/stream apply <platform> <channel> <link> [описание]",
                "",
                ChatColor.GRAY + "ЛКМ: получить кликабельную подсказку команды"));

        inv.setItem(14, item(Material.PLAYER_HEAD, ChatColor.GOLD + "👤 Мой статус стримера",
                me == null ? ChatColor.RED + "Заявка не найдена" : ChatColor.GRAY + "Платформа: " + ChatColor.YELLOW + me.platform,
                me == null ? ChatColor.GRAY + "Подай заявку через этот GUI" : ChatColor.GRAY + "Канал: " + ChatColor.WHITE + me.channel,
                me == null ? "" : ChatColor.GRAY + "Статус: " + (me.approved ? ChatColor.GREEN + "approved" : ChatColor.YELLOW + "ожидает проверки"),
                me == null ? "" : ChatColor.GRAY + "Предупреждений: " + ChatColor.RED + me.warnings));

        inv.setItem(16, item(Material.LIME_DYE, ChatColor.GREEN + "📡 Ручной LIVE-режим",
                ChatColor.GRAY + "Доступен только approved-стримерам.",
                ChatColor.YELLOW + "ВК: !стрим live <название>",
                ChatColor.YELLOW + "ВК: !стрим stop"));

        inv.setItem(28, item(Material.EMERALD, ChatColor.GREEN + "🎁 Награды и промокоды",
                ChatColor.GRAY + "Промокоды создаются по milestones.",
                ChatColor.GRAY + "Зрители активируют: " + ChatColor.YELLOW + "!стрим промо <код>",
                ChatColor.GRAY + "Награды: репутация + шанс редкого приза."));

        inv.setItem(30, item(Material.COMPARATOR, ChatColor.AQUA + "🗳 Голосования зрителей",
                ChatColor.GRAY + "Зрители голосуют за баффы, события",
                ChatColor.GRAY + "и челленджи стримеру.",
                ChatColor.YELLOW + "ВК: !стрим голос <1-4>"));

        inv.setItem(32, item(Material.MAP, ChatColor.YELLOW + "📋 Челленджи",
                ChatColor.GRAY + "PvE, крафт, экономика и социальные задания.",
                ChatColor.GRAY + "Запускаются на milestones или голосованием."));

        inv.setItem(34, item(Material.BARRIER, ChatColor.RED + "⚠ Правила и антиабуз",
                ChatColor.GRAY + "Ручной режим защищён кулдаунами.",
                ChatColor.GRAY + "За фейки: предупреждение, кулдаун, снятие approved."));

        inv.setItem(36, item(Material.PURPLE_WOOL, ChatColor.LIGHT_PURPLE + "Twitch",
                ChatColor.GRAY + "Основная платформа.", ChatColor.YELLOW + "platform: twitch"));
        inv.setItem(37, item(Material.BLUE_WOOL, ChatColor.BLUE + "VK Видео / Live",
                ChatColor.GRAY + "Приоритетная ВК-платформа.", ChatColor.YELLOW + "platform: vk"));
        inv.setItem(38, item(Material.RED_WOOL, ChatColor.RED + "YouTube",
                ChatColor.GRAY + "Поддерживается как доп. платформа.", ChatColor.YELLOW + "platform: youtube"));
        inv.setItem(39, item(Material.GRAY_WOOL, ChatColor.GRAY + "Manual",
                ChatColor.GRAY + "Ручной режим без API.", ChatColor.YELLOW + "platform: manual"));

        if (active.isEmpty()) {
            inv.setItem(22, item(Material.CAMPFIRE, ChatColor.RED + "Сейчас нет активных стримов",
                    ChatColor.GRAY + "Активные стримы появятся здесь.",
                    ChatColor.GRAY + "ВК-команда: !стрим список"));
        } else {
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 40, 41, 42, 43, 44};
            int i = 0;
            for (StreamManager.LiveStream ls : active) {
                if (i >= slots.length) break;
                StreamManager.StreamerProfile sp = manager.getProfile(ls.vkId);
                if (sp == null) continue;
                long mins = (System.currentTimeMillis() - ls.startedAt) / 60000L;
                inv.setItem(slots[i++], item(Material.REDSTONE_TORCH, ChatColor.GREEN + "LIVE: " + sp.channel,
                        ChatColor.GRAY + "Платформа: " + ChatColor.YELLOW + sp.platform.toUpperCase(),
                        ChatColor.GRAY + "Название: " + ChatColor.WHITE + ls.title,
                        ChatColor.GRAY + "Длительность: " + ChatColor.AQUA + mins + " мин.",
                        ChatColor.GRAY + "Ссылка: " + ChatColor.AQUA + sp.link,
                        "",
                        ChatColor.YELLOW + "ЛКМ: получить кликабельную ссылку",
                        ChatColor.LIGHT_PURPLE + "ВК: !стрим промо <код> / !стрим голос <1-4>"));
            }
        }
        inv.setItem(49, item(Material.BOOK, ChatColor.AQUA + "❔ Быстрая помощь",
                ChatColor.YELLOW + "/stream apply twitch MyChannel https://twitch.tv/MyChannel описание",
                ChatColor.GRAY + "VK-админ подтверждает: !стрим approve <vk>",
                ChatColor.GRAY + "После подтверждения: !стрим live <название>"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        int clicked = e.getRawSlot();
        if (clicked == 12 || clicked == 49) {
            p.closeInventory();
            sendApplySuggest(p);
            return;
        }
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 40, 41, 42, 43, 44};
        int idx = 0;
        for (StreamManager.LiveStream ls : manager.getActiveStreams()) {
            if (idx >= slots.length) break;
            StreamManager.StreamerProfile sp = manager.getProfile(ls.vkId);
            if (sp == null) continue;
            if (clicked == slots[idx]) {
                p.closeInventory();
                TextComponent tc = new TextComponent(ChatColor.LIGHT_PURPLE + "▶ Смотреть стрим " + sp.channel);
                tc.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, sp.link));
                p.spigot().sendMessage(tc);
                return;
            }
            idx++;
        }
    }

    private void sendApplySuggest(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "========== " + ChatColor.LIGHT_PURPLE + "Заявка стримера" + ChatColor.DARK_PURPLE + " ==========");
        TextComponent twitch = new TextComponent(ChatColor.AQUA + "[Подать Twitch-заявку] ");
        twitch.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stream apply twitch channel https://twitch.tv/channel описание"));
        p.spigot().sendMessage(twitch);
        TextComponent vk = new TextComponent(ChatColor.BLUE + "[Подать VK-заявку] ");
        vk.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stream apply vk channel https://live.vkvideo.ru/channel описание"));
        p.spigot().sendMessage(vk);
        TextComponent yt = new TextComponent(ChatColor.RED + "[Подать YouTube-заявку]");
        yt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stream apply youtube channel https://youtube.com/@channel описание"));
        p.spigot().sendMessage(yt);
        p.sendMessage(ChatColor.GRAY + "Формат: /stream apply <platform> <channel> <link> [описание]");
    }

    private String join(String[] a, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }
}
