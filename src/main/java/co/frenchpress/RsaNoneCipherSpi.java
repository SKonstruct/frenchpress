package co.frenchpress;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/**
 * CipherSpi for "RSA/None/PKCS1Padding" that simply forwards to a JDK-provided
 * RSA cipher. Registered by {@link FrenchpressJceProvider}; instantiated by the
 * JCE via its public no-arg constructor.
 *
 * <p>RSA has no chaining, so "None" (BouncyCastle) and "ECB" (JDK) are the same
 * operation — this class exists only to bridge the two spellings.
 */
public final class RsaNoneCipherSpi extends CipherSpi {

    private static final String DELEGATE = "RSA/ECB/PKCS1Padding";

    private final Cipher delegate;

    public RsaNoneCipherSpi() throws GeneralSecurityException {
        // Pinned to the JDK provider. An unqualified lookup would hand the actual RSA
        // to whichever provider ranks first in the JVM -- including one installed by
        // other code in this process -- even though our caller asked for us by name.
        // (There is no recursion risk either way: we never register RSA/ECB.)
        Cipher jdk;
        try {
            jdk = Cipher.getInstance(DELEGATE, "SunJCE");
        } catch (NoSuchProviderException e) {
            jdk = Cipher.getInstance(DELEGATE);
        }
        this.delegate = jdk;
    }

    // Unreachable while the provider registers only the full three-part name, but
    // they must refuse rather than accept-and-ignore: silently answering to
    // "RSA/GCM/OAEPPadding" with PKCS#1 v1.5 is exactly the kind of downgrade a
    // later broadening of that registration would introduce.

    @Override protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (!"None".equalsIgnoreCase(mode) && !"ECB".equalsIgnoreCase(mode)) {
            throw new NoSuchAlgorithmException("unsupported mode: " + mode);
        }
    }

    @Override protected void engineSetPadding(String padding) throws NoSuchPaddingException {
        if (!"PKCS1Padding".equalsIgnoreCase(padding)) {
            throw new NoSuchPaddingException("unsupported padding: " + padding);
        }
    }

    @Override protected int engineGetBlockSize() {
        return delegate.getBlockSize();
    }

    @Override protected int engineGetOutputSize(int inputLen) {
        return delegate.getOutputSize(inputLen);
    }

    @Override protected byte[] engineGetIV() {
        return delegate.getIV();
    }

    @Override protected AlgorithmParameters engineGetParameters() {
        return delegate.getParameters();
    }

    @Override protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        delegate.init(opmode, key, random);
    }

    @Override protected void engineInit(int opmode, Key key, AlgorithmParameterSpec spec,
            SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        delegate.init(opmode, key, spec, random);
    }

    @Override protected void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        delegate.init(opmode, key, params, random);
    }

    @Override protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        return delegate.update(input, inputOffset, inputLen);
    }

    @Override protected int engineUpdate(byte[] input, int inputOffset, int inputLen,
            byte[] output, int outputOffset) throws ShortBufferException {
        return delegate.update(input, inputOffset, inputLen, output, outputOffset);
    }

    @Override protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        return delegate.doFinal(input, inputOffset, inputLen);
    }

    @Override protected int engineDoFinal(byte[] input, int inputOffset, int inputLen,
            byte[] output, int outputOffset)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        return delegate.doFinal(input, inputOffset, inputLen, output, outputOffset);
    }
}
