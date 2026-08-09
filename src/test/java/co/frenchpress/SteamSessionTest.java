package co.frenchpress;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SteamSessionTest {

    @Test
    public void testParse_NullBlob() {
        assertNull(SteamSession.StoredCreds.parse(null));
    }

    @Test
    public void testParse_EmptyBlob() {
        assertNull(SteamSession.StoredCreds.parse(""));
        assertNull(SteamSession.StoredCreds.parse("   \t\n"));
    }

    @Test
    public void testParse_MissingAccount() {
        String blob = "refreshToken=some_token\n" +
                      "guardData=some_guard_data\n";
        assertNull(SteamSession.StoredCreds.parse(blob));
    }

    @Test
    public void testParse_EmptyAccount() {
        String blob = "account=\n" +
                      "refreshToken=some_token\n" +
                      "guardData=some_guard_data\n";
        assertNull(SteamSession.StoredCreds.parse(blob));
    }

    @Test
    public void testParse_MissingRefreshToken() {
        String blob = "account=some_account\n" +
                      "guardData=some_guard_data\n";
        assertNull(SteamSession.StoredCreds.parse(blob));
    }

    @Test
    public void testParse_EmptyRefreshToken() {
        String blob = "account=some_account\n" +
                      "refreshToken=\n" +
                      "guardData=some_guard_data\n";
        assertNull(SteamSession.StoredCreds.parse(blob));
    }

    @Test
    public void testParse_ValidWithoutGuardData() {
        String blob = "account=my_user\n" +
                      "refreshToken=my_token\n";
        SteamSession.StoredCreds creds = SteamSession.StoredCreds.parse(blob);
        assertNotNull(creds);
        assertEquals("my_user", creds.account);
        assertEquals("my_token", creds.refreshToken);
        assertNull(creds.guardData);
    }

    @Test
    public void testParse_ValidWithGuardData() {
        String blob = "account=my_user\n" +
                      "refreshToken=my_token\n" +
                      "guardData=my_guard_data\n";
        SteamSession.StoredCreds creds = SteamSession.StoredCreds.parse(blob);
        assertNotNull(creds);
        assertEquals("my_user", creds.account);
        assertEquals("my_token", creds.refreshToken);
        assertEquals("my_guard_data", creds.guardData);
    }
}
