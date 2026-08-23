package co.frenchpress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The store's job is to make a tampered or planted credential file unusable
 * while keeping a genuine one readable across launches, so that is what these
 * assert. The key lives in {@link Preferences}, which is per-user and shared
 * with a real installation, so it is saved and restored around every test.
 */
public class FileCredentialStoreTest {

    private static final String PREF_KEY = "frenchpress_aes_key_v2";
    private static final String LEGACY_PREF_KEY = "frenchpress_aes_key";

    private Path tempDir;
    private Path credFile;
    private String savedKey;
    private String savedLegacyKey;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("frenchpress_test");
        credFile = tempDir.resolve("steam.creds");
        Preferences prefs = Preferences.userNodeForPackage(FileCredentialStore.class);
        savedKey = prefs.get(PREF_KEY, null);
        savedLegacyKey = prefs.get(LEGACY_PREF_KEY, null);
    }

    @AfterEach
    public void tearDown() throws Exception {
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
        Preferences prefs = Preferences.userNodeForPackage(FileCredentialStore.class);
        if (savedKey != null) prefs.put(PREF_KEY, savedKey); else prefs.remove(PREF_KEY);
        if (savedLegacyKey != null) prefs.put(LEGACY_PREF_KEY, savedLegacyKey);
        else prefs.remove(LEGACY_PREF_KEY);
    }

    private FileCredentialStore store() {
        return new FileCredentialStore(credFile);
    }

    @Test
    public void roundTripsThroughTheFile() {
        String blob = "account=knight\nrefreshToken=abc123\n";
        store().save(blob);
        assertEquals(blob, store().load());
    }

    @Test
    public void missingFileLoadsAsNull() {
        assertNull(store().load());
    }

    @Test
    public void storedBlobIsNotReadableAsPlaintext() throws Exception {
        store().save("account=knight\nrefreshToken=supersecret\n");
        String onDisk = Files.readString(credFile);
        assertFalse(onDisk.contains("supersecret"), "token must not be on disk in the clear");
    }

    @Test
    public void tamperedCiphertextIsRejectedAndDiscarded() throws Exception {
        store().save("account=knight\nrefreshToken=abc123\n");
        String onDisk = Files.readString(credFile).strip();
        // Flip one base64 character in the ciphertext body, past the IV.
        int at = onDisk.length() - 5;
        char replacement = onDisk.charAt(at) == 'A' ? 'B' : 'A';
        Files.writeString(credFile, onDisk.substring(0, at) + replacement + onDisk.substring(at + 1));

        assertNull(store().load(), "a failed GCM tag must not yield credentials");
        assertFalse(Files.exists(credFile), "a rejected file should be cleared");
    }

    @Test
    public void truncatedFileIsRejected() throws Exception {
        Files.writeString(credFile, "AAAA");
        assertNull(store().load());
    }

    @Test
    public void plantedPlaintextThatIsNotCredentialsIsRejected() throws Exception {
        Files.writeString(credFile, "not credentials at all");
        assertNull(store().load());
    }

    @Test
    public void legacyPlaintextIsAcceptedOnceThenReencrypted() throws Exception {
        String legacy = "account=knight\nrefreshToken=abc123\n";
        Files.writeString(credFile, legacy);

        // load() strips the file, so the trailing newline does not survive; Properties
        // parsing does not care, and nothing else reads this blob.
        assertEquals(legacy.strip(), store().load(), "a pre-encryption file must still log the user in");

        String onDisk = Files.readString(credFile);
        assertFalse(onDisk.contains("refreshToken="), "the file should have been re-encrypted");
        assertEquals(legacy.strip(), store().load(), "and still readable afterwards");
    }

    @Test
    public void clearRemovesTheFileAndTheKey() {
        store().save("account=knight\nrefreshToken=abc123\n");
        store().clear();

        assertFalse(Files.exists(credFile));
        assertNull(Preferences.userNodeForPackage(FileCredentialStore.class).get(PREF_KEY, null));
    }

    @Test
    public void savedFileIsOwnerOnlyWherePosixApplies() throws Exception {
        store().save("account=knight\nrefreshToken=abc123\n");
        if (!Files.getFileStore(credFile).supportsFileAttributeView("posix")) return;

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(credFile);
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms);
    }

    @Test
    public void aFailedSaveLeavesNoTempFileBehind() throws Exception {
        store().save("account=knight\nrefreshToken=abc123\n");
        try (var paths = Files.list(tempDir)) {
            assertTrue(paths.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }
}
