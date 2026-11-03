# ArreglarItems (Arreglar + Rompedor en un solo plugin)

- `/arreglar`: con el menú abierto, shift + click derecho a un ítem de MythicMobs de tu inventario.
  - Sin runas: se destruye y recibes su versión nueva (`mm i give`).
  - Con runas: se cierra el menú y se abre el rompedor.
- `/rompedor` (alias `/romper`): pones un ítem en el slot central (o shift + click desde el inventario),
  se destruye y recibes su versión limpia y todas sus runas de AdvancedEnchantments al 100%.
- `/arreglar reload` o `/rompedor reload`: recarga el `config.yml` completo.

Permisos: `arreglar.usar`, `arreglar.admin`, `rompedor.usar`, `rompedor.admin`.

Requisitos: Paper/Purpur 1.21.x, Java 21, MythicMobs y AdvancedEnchantments.
**Quita el plugin RompedorEncantamientos**: este lo reemplaza.

Compilar: sube el repo a GitHub y descarga el artefacto `ArreglarItems` en Actions.
Local: `mvn clean package`.
