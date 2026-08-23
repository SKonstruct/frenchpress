package co.frenchpress;

import in.dragonbra.javasteam.util.crypto.CryptoHelper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These cover the hand-written replacements for javasteam's crypto (the shade
 * strips the upstream classes, so these are what actually run). Steam's own
 * protocol weaknesses are deliberately not asserted against — see the javadoc on
 * {@link CryptoHelper#symmetricDecrypt(byte[], byte[])}.
 */
public class CryptoHelperTest {

    private static byte[] key() {
        return CryptoHelper.generateRandomBlock(32);
    }

    @Test
    public void symmetricRoundTrips() throws Exception {
        byte[] key = key();
        byte[] plain = "the schedule is aggressive but achievable".getBytes(StandardCharsets.UTF_8);

        byte[] cipherText = CryptoHelper.symmetricEncrypt(plain, key);
        assertFalse(Arrays.equals(plain, Arrays.copyOf(cipherText, plain.length)));
        assertArrayEquals(plain, CryptoHelper.symmetricDecrypt(cipherText, key));
    }

    @Test
    public void symmetricEncryptIsRandomised() throws Exception {
        byte[] key = key();
        byte[] plain = "same message".getBytes(StandardCharsets.UTF_8);

        assertFalse(Arrays.equals(CryptoHelper.symmetricEncrypt(plain, key),
                CryptoHelper.symmetricEncrypt(plain, key)),
                "a fresh IV per message means two encryptions must differ");
    }

    @Test
    public void symmetricDecryptRejectsTheWrongKey() throws Exception {
        byte[] cipherText = CryptoHelper.symmetricEncrypt("secret".getBytes(StandardCharsets.UTF_8), key());
        assertThrows(Exception.class, () -> CryptoHelper.symmetricDecrypt(cipherText, key()));
    }

    @Test
    public void bothDirectionsRejectAShortKey() {
        byte[] shortKey = new byte[16];
        byte[] input = new byte[32];
        // Checked CryptoException, not an unchecked throw that would escape
        // NetFilterEncryption's handling and kill the network thread.
        assertThrows(in.dragonbra.javasteam.util.crypto.CryptoException.class,
                () -> CryptoHelper.symmetricEncrypt(input, shortKey));
        assertThrows(in.dragonbra.javasteam.util.crypto.CryptoException.class,
                () -> CryptoHelper.symmetricDecrypt(input, shortKey));
    }

    @Test
    public void symmetricDecryptRejectsInputShorterThanTheIv() {
        assertThrows(in.dragonbra.javasteam.util.crypto.CryptoException.class,
                () -> CryptoHelper.symmetricDecrypt(new byte[15], key()));
    }

    @Test
    public void hmacIvRoundTrips() throws Exception {
        byte[] key = key();
        byte[] hmacSecret = CryptoHelper.generateRandomBlock(16);
        byte[] plain = "packet body".getBytes(StandardCharsets.UTF_8);

        byte[] cipherText = CryptoHelper.symmetricEncryptWithHMACIV(plain, key, hmacSecret);
        assertArrayEquals(plain, CryptoHelper.symmetricDecryptHMACIV(cipherText, key, hmacSecret));
    }

    @Test
    public void hmacIvRejectsATamperedTag() throws Exception {
        byte[] key = key();
        byte[] hmacSecret = CryptoHelper.generateRandomBlock(16);
        byte[] cipherText = CryptoHelper.symmetricEncryptWithHMACIV(
                "packet body".getBytes(StandardCharsets.UTF_8), key, hmacSecret);

        assertThrows(Exception.class,
                () -> CryptoHelper.symmetricDecryptHMACIV(cipherText, key,
                        CryptoHelper.generateRandomBlock(16)),
                "a wrong HMAC secret must not decrypt");
    }

    @Test
    public void crcHashMatchesTheUpstreamLittleEndianEncoding() {
        // CRC32("123456789") == 0xCBF43926, written low byte first.
        byte[] hash = CryptoHelper.crcHash("123456789".getBytes(StandardCharsets.UTF_8));
        assertEquals("2639f4cb", HexFormat.of().formatHex(hash));
    }

    @Test
    public void generateRandomBlockReturnsTheRequestedLength() {
        assertEquals(32, CryptoHelper.generateRandomBlock(32).length);
        assertFalse(Arrays.equals(CryptoHelper.generateRandomBlock(32),
                CryptoHelper.generateRandomBlock(32)));
    }
}
