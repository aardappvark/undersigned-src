import java.math.BigInteger;
import java.nio.file.*;
import java.util.Arrays;

/**
 * Prints the quick-review relayer's key in the form Phantom and Solflare import ("Import private key": base58 of the
 * 64-byte secret = 32-byte seed + 32-byte public key), plus its address. Run it yourself, in your own Terminal:
 *   "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" api/tools/ExportRelayerKey.java
 * Anyone holding the printed key can spend what the relayer wallet holds. Never paste it into a chat or a website.
 */
public class ExportRelayerKey {
    static String b58(byte[] b) {
        String A = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        BigInteger n = new BigInteger(1, b); StringBuilder s = new StringBuilder();
        while (n.signum() > 0) { BigInteger[] qr = n.divideAndRemainder(BigInteger.valueOf(58)); s.insert(0, A.charAt(qr[1].intValue())); n = qr[0]; }
        for (byte x : b) { if (x == 0) s.insert(0, '1'); else break; }
        return s.toString();
    }
    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "UndersignedAPI");
        byte[] pkcs8 = Files.readAllBytes(dir.resolve("relayer.key")), x509 = Files.readAllBytes(dir.resolve("relayer.pub"));
        byte[] seed = Arrays.copyOfRange(pkcs8, pkcs8.length - 32, pkcs8.length), pub = Arrays.copyOfRange(x509, x509.length - 32, x509.length);
        byte[] secret = new byte[64]; System.arraycopy(seed, 0, secret, 0, 32); System.arraycopy(pub, 0, secret, 32, 32);
        System.out.println("Address:     " + b58(pub));
        System.out.println("Private key: " + b58(secret) + "   (Phantom/Solflare: Import private key)");
    }
}
