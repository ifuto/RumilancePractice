package com.rumilance.practice.join;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinQuitMessagesTest {

    /**
     * 1.92.33: no join/quit line exists anywhere any more — the class must not carry any
     * broadcast surface. The only public method left is the kick/ban event silencer.
     */
    @Test
    void exposesNoBroadcastSurface() {
        List<Method> publics = List.of(JoinQuitMessages.class.getDeclaredMethods()).stream()
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toList();
        assertEquals(1, publics.size(), "only the kick silencer may be public");
        assertEquals("apply", publics.get(0).getName());
        assertTrue(takesKickEvent(publics.get(0)),
                "the silencer takes the kick event");
    }

    private static boolean takesKickEvent(Method m) {
        return m.getParameterCount() == 1
                && m.getParameterTypes()[0].getSimpleName().equals("PlayerKickEvent");
    }
}
