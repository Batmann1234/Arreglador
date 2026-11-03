package net.valterion.arreglar;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class ArreglarListener implements Listener {

    private static final Pattern SAFE = Pattern.compile("[\\p{L}\\p{N}_\\-.]+");

    private final ArreglarPlugin plugin;
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Long> last = new HashMap<>();

    public ArreglarListener(ArreglarPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean empty(ItemStack i) {
        return i == null || i.getType().isAir();
    }

    private static boolean isOurs(Inventory inv) {
        return inv != null && inv.getHolder() instanceof ArreglarMenu.Holder;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        busy.remove(e.getPlayer().getUniqueId());
        last.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (isOurs(e.getView().getTopInventory())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isOurs(top)) return;

        // Con el menú abierto no se mueve ningún ítem: ni hacia el menú ni dentro del inventario.
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null || clicked == top) return;
        if (e.getClick() != ClickType.SHIFT_RIGHT) return;

        int slot = e.getSlot();
        if (slot < 0 || slot > 35) return; // solo inventario principal + hotbar
        ItemStack item = clicked.getItem(slot);
        if (empty(item)) return;

        process(p, clicked, slot, item);
    }

    private void process(Player p, Inventory inv, int slot, ItemStack item) {
        UUID id = p.getUniqueId();
        if (!busy.add(id)) return;
        try {
            long now = System.currentTimeMillis();
            long cd = plugin.getConfig().getLong("arreglar.cooldown-ms", 100);
            Long prev = last.get(id);
            if (prev != null && now - prev < cd) {
                plugin.send(p, ArreglarPlugin.ARREGLAR, "cooldown");
                return;
            }
            if (item.getAmount() != 1) {
                plugin.send(p, ArreglarPlugin.ARREGLAR, "cantidad");
                return;
            }

            // 1) Identidad por NBT de MythicMobs (no por nombre ni lore)
            MythicBukkit mythic = MythicBukkit.inst();
            String type = mythic.getItemManager().getMythicTypeFromItem(item);
            if (type == null || !SAFE.matcher(type).matches()) {
                plugin.send(p, ArreglarPlugin.ARREGLAR, "no-mythic");
                return;
            }

            // 2) Solo IDs de la lista
            String target = null;
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("arreglar.items");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    if (key.equalsIgnoreCase(type)) {
                        String v = sec.getString(key);
                        target = (v == null || v.isBlank()) ? type : v;
                        break;
                    }
                }
            }
            if (target == null) {
                plugin.send(p, ArreglarPlugin.ARREGLAR, "no-permitido");
                return;
            }
            if (!SAFE.matcher(target).matches()) {
                fail(p, "E1", "ID nuevo inválido en config para " + type + ": " + target);
                return;
            }

            // 3) ¿Tiene runas? (NBT leído por la API de AE; lo que ya trae de fábrica no cuenta)
            Map<String, Integer> raw = AEBridge.enchants(item);
            ItemStack freshSelf = safeItem(mythic, type);
            Map<String, Integer> rawBase = empty(freshSelf) ? null : AEBridge.enchants(freshSelf);
            if (raw == null || rawBase == null) {
                fail(p, "E2", raw == null ? "no se pudieron leer los encantamientos del ítem"
                        : "MythicMobs no devolvió el ítem base de " + type + " o falló la lectura de AE");
                return;
            }
            Map<String, Integer> onItem = lower(raw);
            Map<String, Integer> base = lower(rawBase);
            boolean hasRunes = false;
            for (Map.Entry<String, Integer> en : onItem.entrySet()) {
                int lvl = en.getValue() == null ? 0 : en.getValue();
                if (lvl > base.getOrDefault(en.getKey(), 0)) {
                    hasRunes = true;
                    break;
                }
            }
            if (hasRunes) {
                // No se consume nada: se cierra este menú y se abre el rompedor.
                plugin.send(p, ArreglarPlugin.ARREGLAR, "tiene-runas");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.closeInventory();
                    if (!p.hasPermission("rompedor.usar")) {
                        plugin.send(p, ArreglarPlugin.ROMPEDOR, "sin-permiso");
                        return;
                    }
                    plugin.rompedorMenu().open(p);
                });
                return;
            }

            // 4) El ítem nuevo tiene que existir ANTES de consumir nada
            if (empty(safeItem(mythic, target))) {
                fail(p, "E3", "el ítem nuevo no existe o no se pudo crear en MythicMobs: " + target);
                return;
            }

            // 5) Consumir primero, entregar después (síncrono). El hueco liberado garantiza espacio.
            ItemStack cur = inv.getItem(slot);
            if (empty(cur) || cur.getAmount() != 1) {
                fail(p, "E4", "el slot " + slot + " ya no tiene el ítem");
                return;
            }
            inv.setItem(slot, null);
            last.put(id, now);

            String audit = "jugador=" + p.getName() + " item=" + type + " nuevo=" + target;
            log(audit);
            try {
                String cmd = plugin.getConfig().getString("arreglar.comandos.item-nuevo")
                        .replace("<jugador>", p.getName()).replace("<item>", target);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Exception ex) {
                plugin.getLogger().severe("FALLO ENTREGANDO -> " + audit + " : " + ex);
                log("FALLO " + audit);
                plugin.sendError(p, ArreglarPlugin.ARREGLAR, "E5");
                return;
            }

            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            plugin.send(p, ArreglarPlugin.ARREGLAR, "listo");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Error inesperado procesando a " + p.getName(), ex);
            plugin.sendError(p, ArreglarPlugin.ARREGLAR, "E9");
        } finally {
            busy.remove(id);
            Bukkit.getScheduler().runTask(plugin, p::updateInventory);
        }
    }

    private void fail(Player p, String code, String why) {
        plugin.getLogger().warning("[" + p.getName() + "] " + code + ": " + why);
        plugin.sendError(p, ArreglarPlugin.ARREGLAR, code);
    }

    private static ItemStack safeItem(MythicBukkit mythic, String id) {
        try {
            return mythic.getItemManager().getItemStack(id);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Map<String, Integer> lower(Map<String, Integer> in) {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, Integer> en : in.entrySet()) {
            if (en.getKey() != null) out.put(en.getKey().toLowerCase(Locale.ROOT), en.getValue());
        }
        return out;
    }

    private void log(String line) {
        try {
            plugin.getDataFolder().mkdirs();
            File f = new File(plugin.getDataFolder(), "registro.log");
            try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
                w.println("[" + LocalDateTime.now() + "] " + line);
            }
        } catch (Exception ignored) {
        }
    }
}
