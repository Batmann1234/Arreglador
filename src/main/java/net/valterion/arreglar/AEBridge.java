package net.valterion.arreglar;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/** Acceso a AEAPI por reflexión: compila sin el jar de AdvancedEnchantments. */
final class AEBridge {

    private static Method method;
    private static Logger log;

    private AEBridge() {
    }

    static boolean init(Logger logger) {
        log = logger;
        try {
            Class<?> c = Class.forName("net.advancedplugins.ae.api.AEAPI");
            method = c.getMethod("getEnchantmentsOnItem", ItemStack.class);
            return true;
        } catch (Throwable t) {
            log.severe("No se encontró AEAPI#getEnchantmentsOnItem en tu versión de AdvancedEnchantments: " + t);
            return false;
        }
    }

    /**
     * Mapa vacío si el ítem no tiene encantamientos (AE puede devolver null en ese caso).
     * Devuelve null SOLO si la llamada falló; quien llama debe abortar sin consumir el ítem.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> enchants(ItemStack item) {
        try {
            Object r = method.invoke(null, item);
            if (r == null) return new HashMap<>();
            return (Map<String, Integer>) r;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            log.warning("Fallo leyendo los encantamientos de AE: " + c);
            return null;
        }
    }
}
