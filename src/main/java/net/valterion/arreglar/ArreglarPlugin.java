package net.valterion.arreglar;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArreglarPlugin extends JavaPlugin {

    private ArreglarMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!AEBridge.init(getLogger())) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.menu = new ArreglarMenu(this);
        getServer().getPluginManager().registerEvents(new ArreglarListener(this), this);
    }

    public String raw(String key) {
        return getConfig().getString("mensajes." + key, key);
    }

    public void send(Player p, String key) {
        p.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + raw(key)));
    }

    public void sendError(Player p, String code) {
        String msg = raw("error");
        msg = msg.contains("<codigo>") ? msg.replace("<codigo>", code) : msg + " (" + code + ")";
        p.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("arreglar.admin")) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + raw("sin-permiso")));
                return true;
            }
            reloadConfig();
            sender.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + raw("recargado")));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + raw("solo-jugadores")));
            return true;
        }
        if (!p.hasPermission("arreglar.usar")) {
            send(p, "sin-permiso");
            return true;
        }
        menu.open(p);
        return true;
    }
}
