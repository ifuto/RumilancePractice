package com.rumilance.practice.gui;

import com.rumilance.practice.model.KitDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** The text-only form of a kit's chosen display name for plain-string lore lines. */
public final class KitDisplayNames {

    private KitDisplayNames() {
    }

    public static String plain(KitDefinition kit) {
        if (kit == null) {
            return "";
        }
        // Display names may contain MiniMessage colours/decoration. The child list renders them
        // as Components; the short "Default child: ..." lore must show the same readable name,
        // not literal tags and not just the storage id.
        return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(kit.prettyDisplayName()));
    }
}
