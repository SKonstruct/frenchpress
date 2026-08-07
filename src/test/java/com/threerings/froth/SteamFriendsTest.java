package com.threerings.froth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SteamFriendsTest {

    @Test
    public void testActivateGameOverlayToWebPage_NullModeThrowsNPE() {
        assertThrows(NullPointerException.class, () -> {
            SteamFriends.activateGameOverlayToWebPage("https://example.com", null);
        }, "Should throw NullPointerException when WebPageMode is null");
    }
}
