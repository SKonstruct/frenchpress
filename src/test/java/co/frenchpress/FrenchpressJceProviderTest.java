package co.frenchpress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.security.Provider;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

public class FrenchpressJceProviderTest {

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        Security.removeProvider(FrenchpressJceProvider.NAME);
    }

    @Test
    public void testEnsureRegisteredAddsProvider() {
        assertNull(Security.getProvider(FrenchpressJceProvider.NAME), "Provider should not be registered initially");

        FrenchpressJceProvider.ensureRegistered();

        Provider provider = Security.getProvider(FrenchpressJceProvider.NAME);
        assertNotNull(provider, "Provider should be registered after ensureRegistered()");
        assertTrue(provider instanceof FrenchpressJceProvider, "Provider should be an instance of FrenchpressJceProvider");
        assertEquals(FrenchpressJceProvider.NAME, provider.getName());
    }

    @Test
    public void testEnsureRegisteredIsIdempotent() {
        FrenchpressJceProvider.ensureRegistered();
        Provider firstRegistration = Security.getProvider(FrenchpressJceProvider.NAME);

        FrenchpressJceProvider.ensureRegistered();
        Provider secondRegistration = Security.getProvider(FrenchpressJceProvider.NAME);

        assertSame(firstRegistration, secondRegistration, "Provider instance should be the same across multiple registrations");
    }

    @Test
    public void testCipherIsAvailable() throws Exception {
        FrenchpressJceProvider.ensureRegistered();

        Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding", FrenchpressJceProvider.NAME);
        assertNotNull(cipher, "Cipher should be successfully instantiated from the provider");
    }
}
