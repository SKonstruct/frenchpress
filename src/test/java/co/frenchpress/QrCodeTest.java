package co.frenchpress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QrCodeTest {
  private static final String URL = "https://s.team/q/1/1234567890123456789";

  @Test
  void theGridIsSquareAndOnlyZerosAndOnes () {
    String[] rows = QrCode.encode(URL).split("\n");
    assertTrue(rows.length >= 21, "a QR code is at least 21 modules wide");
    for (String row : rows) {
      assertEquals(rows.length, row.length());
      assertTrue(row.matches("[01]+"), row);
    }
  }

  @Test
  void theGridHasNoQuietZoneAndCarriesTheFinderPatterns () {
    String[] rows = QrCode.encode(URL).split("\n");
    String seven = "1111111";
    assertTrue(rows[0].startsWith(seven), "top-left finder");
    assertTrue(rows[0].endsWith(seven), "top-right finder");
    assertTrue(rows[rows.length - 1].startsWith(seven), "bottom-left finder");
  }

  @Test
  void theSameUrlGivesTheSameGrid () {
    assertEquals(QrCode.encode(URL), QrCode.encode(URL));
  }
}
