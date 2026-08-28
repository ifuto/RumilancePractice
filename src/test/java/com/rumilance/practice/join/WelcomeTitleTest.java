package com.rumilance.practice.join;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WelcomeTitleTest {

    @Test
    void sweepFramesKeepTheBrandPlainText() {
        assertEquals("N Arena", PlainTextComponentSerializer.plainText().serialize(WelcomeTitle.frame(0)));
        assertEquals("N Arena", PlainTextComponentSerializer.plainText().serialize(WelcomeTitle.frame(3)));
        assertEquals("N Arena", PlainTextComponentSerializer.plainText().serialize(WelcomeTitle.frame(-1)));
    }

    @Test
    void sweepColourIsLighterThanBaseAqua() {
        assertNotEquals(WelcomeTitle.AQUA.value(), WelcomeTitle.SWEEP.value());
    }
}
