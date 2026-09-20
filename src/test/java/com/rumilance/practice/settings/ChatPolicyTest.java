package com.rumilance.practice.settings;

import com.rumilance.practice.model.PlayerSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reception matrix for the chat settings screen: defaults open, every flag independent,
 * friend and non-friend branches separate, and a missing settings row never mutes anyone.
 */
class ChatPolicyTest {

    private static PlayerSettings settings() {
        return PlayerSettings.defaultsFor(UUID.randomUUID(), "auto");
    }

    @Test
    void everythingIsReceivedByDefault() {
        PlayerSettings s = settings();
        assertTrue(ChatPolicy.receivesGlobalChat(s));
        assertTrue(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.FRIEND));
        assertTrue(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.OTHER));
        assertTrue(ChatPolicy.receivesJoinQuit(s, ChatPolicy.Relation.FRIEND));
        assertTrue(ChatPolicy.receivesJoinQuit(s, ChatPolicy.Relation.OTHER));
    }

    @Test
    void missingSettingsNeverMutes() {
        assertTrue(ChatPolicy.receivesGlobalChat(null));
        assertTrue(ChatPolicy.receivesMessage(null, ChatPolicy.Relation.OTHER));
        assertTrue(ChatPolicy.receivesJoinQuit(null, ChatPolicy.Relation.FRIEND));
    }

    @Test
    void strangerMessagesCanBeBlockedWithoutTouchingFriends() {
        PlayerSettings s = settings().withReceiveStrangerMessages(false);
        assertFalse(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.OTHER));
        assertTrue(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.FRIEND));
    }

    @Test
    void friendMessagesCanBeBlockedWithoutTouchingStrangers() {
        PlayerSettings s = settings().withReceiveFriendMessages(false);
        assertFalse(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.FRIEND));
        assertTrue(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.OTHER));
    }

    @Test
    void joinQuitFlagsAreIndependentOfEachOtherAndOfMessages() {
        PlayerSettings s = settings()
                .withReceiveStrangerJoinQuit(false)
                .withReceiveFriendJoinQuit(false);
        assertFalse(ChatPolicy.receivesJoinQuit(s, ChatPolicy.Relation.OTHER));
        assertFalse(ChatPolicy.receivesJoinQuit(s, ChatPolicy.Relation.FRIEND));
        // Blocking the join/quit lines must not block whispers or chat.
        assertTrue(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.OTHER));
        assertTrue(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.FRIEND));
        assertTrue(ChatPolicy.receivesGlobalChat(s));
    }

    @Test
    void globalChatSwitchIsIndependent() {
        PlayerSettings s = settings().withReceiveGlobalChat(false);
        assertFalse(ChatPolicy.receivesGlobalChat(s));
        assertTrue(ChatPolicy.receivesMessage(s, ChatPolicy.Relation.OTHER));
    }

    @Test
    void unrelatedSettingsChangesKeepTheReceptionFlags() {
        PlayerSettings s = settings()
                .withReceiveStrangerMessages(false)
                .withReceiveStrangerJoinQuit(false)
                .withSoundsEnabled(false)
                .withShowMatchReport(true)
                .withTeamGlow(false);
        assertFalse(s.receiveStrangerMessages());
        assertFalse(s.receiveStrangerJoinQuit());
        assertTrue(s.receiveGlobalChat());
        assertTrue(s.receiveFriendMessages());
        assertTrue(s.receiveFriendJoinQuit());
    }
}
