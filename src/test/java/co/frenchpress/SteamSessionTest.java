package co.frenchpress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.dragonbra.javasteam.enums.EResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SteamSessionTest {
  private static long inSeconds (long s) {
    return System.nanoTime() + TimeUnit.SECONDS.toNanos(s);
  }

  @Test
  void onlySteamRejectingTheTokenCountsAsRejection () {
    assertTrue(SteamSession.isDefinitiveRejection(EResult.InvalidPassword));
    assertTrue(SteamSession.isDefinitiveRejection(EResult.AccessDenied));
    assertTrue(SteamSession.isDefinitiveRejection(EResult.Expired));
    assertTrue(SteamSession.isDefinitiveRejection(EResult.Revoked));
    assertTrue(SteamSession.isDefinitiveRejection(EResult.AccountDisabled));
  }

  @Test
  void aBlinkingNetworkNeverDeletesALogin () {
    assertFalse(SteamSession.isDefinitiveRejection(null));
    assertFalse(SteamSession.isDefinitiveRejection(EResult.NoConnection));
    assertFalse(SteamSession.isDefinitiveRejection(EResult.Timeout));
    assertFalse(SteamSession.isDefinitiveRejection(EResult.ServiceUnavailable));
    assertFalse(SteamSession.isDefinitiveRejection(EResult.TryAnotherCM));
  }

  @Test
  void aTokenWrittenByAnOlderVersionStillParses () {
    var creds = SteamSession.StoredCreds.parse(
      "account=someone\nrefreshToken=tok\nguardData=old\n");
    assertEquals("someone", creds.account);
    assertEquals("tok", creds.refreshToken);
    assertFalse(creds.serialize().contains("guardData"));
  }

  @Test
  void aBlobWithoutATokenIsNotACredential () {
    assertNull(SteamSession.StoredCreds.parse("account=someone\n"));
    assertNull(SteamSession.StoredCreds.parse(""));
    assertNull(SteamSession.StoredCreds.parse(null));
  }

  @Test
  void waitingReturnsTheResultOnceItIsThere () throws Exception {
    var done = CompletableFuture.completedFuture("approved");
    assertEquals("approved",
      SteamSession.awaitCancellable(done, () -> false, inSeconds(5)));
  }

  @Test
  void aCancelStopsTheWaitAndCancelsThePoll () {
    var pending = new CompletableFuture<String>();
    var aborted = assertThrows(SteamSession.SignInAborted.class,
      () -> SteamSession.awaitCancellable(pending, () -> true, inSeconds(5)));
    assertTrue(aborted.cancelled);
    assertTrue(pending.isCancelled());
  }

  @Test
  void aCancelDuringTheWaitIsNoticed () {
    var pending = new CompletableFuture<String>();
    var cancel = new AtomicBoolean();
    Thread.ofVirtual().start(() -> {
      try { Thread.sleep(150); } catch (InterruptedException ignored) {}
      cancel.set(true);
    });
    var aborted = assertThrows(SteamSession.SignInAborted.class,
      () -> SteamSession.awaitCancellable(pending, cancel::get, inSeconds(5)));
    assertTrue(aborted.cancelled);
  }

  @Test
  void theDeadlineEndsTheWait () {
    var pending = new CompletableFuture<String>();
    var aborted = assertThrows(SteamSession.SignInAborted.class,
      () -> SteamSession.awaitCancellable(pending, () -> false,
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)));
    assertFalse(aborted.cancelled);
    assertTrue(pending.isCancelled());
  }

  @Test
  void aFailedPollSurfacesAsItself () {
    var failed = new CompletableFuture<String>();
    failed.completeExceptionally(new IllegalStateException("connection lost"));
    var thrown = assertThrows(ExecutionException.class,
      () -> SteamSession.awaitCancellable(failed, () -> false, inSeconds(5)));
    assertEquals("connection lost", thrown.getCause().getMessage());
  }
}
