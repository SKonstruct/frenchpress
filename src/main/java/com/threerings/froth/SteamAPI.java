package com.threerings.froth;

import co.frenchpress.SteamSession;
import java.util.logging.Logger;

/**
 * frenchpress shim replacement for com.threerings.froth.SteamAPI.
 *
 * Lifecycle methods report success once a SteamSession has been installed
 * by the launcher (via JavaSteam login). All native FFM machinery from
 * froth-foamy is bypassed; libsteam_api.so is never loaded.
 *
 * Must be on the classpath BEFORE projectx-pcode.jar so that this class
 * resolves instead of the one bundled inside the SK jar.
 */
public class SteamAPI
{
  private static final Logger log = Logger.getLogger(SteamAPI.class.getName());

  public static boolean init () {
    if (_initialized) return true;
    log.info("[frenchpress] SteamAPI.init() entered");
    _initialized = SteamSession.get() != null;
    log.info("[frenchpress] SteamAPI.init() -> " + _initialized);
    if (_initialized) {
      // SK registers SteamServerCallback listeners during its boot and waits
      // for steamServersConnected() to proceed past its "connecting to Steam"
      // gate. Fire it on the next runCallbacks() tick.
      _fireConnectedOnNextTick = true;
    }
    return _initialized;
  }

  public static boolean hasLibrary () { return SteamSession.get() != null; }
  public static boolean isInitialized () { return _initialized; }
  public static boolean isSteamRunning () { return _initialized; }

  public static void shutdown () {
    if (!_initialized) return;
    SteamSession s = SteamSession.get();
    if (s != null) s.disconnect();
    _initialized = false;
  }

  public static void runCallbacks () {
    if (!_initialized) return;
    if (_fireConnectedOnNextTick) {
      _fireConnectedOnNextTick = false;
    }
  }

  private static volatile boolean _initialized;
  private static volatile boolean _fireConnectedOnNextTick;
}
