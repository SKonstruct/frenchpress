package co.frenchpress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

    /**
     * The point of the shim: JavaSteam encrypts the Steam password with the
     * BouncyCastle spelling of PKCS#1 v1.5, and it has to come out the other side
     * decryptable by a plain JDK RSA cipher. "The class loads" does not prove that.
     */
    @Test
    public void testShimActuallyEncryptsWithPkcs1() throws Exception {
        FrenchpressJceProvider.ensureRegistered();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        byte[] plain = "hunter2".getBytes(StandardCharsets.UTF_8);

        Cipher shim = Cipher.getInstance("RSA/None/PKCS1Padding", FrenchpressJceProvider.NAME);
        shim.init(Cipher.ENCRYPT_MODE, pair.getPublic());
        byte[] encrypted = shim.doFinal(plain);

        Cipher jdk = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        jdk.init(Cipher.DECRYPT_MODE, pair.getPrivate());
        assertArrayEquals(plain, jdk.doFinal(encrypted));
    }

    @Test
    public void testProviderDoesNotClaimRsaForOtherCallers() throws Exception {
        FrenchpressJceProvider.ensureRegistered();

        // Only the exact three-part name is registered, so unrelated code asking for
        // RSA in this JVM must still land on the platform provider.
        assertNotEquals(FrenchpressJceProvider.NAME,
                Cipher.getInstance("RSA/ECB/PKCS1Padding").getProvider().getName());
        assertNotEquals(FrenchpressJceProvider.NAME,
                Cipher.getInstance("RSA").getProvider().getName());
    }
}
