package co.frenchpress;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SPI for collecting Steam credentials interactively.
 *
 * <p>frenchpress ships {@link SwingCredentialPrompt} as the built-in fallback
 * for desktop environments. A host application (e.g. KnightLauncher-Android)
 * can supply a native implementation without leaking platform types into this
 * module, by either:
 * <ul>
 *   <li>calling {@link #register} before {@link com.threerings.froth.SteamAPI#init()}
 *       runs, or</li>
 *   <li>setting system property {@code frenchpress.credentialPrompt} to an
 *       implementing class name with a public no-arg constructor, or</li>
 *   <li>registering one via {@link java.util.ServiceLoader}.</li>
 * </ul>
 */
public interface CredentialPrompt {

  /**
   * Prompt the user for their Steam username and password.
   *
   * @return credentials to use for Steam login, or {@code null}/credentials
   *         where {@link Credentials#isWebAccount()} is true if the user
   *         wants to use a Three Rings / Grey Havens web account instead
   */
  Credentials promptForLogin ();

  /**
   * Prompt for a Steam Guard authenticator (TOTP) code.
   *
   * <p>The user may also approve the sign-in from the Steam Mobile App
   * without entering a code. Returning {@code null} or an empty string
   * signals that preference and lets JavaSteam poll for app confirmation.
   *
   * @param prevWrong true if the previous code was rejected
   * @return the authenticator code, or {@code null}/empty to wait for
   *         Steam Mobile App approval instead
   */
  String promptForDeviceCode (boolean prevWrong);

  /**
   * Prompt for a Steam Guard email code.
   *
   * <p>The user may also approve the sign-in from the Steam Mobile App
   * without entering a code. Returning {@code null} or an empty string
   * signals that preference and lets JavaSteam poll for app confirmation.
   *
   * @param email     the address the code was sent to
   * @param prevWrong true if the previous code was rejected
   * @return the email code, or {@code null}/empty to wait for
   *         Steam Mobile App approval instead
   */
  String promptForEmailCode (String email, boolean prevWrong);

  /**
   * Registers an implementation, overriding auto-detection.  Call early —
   * before {@link com.threerings.froth.SteamAPI#init()} — so the prompt is
   * in place before any login attempt is made.
   *
   * <p>This is the recommended integration point for Android hosts, which
   * cannot use ServiceLoader or system properties easily. It is also the only
   * one that is not reachable by game code after the fact: once the prompt has
   * been resolved, further calls are refused, so nothing loaded later can
   * substitute itself for the dialog that collects the password.
   */
  static void register (CredentialPrompt p) {
    Holder.register(p);
  }

  /**
   * Resolves the active prompt in priority order:
   * <ol>
   *   <li>Instance registered via {@link #register}</li>
   *   <li>Class named by system property {@code frenchpress.credentialPrompt}</li>
   *   <li>{@link java.util.ServiceLoader} discovery</li>
   *   <li>{@link SwingCredentialPrompt} if AWT/Swing is available</li>
   *   <li>{@code null} (no interactive prompt; fall back to env vars or fail)</li>
   * </ol>
   *
   * <p>Resolved <b>once</b> per JVM and cached, for the reasons in
   * {@link CredentialStore#resolve}: this object is handed the user's password
   * and 2FA code, and frenchpress shares a JVM with game code and mods.
   */
  static CredentialPrompt resolve () {
    return Holder.resolve();
  }

  /** Holds the registered/resolved instance (avoids a public mutable field). */
  final class Holder {

    /** Snapshotted at class-load so a later {@code setProperty} cannot redirect us. */
    private static final String OVERRIDE =
      System.getProperty("frenchpress.credentialPrompt", "");

    private static volatile CredentialPrompt registered;
    private static volatile CredentialPrompt resolved;
    private static volatile boolean locked;

    private Holder () {}

    static synchronized void register (CredentialPrompt p) {
      if (locked) {
        Logger.getLogger(CredentialPrompt.class.getName()).warning(
          "[frenchpress] credential prompt already in use; ignoring late register()");
        return;
      }
      registered = p;
    }

    static CredentialPrompt resolve () {
      CredentialPrompt r = resolved;
      if (r != null) return r;
      synchronized (Holder.class) {
        if (resolved == null) {
          CredentialPrompt created = create();
          // Nothing to lock in when there is no prompt at all (headless, no host
          // implementation) -- a host registering later should still be honoured.
          if (created == null) return null;
          locked = true;
          resolved = created;
        }
        return resolved;
      }
    }

    private static CredentialPrompt create () {
      if (registered != null) return registered;

      if (!OVERRIDE.isEmpty()) {
        try {
          return (CredentialPrompt) Class.forName(OVERRIDE)
            .getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
          Logger.getLogger(CredentialPrompt.class.getName()).log(Level.WARNING,
            "[frenchpress] credentialPrompt '" + OVERRIDE + "' failed to load; falling back", t);
        }
      }

      try {
        // Our own loader, not the thread-context one — see CredentialStore.
        var it = java.util.ServiceLoader
          .load(CredentialPrompt.class, CredentialPrompt.class.getClassLoader())
          .iterator();
        if (it.hasNext()) return it.next();
      } catch (Throwable t) {
        Logger.getLogger(CredentialPrompt.class.getName()).log(Level.SEVERE,
          "[frenchpress] ServiceLoader for CredentialPrompt failed", t);
      }

      return SwingCredentialPrompt.createIfSupported();
    }
  }
}
