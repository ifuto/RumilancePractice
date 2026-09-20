package com.rumilance.practice.settings;

import com.rumilance.practice.model.PlayerSettings;

/**
 * 「メッセージの受信」— who a player accepts traffic from.
 *
 * <p>Five independent switches, decided from the <em>receiver's</em> settings only:
 * global chat, TELL/WHISPER from friends, TELL/WHISPER from everyone else, friend
 * join/quit lines and other players' join/quit lines. Keeping the decision here (pure,
 * no Bukkit) means the same matrix drives the chat path, the whisper path and the
 * join/quit broadcast, and JUnit can exercise every branch.</p>
 *
 * <p>The friend system does not exist yet, so callers currently pass
 * {@link Relation#OTHER} for everybody; the friend branches are wired and ready for it.
 * A null settings object means "not loaded / server default" and always allows the
 * message — a settings lookup failure must never silently mute a player.</p>
 */
public final class ChatPolicy {

    /** How the receiver is related to the sender (or to the player joining/quitting). */
    public enum Relation {
        /** On the receiver's friend list. */
        FRIEND,
        /** Anyone else. */
        OTHER
    }

    private ChatPolicy() {
    }

    /** Whether the receiver wants global chat at all. */
    public static boolean receivesGlobalChat(PlayerSettings receiver) {
        return receiver == null || receiver.receiveGlobalChat();
    }

    /** Whether a TELL/WHISPER from {@code relation} reaches this receiver. */
    public static boolean receivesMessage(PlayerSettings receiver, Relation relation) {
        if (receiver == null) {
            return true;
        }
        return relation == Relation.FRIEND
                ? receiver.receiveFriendMessages()
                : receiver.receiveStrangerMessages();
    }

    /** Whether a {@code [+] name} / {@code [-] name} line for {@code subject} is shown. */
    public static boolean receivesJoinQuit(PlayerSettings receiver, Relation subject) {
        if (receiver == null) {
            return true;
        }
        return subject == Relation.FRIEND
                ? receiver.receiveFriendJoinQuit()
                : receiver.receiveStrangerJoinQuit();
    }
}
