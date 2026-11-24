# frenchpress - Spiral Knights Steam login shim and UI

A simple, lightweight, and functional Java shim library that shadows Spiral Knights' steam_api library calls from froth-foamy and passes them to JavaSteam to facilitate Steam-linked account logins without requiring an entire native Steam process running in the background.

## Requirements

- Maven
- JDK 25

## Building

```bash
mvn package
```

## Usage

Place frenchpress.jar first in the classpath when invoking the JVM to run Spiral Knights along with the flag `-Dcom.threerings.froth.disable_steam_api=true` to prevent it from trying to load the actual steam_api calls, otherwise it will fail without an actual Steam client running.

For example:
```bash
java --class-path frenchpress.jar:all:other:required:Spiral:Knights:JAR:files \
    -Dcom.threerings.froth.disable_steam_api=true \
    -Dorg.lwjgl.util.NoChecks=true \
    -Dsun.java2d.d3d=false \
    -Dappdir=/your/Spiral/Knights/directory \
    -Dresource_dir=/your/Spiral/Knights/directory/rsrc \
    -Dcrucible.dir=/your/Spiral/Knights/directory/crucible \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED \
    com.threerings.projectx.client.ProjectXApp client
```

## Signing in

frenchpress never asks for a Steam username or password. On first launch (or after a
stored token expires or is revoked) it signs in through a QR code approved in the Steam
mobile app. It tries, in order:

1. Stored token : Written automatically after a successful sign-in; silent.
2. QR code : Shown through a `CredentialPrompt`. The user scans it with the Steam mobile
   app, or opens the challenge link on a device that has the app. Steam rotates the
   challenge while it waits and frenchpress hands each new one to the prompt. A connection
   that drops before the user approves is restarted with a fresh code. The whole wait is
   capped at 5 minutes.

A `CredentialPrompt` has three methods: `showSignIn(url, qrModules)`, `hideSignIn()` and
`isSignInCancelled()`. `qrModules` is the code as text, one row per line, `1` for a dark
module and `0` for a light one, with no quiet zone, so hosts draw it with their own
margin. Desktop JVMs get a Swing window automatically. Android and iOS hosts call
`CredentialPrompt.register(impl)` early (or name a class with
`-Dfrenchpress.credentialPrompt`) with a native implementation.

A host can run the sign-in before the game starts, then disconnect and let the game's own copy log on
from the stored token. If that early sign-in was cancelled or failed, the host sets
`-Dfrenchpress.signInDeclined=true` so the game's copy does not show the QR code a second time.

Cancelling, or a sign-in that runs out of time, skips Steam entirely and lets Spiral
Knights proceed with a Three Rings / Grey Havens web account.

The QR code is drawn by zxing-core, shaded into the jar under `co.frenchpress.shaded.zxing`.

## Security

frenchpress stores a long-lived refresh token, and it
runs **inside Spiral Knights' own JVM**, next to game code and any mods on the classpath.
That shapes what it does:

- **The prompt and the store are resolved once**, at first use, from frenchpress's own
  classloader — not the thread-context one, which belongs to the game. A
  `META-INF/services` entry in a game jar cannot claim either SPI, and
  `CredentialPrompt.register` is refused once a prompt is in use. Hosts should call
  `register` early rather than relying on `-Dfrenchpress.credentialPrompt`, which is only
  snapshotted at class-load and so is as trustworthy as whatever ran before that.
- **`FileCredentialStore` seals the token with AES-256-GCM, and the key sits in
  `java.util.prefs` under the same user.** Treat that as integrity, not secrecy: a
  tampered or planted file fails the tag and is discarded, but anyone who can read the
  credential file can also read the key. If the host can do better — Keychain, Android
  Keystore — inject a `CredentialStore` through the SPI; that is what it is for.
- **Steam's transport crypto is weak in ways this library cannot fix.** The legacy
  envelope authenticates nothing in its non-HMAC mode, the HMAC mode decrypts before it
  verifies, and its IV carries 24 bits of entropy. `CryptoHelper` matches upstream
  JavaSteam byte for byte here on purpose; see the javadoc on `symmetricDecrypt`.
- **No Steam password is ever handled.** Sign-in is approved in the Steam mobile app, so the
  password stays there.

Run the tests (`mvn test`) after touching anything above; the credential store and both
crypto shims have round-trip and tamper-rejection coverage.

## References

Three Rings Design / Grey Havens
- [Spiral Knights](https://www.spiralknights.com/) : A really cool MMO you should play
- [froth](https://github.com/threerings/froth) : The OG implementation of Spiral Knights' Steam API Java calls
- [froth-foamy](https://github.com/greyhavens/froth-foamy) : The new, cooler, and current implementation of Spiral Knights' Steam API Java calls

Longi94 and Contributors
- [JavaSteam](https://github.com/Longi94/JavaSteam) : Java port of SteamKit2
