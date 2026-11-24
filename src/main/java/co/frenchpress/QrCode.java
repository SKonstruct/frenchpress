package co.frenchpress;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/** Turns a sign-in challenge URL into the module grid {@link CredentialPrompt#showSignIn} hands to hosts. */
final class QrCode {

  private QrCode () {}

  /**
   * @return one row per line, {@code '1'} for a dark module and {@code '0'} for a light one,
   *         with no quiet zone
   * @throws IllegalArgumentException if {@code text} cannot be encoded
   */
  static String encode (String text) {
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.MARGIN, 0);
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    BitMatrix matrix;
    try {
      // 0x0 asks for the smallest grid, one pixel per module.
      matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
    } catch (WriterException e) {
      throw new IllegalArgumentException("cannot encode QR code", e);
    }
    StringBuilder sb = new StringBuilder((matrix.getWidth() + 1) * matrix.getHeight());
    for (int y = 0; y < matrix.getHeight(); y++) {
      if (y > 0) sb.append('\n');
      for (int x = 0; x < matrix.getWidth(); x++) sb.append(matrix.get(x, y) ? '1' : '0');
    }
    return sb.toString();
  }
}
