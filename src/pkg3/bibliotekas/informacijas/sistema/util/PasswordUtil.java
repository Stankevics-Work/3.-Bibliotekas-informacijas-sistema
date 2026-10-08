package pkg3.bibliotekas.informacijas.sistema.util;
import org.mindrot.jbcrypt.BCrypt;
/** Paroles jaucēšana un pārbaude ar dokumentā norādīto BCrypt */
public final class PasswordUtil {
    private PasswordUtil() { }
    public static String hash(String password) { return BCrypt.hashpw(password, BCrypt.gensalt(12)); }
    public static boolean verify(String password, String hash) { return BCrypt.checkpw(password, hash); }
}
