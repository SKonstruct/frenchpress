package co.frenchpress;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Default {@link CredentialStore}: a single file in a private directory,
 * locked to owner-only permissions where the filesystem supports POSIX modes.
 *
 * <p>Location, in order of precedence:
 * <ol>
 *   <li>{@code FRENCHPRESS_CRED_FILE} env var (absolute path), else</li>
 *   <li>{@code $XDG_DATA_HOME/frenchpress/steam.creds}, else</li>
 *   <li>{@code ~/.local/share/frenchpress/steam.creds}</li>
 * </ol>
 *
 * <p><b>What the encryption here is and is not.</b> The blob is sealed with
 * AES-256-GCM, but the key lives in {@link Preferences} under the same user
 * account — and on the mobile hosts, inside the same app container as the
 * file. Anyone who can read the credential file can read the key, so this
 * defends against a casual copy of one file, not against an attacker with
 * access to the container, a device backup, or a full-directory export. What
 * it does buy unconditionally is <i>integrity</i>: a modified or planted file
 * fails the GCM tag and is discarded rather than logged in with. A host that
 * needs real confidentiality should inject a Keychain/Keystore-backed
 * implementation through the {@link CredentialStore} SPI.
 */
public final class FileCredentialStore implements CredentialStore {

  private static final Logger log = Logger.getLogger(FileCredentialStore.class.getName());

  /** Current key: AES-256. */
  private static final String PREF_KEY = "frenchpress_aes_key_v2";
  /** Pre-2026 key: AES-128. Read once during migration, then removed. */
  private static final String LEGACY_PREF_KEY = "frenchpress_aes_key";

  private static final int IV_BYTES = 12;
  private static final int TAG_BYTES = 16;

