package co.frenchpress;

/**
 * Headless {@link CredentialPrompt} for Android / no-UI hosts.
 *
 * <p>Supplies the Steam username/password from the
 * {@code FRENCHPRESS_STEAM_USER} / {@code FRENCHPRESS_STEAM_PASS} environment
 * variables and never shows any dialog. For Steam Guard it returns an empty
 * code, which signals JavaSteam to poll for Steam Mobile App approval instead
 * of waiting on an (impossible) interactive code entry.
 *
 * <p>Intended to be selected explicitly via
 * {@code -Dfrenchpress.credentialPrompt=co.frenchpress.HeadlessEnvPrompt} so it
 * wins over {@link SwingCredentialPrompt}, which would otherwise be chosen on a
 * non-headless cacio AWT and render into an invisible buffer.
 *
 * <p>An empty/absent username yields a web-account {@link Credentials} so the
 * game proceeds with a Three Rings / Grey Havens account.
 */
public final class HeadlessEnvPrompt implements CredentialPrompt {

  public HeadlessEnvPrompt () {}

  @Override public Credentials promptForLogin () {
    String user = System.getenv("FRENCHPRESS_STEAM_USER");
    String pass = System.getenv("FRENCHPRESS_STEAM_PASS");
    if (user == null || user.isEmpty()) {
      return new Credentials("", "");
    }
    return new Credentials(user, pass == null ? "" : pass);
  }

  @Override public String promptForDeviceCode (boolean prevWrong) {
    return "";
  }

  @Override public String promptForEmailCode (String email, boolean prevWrong) {
    return "";
  }
}
