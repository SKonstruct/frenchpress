package co.frenchpress;

import java.security.Provider;
import java.security.Security;

/**
 * Minimal JCE provider so JavaSteam's
 * {@code Cipher.getInstance("RSA/None/PKCS1Padding", CryptoHelper.SEC_PROV)} works
 * on a stock JDK with no BouncyCastle.
 *
 * <p>For RSA there is no chaining, so the BouncyCastle "None" mode and the JDK
 * "ECB" mode denote the same single-block operation; PKCS#1 v1.5 padding is
 * identical in both. This provider registers the BC-spelled transformation and
 * routes it to the JDK's "RSA/ECB/PKCS1Padding" (see {@link RsaNoneCipherSpi}).
 *
 * <p>Point {@link in.dragonbra.javasteam.util.crypto.CryptoHelper#SEC_PROV} at
 * {@link #NAME} and call {@link #ensureRegistered()} before any Steam auth.
 */
public final class FrenchpressJceProvider extends Provider {

    /** Provider name; must match CryptoHelper.SEC_PROV. */
    public static final String NAME = "FrenchpressJCE";

    public FrenchpressJceProvider() {
        super(NAME, "1.0", "frenchpress RSA/None -> JDK RSA/ECB shim (no BouncyCastle)");
        put("Cipher.RSA/None/PKCS1Padding", RsaNoneCipherSpi.class.getName());
    }

    /** Idempotently registers the provider with the platform. */
    public static synchronized void ensureRegistered() {
        if (Security.getProvider(NAME) == null) {
            Security.addProvider(new FrenchpressJceProvider());
        }
    }
}
