package net.valterion.arreglar;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin único con dos menús:
 *  - /arreglar : cambia un ítem sin runas por su versión nueva.
 *  - /rompedor : destruye un ítem y devuelve su versión limpia + todas sus runas.
 */
public final class ArreglarPlugin extends JavaPlugin {

    public static final String ARREGLAR = "arreglar";
    public static final String ROMPEDOR = "rompedor";

    private ArreglarMenu arreglarMenu;
    private RompedorMenu rompedorMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!AEBridge.init(getLogger())) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.arreglarMenu = new ArreglarMenu(this);
        this.rompedorMenu = new RompedorMenu(this);
        getServer().getPluginManager().registerEvents(new ArreglarListener(this), this);
        getServer().getPluginManager().registerEvents(new RompedorListener(this, rompedorMenu), this);
    }

    public RompedorMenu rompedorMenu() {
        return rompedorMenu;
    }

    /** Lee mensajes.<sección>.<clave>. */
    public String raw(String section, String key) {
        return getConfig().getString("mensajes." + section + "." + key, key);
    }

    private void deliver(CommandSender to, String section, String text) {
        to.sendMessage(MiniMessage.miniMessage().deserialize(raw(section, "prefix") + text));
    }

    public void send(CommandSender to, String section, String key, String... replace) {
        String text = raw(section, key);
        for (int i = 0; i + 1 < replace.length; i += 2) {
            text = text.replace(replace[i], replace[i + 1]);
        }
        deliver(to, section, text);
    }

    public void sendError(Player p, String section, String code) {
        String msg = raw(section, "error");
        msg = msg.contains("<codigo>") ? msg.replace("<codigo>", code) : msg + " (" + code + ")";
        deliver(p, section, msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String section = cmd.getName().equalsIgnoreCase("rompedor") ? ROMPEDOR : ARREGLAR;

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(section + ".admin")) {
                send(sender, section, "sin-permiso");
                return true;
            }
            reloadConfig(); // recarga la config completa (los dos menús)
            send(sender, section, "recargado");
            return true;
        }
        if (!(sender instanceof Player p)) {
            send(sender, section, "solo-jugadores");
            return true;
        }
        if (!p.hasPermission(section + ".usar")) {
            send(p, section, "sin-permiso");
            return true;
        }
        if (section.equals(ROMPEDOR)) rompedorMenu.open(p);
        else arreglarMenu.open(p);
        return true;
    }
}
