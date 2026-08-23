package co.frenchpress;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pluggable persistence for the Steam refresh token (and Steam Guard machine
 * token) so relaunches skip password + 2FA.
 *
 * <p>The blob handed to {@link #save} / returned by {@link #load} is opaque to
 * callers; treat it as a single secret string. It is roughly as sensitive as a
 * password at rest, so implementations are expected to protect it.
 *
 * <p>frenchpress ships {@link FileCredentialStore} (a private-dir,
 * perms-locked file) as the default. A host application can inject a
 * stronger, OS-backed implementation without leaking platform types
 * into this JDK module, by either:
 * <ul>
 *   <li>setting system property {@code frenchpress.credentialStore} to an
 *       implementing class name with a public no-arg constructor, or</li>
 *   <li>registering one via {@link java.util.ServiceLoader}.</li>
 * </ul>
 */
public interface CredentialStore {

  /** @return the previously saved blob, or {@code null} if none/unreadable. */
  String load ();

  /** Persist {@code data}, replacing any prior value. */
  void save (String data);

  /** Remove any stored blob (e.g. after the token is rejected as expired). */
  void clear ();

  /**
   * Resolves the active store: explicit system property first, then
   * {@link java.util.ServiceLoader}, then the default file-backed store.
   *
   * <p>Resolved <b>once</b> per JVM and cached. frenchpress runs inside Spiral
   * Knights' own JVM, alongside game code and mods, and this method decides who
   * receives the Steam refresh token — so it deliberately does not re-read its
   * inputs on every call. The property is snapshotted when this class loads and
   * the {@link java.util.ServiceLoader} lookup is pinned to frenchpress's own
   * classloader, so a {@code META-INF/services} entry dropped into a game jar
   * cannot claim it. A host that wants certainty should not rely on the
   * property at all: see {@link CredentialPrompt#register}.
   */
  static CredentialStore resolve () {
    return Holder.resolve();
  }

  /** Holds the one resolved store (avoids a public mutable field). */
  final class Holder {

    /** Snapshotted at class-load so a later {@code setProperty} cannot redirect us. */
    private static final String OVERRIDE =
      System.getProperty("frenchpress.credentialStore", "");

    private static volatile CredentialStore instance;

    private Holder () {}

    static CredentialStore resolve () {
      CredentialStore resolved = instance;
      if (resolved != null) return resolved;
      synchronized (Holder.class) {
        if (instance == null) instance = create();
        return instance;
      }
    }

    private static CredentialStore create () {
      if (!OVERRIDE.isEmpty()) {
        try {
          return (CredentialStore) Class.forName(OVERRIDE)
            .getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
          Logger.getLogger(CredentialStore.class.getName()).log(Level.WARNING,
            "[frenchpress] credentialStore '" + OVERRIDE + "' failed to load; falling back to default", t);
        }
      }
      try {
        // Our own loader, not the thread-context one: the TCCL here is Spiral
        // Knights' classloader, so the no-arg form would let any jar on the
        // game's classpath register itself as the credential store.
        var it = java.util.ServiceLoader
          .load(CredentialStore.class, CredentialStore.class.getClassLoader())
          .iterator();
        if (it.hasNext()) return it.next();
      } catch (Throwable t) {
        Logger.getLogger(CredentialStore.class.getName()).log(Level.SEVERE,
          "[frenchpress] ServiceLoader for CredentialStore failed", t);
      }
      return new FileCredentialStore();
    }
  }
}
