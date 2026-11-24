package co.frenchpress;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.authentication.AuthPollResult;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;
import in.dragonbra.javasteam.steam.authentication.QrAuthSession;
import in.dragonbra.javasteam.steam.handlers.steamauthticket.SteamAuthTicket;
import in.dragonbra.javasteam.steam.handlers.steamauthticket.TicketInfo;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaSteam-backed Steam session, owned by the shim and lazily logged in
 * the first time {@link com.threerings.froth.SteamAPI#init()} runs.
 *
 * Sign-in priority:
 *  1. Stored refresh token (CredentialStore) — silent, no UI shown.
 *  2. A QR code shown through {@link CredentialPrompt}, approved in the Steam
 *     mobile app: scan it, or open the challenge link on a device that has the
 *     app. Cancelling skips Steam and the game proceeds with a web account.
 *
 * No Steam password ever reaches this library.
 *
 * Android hosts should call {@link CredentialPrompt#register} early with
 * a native implementation.  Desktop environments get
 * {@link SwingCredentialPrompt} automatically.
 */
public final class SteamSession {

  private static final Logger log = Logger.getLogger(SteamSession.class.getName());

  /** Spiral Knights' Steam AppID. */
  public static final int SK_APPID = 99900;

  /**
   * How long a stored-token logon and the begin-session call may take. Steam drops the
   * CM WebSocket on its own at ~64–69s of an unanswered device-confirmation poll
   * (observed; likely a server heartbeat/idle timeout), which surfaces as a
   * CancellationException out of the poll. Whether a QR poll is cut the same way is not
   * known, which is why a QR sign-in restarts itself when its connection dies.
   */
  private static final long AUTH_WAIT_SECONDS = 60;

  /**
   * How long to wait for the user to approve a QR sign-in, across every restart of it.
   * Finding a second device and scanning takes longer than a push approval did.
   */
  private static final long QR_WAIT_SECONDS = 300;

  /**
   * Extra time for everything before the approval wait: server list, TLS and the begin-session
   * call. A slow connect must not eat the time the user needs to fetch their phone.
   */
  private static final long CONNECT_GRACE_SECONDS = 45;

  /** How often a pending sign-in looks at the cancel flag and the deadline. */
  private static final long QR_POLL_SLICE_MS = 500;

  /** Cap on QR attempts; each lasts about a minute before Steam closes the connection. */
  private static final int QR_MAX_ATTEMPTS = 5;

  /** Pause before restarting, so a persistent failure does not hammer Steam. */
  private static final long QR_RESTART_DELAY_MS = 2000;

  /**
   * @return the active session, logging in on first call. Returns null if login
   *         fails or the user chose a web account.
   *
   * <p>Only the caller that wins {@code LOGIN_ATTEMPTED} performs the login;
   * everyone else waits on {@code LOGIN_DONE}. Deliberately not
   * {@code synchronized}: the login blocks for up to {@link #QR_WAIT_SECONDS},
   * and the game calls this from {@code SteamUser.isLoggedOn()} /
   * {@code getSteamID()}, so holding the class monitor across the wait froze
   * the whole client for as long whenever Steam was slow or unreachable.
   */
  public static SteamSession get () {
    if (LOGIN_DONE.getCount() == 0) return INSTANCE;

    // Re-entrant for the thread doing the login: the old `synchronized get()` let a
    // nested call straight through (monitors are re-entrant), and a prompt or a
    // callback calling back into froth on that thread must not wait on itself.
    if (Thread.currentThread() == LOGIN_THREAD) return INSTANCE;

    boolean mine;
    synchronized (SteamSession.class) {
      mine = !LOGIN_ATTEMPTED;
      LOGIN_ATTEMPTED = true;
      if (mine) LOGIN_THREAD = Thread.currentThread();
    }
    if (mine) {
      try {
        INSTANCE = login();
      } finally {
        LOGIN_THREAD = null;
        LOGIN_DONE.countDown();
      }
      return INSTANCE;
    }

    try {
      // Bounded by the login's own timeout; the slack covers teardown.
      long waitStart = System.currentTimeMillis();
      log.info("[frenchpress] waiting for the in-flight Steam login");
      if (!LOGIN_DONE.await(QR_WAIT_SECONDS + CONNECT_GRACE_SECONDS + 30, TimeUnit.SECONDS)) {
        log.warning("[frenchpress] timed out waiting for the in-flight Steam login");
      } else {
        log.info("[frenchpress] in-flight login finished after "
          + (System.currentTimeMillis() - waitStart) + " ms");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    return INSTANCE;
  }

  /**
   * Set by the host's bootstrap when it already ran the QR sign-in before the game started and the
   * user cancelled or it failed, so the game does not ask a second time. Read once, at class load.
   */
  private static final boolean SIGN_IN_DECLINED = Boolean.getBoolean("frenchpress.signInDeclined");

  /** The EResult of the last failed logon, or null if the attempt never got an answer from Steam. */
  private static volatile EResult lastLogonFailure;

  /** Only an answer from Steam that the token itself is no good justifies deleting it. */
  static boolean isDefinitiveRejection (EResult result) {
    return result == EResult.InvalidPassword || result == EResult.AccessDenied
      || result == EResult.Expired || result == EResult.Revoked
      || result == EResult.AccountDisabled;
  }

  private static SteamSession login () {
    // JavaSteam's crypto spells some transformations the BC way, via CryptoHelper.SEC_PROV.
    // Register our no-BC shim provider (SEC_PROV) before any Steam traffic so they resolve.
    FrenchpressJceProvider.ensureRegistered();

    CredentialStore store = CredentialStore.resolve();

    // 1. Steady state: a previously stored refresh token skips the QR code.
    StoredCreds saved = StoredCreds.parse(store.load());
    if (saved != null) {
      log.info("[frenchpress] trying stored refresh token (account len="
        + saved.account.length() + ")");
      lastLogonFailure = null;
      SteamSession s;
      setKeepAlive(true);
      try {
        s = attempt(store, saved, null, 0).session;
      } finally {
        setKeepAlive(false);
      }
      if (s != null) return s;
      if (!isDefinitiveRejection(lastLogonFailure)) {
        // Offline, a Steam outage or a slow connect says nothing about the token. Clearing it
        // here deleted a good login (and its key) whenever the network blinked at launch.
        log.warning("[frenchpress] stored token not used this time (" + lastLogonFailure
          + "); keeping it");
        emitLaunchStatus("Steam sign-in did not complete; continuing without Steam");
        return null;
      }
      log.info("[frenchpress] stored token rejected; clearing and falling back to a QR sign-in");
      store.clear();
    }

    // 2. QR sign-in, approved in the Steam mobile app.
    if (SIGN_IN_DECLINED) {
      log.info("[frenchpress] sign-in was already declined or failed before launch; "
        + "continuing without Steam");
      return null;
    }
    CredentialPrompt prompt = CredentialPrompt.resolve();
    if (prompt == null) {
      log.warning("[frenchpress] no stored token and no sign-in UI available; "
        + "Steam auth will not proceed");
      return null;
    }
    return signInWithQr(store, prompt);
  }

  /**
   * Runs the QR sign-in until it succeeds, the user cancels or the budget runs out. A session that
   * loses its connection before the user approved is started again with a fresh code, which the
   * prompt shows in place of the old one.
   */
  private static SteamSession signInWithQr (CredentialStore store, CredentialPrompt prompt) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(QR_WAIT_SECONDS);
    // One keep-alive bracket for the whole sign-in: on Android the host process is killed when the
    // user tabs out to approve it, which aborts the poll and forces a fresh sign-in next launch.
    // It must be ON before the user can be prompted, and OFF again on EVERY exit, but not between
    // restarts, or a backgrounded launcher could be killed in that gap.
    setKeepAlive(true);
    try {
      for (int n = 1; n <= QR_MAX_ATTEMPTS; n++) {
        Attempt a = attempt(store, null, prompt, deadline);
        if (a.session != null) return a.session;
        if (!a.restart || prompt.isSignInCancelled() || System.nanoTime() >= deadline) break;
        log.info("[frenchpress] sign-in connection dropped before approval; starting a new QR code ("
          + n + "/" + QR_MAX_ATTEMPTS + ")");
        Thread.sleep(QR_RESTART_DELAY_MS);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } finally {
      setKeepAlive(false);
      try {
        prompt.hideSignIn();
      } catch (Throwable t) {
        log.log(Level.WARNING, "[frenchpress] hiding the sign-in UI failed", t);
      }
    }
    emitLaunchStatus("Steam sign-in did not complete; continuing without Steam");
    return null;
  }

  /** The outcome of one {@link #attempt}. */
  private static final class Attempt {
    final SteamSession session; // non-null on success
    final boolean restart;      // a QR sign-in lost its connection before the user approved

    Attempt (SteamSession session, boolean restart) {
      this.session = session;
      this.restart = restart;
    }
  }

  /** The user cancelled a pending sign-in, or its time ran out. */
  static final class SignInAborted extends Exception {
    final boolean cancelled;

    SignInAborted (boolean cancelled) {
      super(cancelled ? "cancelled" : "timed out");
      this.cancelled = cancelled;
    }
  }

  /**
   * Waits for {@code future} in short slices so a cancel or the deadline is noticed within
   * {@link #QR_POLL_SLICE_MS}, cancelling the future when either happens.
   */
  static <T> T awaitCancellable (CompletableFuture<T> future, BooleanSupplier cancelled,
      long deadlineNanos) throws Exception {
    while (true) {
      if (cancelled.getAsBoolean()) {
        future.cancel(true);
        throw new SignInAborted(true);
      }
      long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
      if (remainingMs <= 0) {
        future.cancel(true);
        throw new SignInAborted(false);
      }
      try {
        return future.get(Math.min(QR_POLL_SLICE_MS, remainingMs), TimeUnit.MILLISECONDS);
      } catch (TimeoutException slice) {
        // not done yet; look at the flag and the clock again
      }
    }
  }

  /**
   * One connect+logon lifecycle. {@code saved} non-null takes the token path; otherwise a QR
   * sign-in is shown through {@code prompt} until {@code deadlineNanos}. On a successful QR
   * sign-in the resulting refresh token is persisted to {@code store} so subsequent launches
   * take the token path.
   */
  private static Attempt attempt (CredentialStore store, StoredCreds saved,
      CredentialPrompt prompt, long deadlineNanos) {
    SteamClient client = new SteamClient();
    CallbackManager manager = new CallbackManager(client);
    SteamUser steamUser = client.getHandler(SteamUser.class);
    SteamAuthTicket authTicket = client.getHandler(SteamAuthTicket.class);
    if (steamUser == null || authTicket == null) {
      log.severe("[frenchpress] JavaSteam handlers unavailable; aborting login");
      try { client.disconnect(); } catch (Exception ignored) {}
      return new Attempt(null, false);
    }

    SteamSession session = new SteamSession(client, manager, steamUser, authTicket);
    CountDownLatch done = new CountDownLatch(1);
    final boolean[] ok = { false };
    final boolean[] restart = { false };
    final StoredCreds[] toPersist = { null };

    manager.subscribe(ConnectedCallback.class, cb -> {
      try {
        if (saved != null) {
          log.info("[frenchpress] ConnectedCallback; logging on with stored token");
          LogOnDetails logon = new LogOnDetails();
          logon.setUsername(saved.account);
          logon.setAccessToken(saved.refreshToken);
          steamUser.logOn(logon);
          return;
        }

        log.info("[frenchpress] ConnectedCallback fired; starting QR sign-in");
        AuthSessionDetails details = new AuthSessionDetails();
        details.deviceFriendlyName = "Spiral Knights";
        details.persistentSession = true;

        // Bounded: these run on the callback pump thread, and an unbounded get() pins that
        // thread (and its socket) forever if Steam never answers.
        QrAuthSession qr = client.getAuthentication().beginAuthSessionViaQR(details)
          .get(AUTH_WAIT_SECONDS, TimeUnit.SECONDS);
        showChallenge(prompt, qr.getChallengeUrl());
        qr.setChallengeUrlChanged(changed -> showChallenge(prompt, changed.getChallengeUrl()));
        emitLaunchStatus("Waiting for Steam sign-in…");

        AuthPollResult poll = awaitCancellable(qr.pollingWaitForResult(),
          prompt::isSignInCancelled, deadlineNanos);

        toPersist[0] = new StoredCreds(poll.getAccountName(), poll.getRefreshToken());

        LogOnDetails logon = new LogOnDetails();
        logon.setUsername(poll.getAccountName());
        logon.setAccessToken(poll.getRefreshToken());
        steamUser.logOn(logon);
      } catch (SignInAborted e) {
        log.info("[frenchpress] QR sign-in " + e.getMessage());
        done.countDown();
      } catch (CancellationException e) {
        // Steam closes the connection after about a minute of an unanswered poll; not a
        // failure, just a code that needs replacing.
        log.info("[frenchpress] Steam closed the sign-in connection");
        restart[0] = saved == null;
        done.countDown();
      } catch (Throwable e) {
        log.log(Level.SEVERE, "[frenchpress] auth flow failed:", e);
        // A dead connection or an expired challenge is worth another code; the token path has
        // nothing to retry here.
        restart[0] = saved == null;
        done.countDown();
      }
    });

    manager.subscribe(LoggedOnCallback.class, cb -> {
      log.info("[frenchpress] LoggedOnCallback result=" + cb.getResult());
      if (cb.getResult() != EResult.OK) {
        lastLogonFailure = cb.getResult();
        log.severe("[frenchpress] logon failed: " + cb.getResult()
          + " / " + cb.getExtendedResult());
        done.countDown();
        return;
      }
      emitLaunchStatus("Signed in to Steam");
      session.steamId = cb.getClientSteamID() != null
        ? cb.getClientSteamID().convertToUInt64() : 0L;
      if (toPersist[0] != null) {
        store.save(toPersist[0].serialize());
        log.info("[frenchpress] persisted refresh token for future launches");
      }
      ok[0] = true;
      done.countDown();
    });

    manager.subscribe(DisconnectedCallback.class, cb -> {
      log.info("[frenchpress] DisconnectedCallback userInitiated="
        + cb.isUserInitiated());
      if (done.getCount() > 0) done.countDown();
    });

    ExecutorService pumpExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "frenchpress-steam-callbacks");
      t.setDaemon(true);
      return t;
    });
    session.pumpExecutor = pumpExecutor;
    pumpExecutor.submit(() -> {
      // The Future is discarded, so an escaping exception would vanish and leave
      // the caller waiting out the full timeout. Log it and release the latch.
      try {
        while (session.running) {
          manager.runWaitCallbacks(1000L);
        }
      } catch (Throwable t) {
        log.log(Level.SEVERE, "[frenchpress] callback pump died", t);
        done.countDown();
      }
    });

    log.info("[frenchpress] connecting to Steam...");
    // The overlay otherwise keeps getdown's last text for the whole login, which on a
    // token resume is the only thing the user ever sees.
    emitLaunchStatus("Connecting to Steam…");
    try {
      client.connect();
      long waitSeconds = saved != null
        ? AUTH_WAIT_SECONDS + CONNECT_GRACE_SECONDS
        : TimeUnit.NANOSECONDS.toSeconds(Math.max(0, deadlineNanos - System.nanoTime()))
          + CONNECT_GRACE_SECONDS;
      if (!done.await(waitSeconds, TimeUnit.SECONDS)) {
        log.warning("[frenchpress] login timed out after " + waitSeconds + "s");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    if (!ok[0]) {
      log.warning("[frenchpress] attempt() returning null (auth not OK)");
      session.disconnect();
      return new Attempt(null, restart[0]);
    }
    log.info("[frenchpress] attempt() returning session; steamID=" + session.steamId);
    return new Attempt(session, false);
  }

  /** Hands the challenge to the prompt. Runs on JavaSteam's threads, so it must not throw. */
  private static void showChallenge (CredentialPrompt prompt, String url) {
    try {
      prompt.showSignIn(url, QrCode.encode(url));
    } catch (Throwable t) {
      log.log(Level.SEVERE, "[frenchpress] could not show the sign-in QR code", t);
    }
  }

  /**
   * Toggles the Android sign-in keep-alive via SkBootstrap.nativeSteamKeepAlive,
   * reached by reflection because frenchpress runs in SK's classloader and must
   * target the system-loader SkBootstrap whose natives sklauncher.c registered —
   * the same hop {@link #emitLaunchStatus} uses. No-op (logged)
   * off Android or before native registration, so desktop logins are unaffected.
   */
  private static void setKeepAlive (boolean active) {
    try {
      Class<?> sysBoot = Class.forName(
          "com.skarm.launcher.bootstrap.SkBootstrap",
          false,
          ClassLoader.getSystemClassLoader());
      Method m = sysBoot.getDeclaredMethod("nativeSteamKeepAlive", boolean.class);
      try {
        m.setAccessible(true);
        m.invoke(null, active);
      } finally {
        m.setAccessible(false);
      }
    } catch (Throwable t) {
      log.log(Level.WARNING, "[frenchpress] keep-alive bridge unavailable", t);
    }
  }

  private static void emitLaunchStatus (String message) {
    try {
      Class<?> sysBoot = Class.forName(
          "com.skarm.launcher.bootstrap.SkBootstrap",
          false,
          ClassLoader.getSystemClassLoader());
      Method nativeLaunchStatus = sysBoot.getDeclaredMethod("nativeLaunchStatus", String.class);
      try {
        nativeLaunchStatus.setAccessible(true);
        nativeLaunchStatus.invoke(null, message);
      } finally {
        nativeLaunchStatus.setAccessible(false);
      }
    } catch (Throwable t) {
      log.log(Level.WARNING, "[frenchpress] launch-status bridge unavailable", t);
    }
  }

  // -------------------------------------------------------------------------
  // StoredCreds
  // -------------------------------------------------------------------------

  static final class StoredCreds {
    final String account;
    final String refreshToken;

    StoredCreds (String account, String refreshToken) {
      this.account = account;
      this.refreshToken = refreshToken;
    }

    String serialize () {
      Properties p = new Properties();
      if (account != null) p.setProperty("account", account);
      if (refreshToken != null) p.setProperty("refreshToken", refreshToken);
      StringWriter w = new StringWriter();
      try { p.store(w, "frenchpress steam credentials"); }
      catch (Exception e) { return ""; }
      return w.toString();
    }

    /** Reads the stored blob; a {@code guardData} entry written by older versions is ignored. */
    static StoredCreds parse (String blob) {
      if (blob == null || blob.isBlank()) return null;
      Properties p = new Properties();
      try { p.load(new StringReader(blob)); }
      catch (Exception e) { return null; }
      String acct = p.getProperty("account");
      String tok  = p.getProperty("refreshToken");
      if (acct == null || acct.isEmpty() || tok == null || tok.isEmpty()) return null;
      return new StoredCreds(acct, tok);
    }
  }

  // -------------------------------------------------------------------------
  // Session methods
  // -------------------------------------------------------------------------

  private SteamSession (SteamClient client, CallbackManager manager,
      SteamUser steamUser, SteamAuthTicket authTicket) {
    this.client = client;
    this.manager = manager;
    this.steamUser = steamUser;
    this.authTicket = authTicket;
  }

  public long steamID () { return steamId; }

  public String personaName () {
    return "Player";
  }

  /**
   * Writes a fresh auth ticket into the game-supplied buffer.
   *
   * @return the ticket handle, or 0 on failure — in which case {@code out} is
   *         left untouched.
   */
  public int getAuthSessionTicket (ByteBuffer out) {
    if (out == null) {
      log.severe("[frenchpress] getAuthSessionTicket called with a null buffer");
      return 0;
    }
    TicketInfo info = null;
    try {
      info = authTicket.getAuthSessionTicket(SK_APPID).get(30, TimeUnit.SECONDS);
      byte[] bytes = info.getTicket();
      if (bytes == null) {
        log.severe("[frenchpress] Steam returned an empty auth ticket");
        info.close();
        return 0;
      }
      // Capacity, not remaining(): the write below starts with clear(), so the
      // space available is the whole buffer regardless of where the caller left
      // its position. Checking remaining() first would reject a buffer that fits.
      if (bytes.length > out.capacity()) {
        log.severe("[frenchpress] auth ticket is " + bytes.length
          + " bytes but the buffer holds " + out.capacity());
        info.close();
        return 0;
      }
      int handle = NEXT_HANDLE.getAndIncrement();
      synchronized (tickets) { tickets.put(handle, info); }
      ByteBuffer dst = out.order(ByteOrder.LITTLE_ENDIAN);
      dst.clear();
      dst.put(bytes);
      dst.flip();
      // Logged on success too: without this, silence here is ambiguous between
      // "issued fine" and "never called", which cost a debugging round.
      log.info("[frenchpress] auth ticket issued [handle=" + handle
        + ", bytes=" + bytes.length + ", capacity=" + out.capacity() + "]");
      return handle;
    } catch (Exception e) {
      log.log(Level.SEVERE, "[frenchpress] getAuthSessionTicket failed", e);
      // Close it, or the ticket stays live Steam-side with nothing tracking it.
      if (info != null) {
        try { info.close(); } catch (Exception ignored) {}
      }
      return 0;
    }
  }

  public void cancelAuthTicket (int handle) {
    TicketInfo info;
    synchronized (tickets) { info = tickets.remove(handle); }
    if (info != null) info.close();
  }

  public void disconnect () {
    running = false;
    // Cancel anything the game never handed back, so the tickets do not stay
    // live Steam-side after we are gone.
    synchronized (tickets) {
      for (TicketInfo info : tickets.values()) {
        try { info.close(); } catch (Exception e) {
          log.log(Level.WARNING, "[frenchpress] failed to close auth ticket", e);
        }
      }
      tickets.clear();
    }
    if (pumpExecutor != null) {
      pumpExecutor.shutdownNow();
    }
    try {
      client.disconnect();
    } catch (Exception e) {
      log.log(Level.WARNING, "Error disconnecting Steam client", e);
    }
  }

  // -------------------------------------------------------------------------
  // Fields
  // -------------------------------------------------------------------------

  private final SteamClient client;
  private final CallbackManager manager;
  private final SteamUser steamUser;
  private final SteamAuthTicket authTicket;
  private final Map<Integer, TicketInfo> tickets = new HashMap<>();
  private volatile long steamId;
  private volatile boolean running = true;
  // Written by the login thread, read by whichever thread calls disconnect().
  private volatile ExecutorService pumpExecutor;

  private static final AtomicInteger NEXT_HANDLE = new AtomicInteger(1);
  private static volatile SteamSession INSTANCE;
  private static volatile boolean LOGIN_ATTEMPTED;
  private static final CountDownLatch LOGIN_DONE = new CountDownLatch(1);
  /** The thread performing login(), so nested calls from it do not wait on themselves. */
  private static volatile Thread LOGIN_THREAD;
}
