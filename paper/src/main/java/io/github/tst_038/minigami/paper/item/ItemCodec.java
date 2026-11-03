package io.github.tst_038.minigami.paper.item;

import java.util.Base64;
import org.bukkit.inventory.ItemStack;

/** An item as text, the bytes Paper writes for it, which it upgrades when reading them on a newer version. */
public final class ItemCodec {

    private ItemCodec() {
    }

    /** Reads server state, so call it on the server thread. */
    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String text) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
    }
}
