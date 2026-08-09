package co.frenchpress;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CredentialsTest {

    @Test
    public void testIsWebAccountWithNullUsername() {
        Credentials creds = new Credentials(null, "password123");
        assertTrue(creds.isWebAccount(), "Expected isWebAccount() to return true when username is null");
    }

    @Test
    public void testIsWebAccountWithEmptyUsername() {
        Credentials creds = new Credentials("", "password123");
        assertTrue(creds.isWebAccount(), "Expected isWebAccount() to return true when username is empty");
    }

    @Test
    public void testIsWebAccountWithNonEmptyUsername() {
        Credentials creds = new Credentials("steamuser", "password123");
        assertFalse(creds.isWebAccount(), "Expected isWebAccount() to return false when username is non-empty");
    }

    @Test
    public void testRecordAccessors() {
        Credentials creds = new Credentials("testuser", "testpass");
        assertEquals("testuser", creds.username(), "Expected username accessor to return correct value");
        assertEquals("testpass", creds.password(), "Expected password accessor to return correct value");
    }
}
