package co.frenchpress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SwingCredentialPromptTest {
  private static final int WHITE = 0xFFFFFF;
  private static final int BLACK = 0x000000;

  @Test
  void theCodeIsDrawnBlackOnWhiteWithAQuietZone () {
    BufferedImage img = SwingCredentialPrompt.render("10\n01");
    // 2 modules + 4 quiet modules each side, 6 px per module
    assertEquals(60, img.getWidth());
    assertEquals(60, img.getHeight());
    assertEquals(WHITE, img.getRGB(0, 0) & 0xFFFFFF);
    assertEquals(BLACK, img.getRGB(4 * 6, 4 * 6) & 0xFFFFFF);
    assertEquals(WHITE, img.getRGB(5 * 6, 4 * 6) & 0xFFFFFF);
    assertEquals(BLACK, img.getRGB(5 * 6, 5 * 6) & 0xFFFFFF);
  }
}
