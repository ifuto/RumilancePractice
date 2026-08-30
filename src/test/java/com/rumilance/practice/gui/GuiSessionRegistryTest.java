package com.rumilance.practice.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiSessionRegistryTest {

    @Test
    void openInheritsFromGameMenuExceptWhenOpeningGameMenu() {
        GuiSessionRegistry registry = new GuiSessionRegistry();
        UUID playerId = UUID.randomUUID();

        GuiSession select = registry.open(playerId, GuiType.EKIT_SELECT, 6);
        select.setFromGameMenu(true);

        GuiSession editor = registry.open(playerId, GuiType.EDIT_KIT, 5);
        assertTrue(editor.fromGameMenu());

        GuiSession menu = registry.open(playerId, GuiType.GAME_MENU, 6);
        assertFalse(menu.fromGameMenu());
    }

    @Test
    void battleMenuOpenedFromGameMenuReturnsToGameMenuAndChildrenReturnToBattle() {
        GuiSessionRegistry registry = new GuiSessionRegistry();
        UUID playerId = UUID.randomUUID();

        registry.open(playerId, GuiType.GAME_MENU, 6);
        GuiSession battle = registry.open(playerId, GuiType.BATTLE_MENU, 6);
        assertTrue(battle.fromGameMenu());
        assertFalse(battle.fromBattleMenu());

        GuiSession ranked = registry.open(playerId, GuiType.RANKED_QUEUE, 6);
        assertTrue(ranked.fromBattleMenu());
        assertTrue(ranked.fromGameMenu());
    }

    @Test
    void openDoesNotMarkFromGameMenuWhenPreviousWasNot() {
        GuiSessionRegistry registry = new GuiSessionRegistry();
        UUID playerId = UUID.randomUUID();
        registry.open(playerId, GuiType.EKIT_SELECT, 6);
        GuiSession editor = registry.open(playerId, GuiType.EDIT_KIT, 5);
        assertFalse(editor.fromGameMenu());
    }
}
