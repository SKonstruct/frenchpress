package co.frenchpress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.dragonbra.javasteam.enums.EResult;
import org.junit.jupiter.api.Test;

class SteamSessionTest {
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
}
