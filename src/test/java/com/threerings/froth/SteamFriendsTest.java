package com.threerings.froth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SteamFriendsTest {

    /** SK's friends list calls this per row; without it the game's render thread dies. */
    @Test
    public void testHasFriend_NobodyIsAFriend() {
        assertFalse(SteamFriends.hasFriend(76561198000000000L, SteamFriends.FRIEND_FLAG_IMMEDIATE));
    }

    @Test
    public void testActivateGameOverlayToWebPage_NullModeThrowsNPE() {
        assertThrows(NullPointerException.class, () -> {
            SteamFriends.activateGameOverlayToWebPage("https://example.com", null);
        }, "Should throw NullPointerException when WebPageMode is null");
    }
}
