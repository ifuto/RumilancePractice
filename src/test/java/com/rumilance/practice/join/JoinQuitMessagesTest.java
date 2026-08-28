package com.rumilance.practice.join;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinQuitMessagesTest {

    @Test
    void joinAndQuitKeepRequestedShape() {
        assertEquals("[+] Alice", PlainTextComponentSerializer.plainText().serialize(JoinQuitMessages.join("Alice")));
        assertEquals("[-] Bob", PlainTextComponentSerializer.plainText().serialize(JoinQuitMessages.quit("Bob")));
    }
}