  private static final Set<PosixFilePermission> FILE_PERMS =
    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<PosixFilePermission> DIR_PERMS =
    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE);

  private final Path file;

  public FileCredentialStore () {
    this(defaultPath());
  }

  /** Test seam: use an explicit location instead of the env-derived one. */
  FileCredentialStore (Path file) {
    this.file = file;
  }

  @Override public String load () {
    try {
      if (!Files.isReadable(file)) return null;
      String blob = Files.readString(file).strip();
      if (blob.isEmpty()) return null;

      String plain = decryptWith(currentKey(), blob);
      if (plain != null) return plain;

      // One-time migration from the AES-128 key, or from the pre-encryption
      // plaintext format. Anything else failed authentication: discard it
      // rather than hand an unverified blob back as credentials.
      String migrated = readLegacy(blob);
      if (migrated == null) {
        log.warning("[frenchpress] stored credentials failed authentication; discarding");
        clear();
        return null;
      }
      save(migrated);
      prefs().remove(LEGACY_PREF_KEY);
      log.info("[frenchpress] migrated stored credentials to the current key");
      return migrated;
    } catch (Throwable t) {
      log.log(Level.WARNING, "[frenchpress] credential load failed", t);
      return null;
    }
  }

  @Override public void save (String data) {
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      Path dir = file.getParent();
      if (dir != null) createDirectory(dir);

      // Write to a sibling temp file created owner-only *before* it holds any
      // ciphertext, then rename over the target. Truncating the real file in
      // place would destroy a good token if encryption or the write failed.
      Files.deleteIfExists(tmp);
      createOwnerOnly(tmp);
      Files.writeString(tmp, encrypt(data));
      move(tmp, file);
    } catch (Throwable t) {
      log.log(Level.WARNING, "[frenchpress] credential save failed", t);
      try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
    }
  }

  @Override public void clear () {
    try {
      Files.deleteIfExists(file);
      Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
      // Drop the key too: leaving it behind serves nothing once there is no
      // ciphertext, and the next login generates a fresh one.
      Preferences prefs = prefs();
      prefs.remove(PREF_KEY);
      prefs.remove(LEGACY_PREF_KEY);
      cachedKey = null;
    } catch (Throwable t) {
      log.log(Level.WARNING, "[frenchpress] credential clear failed", t);
    }
  }

  // ---------------------------------------------------------------------------
  // Crypto
  // ---------------------------------------------------------------------------

  private static volatile SecretKey cachedKey = null;
  private static final SecureRandom secureRandom = new SecureRandom();
  private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
  private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
  private static final ThreadLocal<Cipher> cipherCache = ThreadLocal.withInitial(() -> {
    try {
      return Cipher.getInstance("AES/GCM/NoPadding");
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize Cipher", e);
    }
  });

  private static Preferences prefs () {
    return Preferences.userNodeForPackage(FileCredentialStore.class);
  }

  private static SecretKey currentKey () throws Exception {
    SecretKey key = cachedKey;
    if (key != null) return key;

    synchronized (FileCredentialStore.class) {
      if (cachedKey != null) return cachedKey;

      Preferences prefs = prefs();
      String encoded = prefs.get(PREF_KEY, null);
      if (encoded != null) {
        byte[] decoded = BASE64_DECODER.decode(encoded);
        cachedKey = new SecretKeySpec(decoded, "AES");
        return cachedKey;
      }
      KeyGenerator keyGen = KeyGenerator.getInstance("AES");
      keyGen.init(256);
      SecretKey generated = keyGen.generateKey();
      prefs.put(PREF_KEY, BASE64_ENCODER.encodeToString(generated.getEncoded()));
      // put() is only in memory until the prefs timer (30 s) or a shutdown hook flushes it, and
      // the Android host kills the process outright. A session closed soon after approving
      // then kept steam.creds but lost the key that decrypts it.
      try {
        prefs.flush();
      } catch (java.util.prefs.BackingStoreException e) {
        log.log(Level.WARNING, "could not flush the credential key", e);
      }
      cachedKey = generated;
      return generated;
    }
  }

  /** @return the plaintext if {@code blob} authenticates under an older format, else null. */
  private static String readLegacy (String blob) {
    String encoded = prefs().get(LEGACY_PREF_KEY, null);
    if (encoded != null) {
      byte[] decoded = BASE64_DECODER.decode(encoded);
      String plain = decryptWith(new SecretKeySpec(decoded, "AES"), blob);
      if (plain != null) return plain;
    }
    // Installs older than the encrypted format stored the Properties text
    // verbatim. Accept only something shaped like that, never arbitrary bytes.
    return blob.contains("refreshToken=") ? blob : null;
  }

  private static String decryptWith (SecretKey key, String blob) {
    try {
      byte[] message = BASE64_DECODER.decode(blob);
      if (message.length < IV_BYTES + TAG_BYTES) return null;
      Cipher cipher = cipherCache.get();
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, message, 0, IV_BYTES));
      return new String(cipher.doFinal(message, IV_BYTES, message.length - IV_BYTES),
        StandardCharsets.UTF_8);
    } catch (Exception e) {
      // Wrong key, tampered ciphertext, or not base64 at all — all "not ours".
      return null;
    }
  }

  private static String encrypt (String data) throws Exception {
    SecretKey key = currentKey();
    Cipher cipher = cipherCache.get();
    byte[] iv = new byte[IV_BYTES];
    secureRandom.nextBytes(iv);
    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
    byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

    ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
    buffer.put(iv);
    buffer.put(encrypted);
    return BASE64_ENCODER.encodeToString(buffer.array());
  }

  // ---------------------------------------------------------------------------
  // Filesystem
  // ---------------------------------------------------------------------------

  private static void createDirectory (Path dir) throws IOException {
    try {
      Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(DIR_PERMS));
    } catch (UnsupportedOperationException e) {
      Files.createDirectories(dir);
      trySetPerms(dir, DIR_PERMS);
    }
  }

  /** Creates {@code p} empty and owner-only, so it never exists world-readable. */
  private static void createOwnerOnly (Path p) throws IOException {
    try {
      Files.createFile(p, PosixFilePermissions.asFileAttribute(FILE_PERMS));
    } catch (UnsupportedOperationException e) {
      Files.createFile(p);
      trySetPerms(p, FILE_PERMS);
    }
  }

  private static void move (Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
      trySetPerms(to, FILE_PERMS);
    }
  }

  private static void trySetPerms (Path p, Set<PosixFilePermission> perms) {
    try { Files.setPosixFilePermissions(p, perms); }
    catch (UnsupportedOperationException | IOException ignored) {
      java.io.File f = p.toFile();
      f.setReadable(false, false);
      f.setWritable(false, false);
      f.setExecutable(false, false);
      if (perms.contains(PosixFilePermission.OWNER_READ)) f.setReadable(true, true);
      if (perms.contains(PosixFilePermission.OWNER_WRITE)) f.setWritable(true, true);
      if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) f.setExecutable(true, true);
    }
  }

  private static Path defaultPath () {
    String override = System.getenv("FRENCHPRESS_CRED_FILE");
    if (override != null && !override.isEmpty()) return Path.of(override);

    String xdg = System.getenv("XDG_DATA_HOME");
    Path base = (xdg != null && !xdg.isEmpty())
      ? Path.of(xdg)
      : Path.of(System.getProperty("user.home", "."), ".local", "share");
    return base.resolve("frenchpress").resolve("steam.creds");
  }
}
