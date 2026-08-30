package com.rumilance.practice.scoreboard;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabFightListServiceTest {

    @Test
    void blankPadProfileStaysWithinMinecraftLimit() throws Exception {
        Field field = TabFightListService.class.getDeclaredField("BLANK_PAD_PROFILE");
        field.setAccessible(true);
        String name = (String) field.get(null);
        assertEquals(" ", name);
        assertTrue(name.length() >= 1 && name.length() <= 16);
    }
}
