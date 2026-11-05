package util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;
import model.Lietotajs;
import model.Loma;

/** Droša paroļu jaucēšana un pārbaude, izmantojot BCrypt. */
public final class PasswordUtil {
    private PasswordUtil() { }

    public static String hash(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Parole nedrīkst būt tukša.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Parole ir pārāk gara (BCrypt atbalsta līdz 72 UTF-8 baitiem).");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    /** Nepareizi formatēta vai neesoša jaucējvērtība tiek uzskatīta par neatbilstošu paroli. */
    public static boolean verify(String password, String hash) {
        if (password == null || hash == null || password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
    }

    /** Pārbauda, vai virkne atbilst BCrypt jaucējvērtības formātam. */
    public static boolean isBcryptHash(String hash) {
        return hash != null && hash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    }

    // ============================================================
    // Koplietojamās drošības pārbaudes (iepriekš dublētas 5 DAO)
    // ============================================================

    public static void requireSignedIn(Lietotajs actor) {
        if (actor == null) throw new SecurityException("Vispirms piesakieties sistēmā.");
    }

    public static void requireStaff(Lietotajs actor) {
        requireSignedIn(actor);
        if (!isStaff(actor)) throw new SecurityException(
                "Šī darbība ir pieejama tikai bibliotekāram vai administratoram.");
    }

    public static void requireAdmin(Lietotajs actor) {
        requireSignedIn(actor);
        if (actor.getLoma() != Loma.ADMIN) throw new SecurityException(
                "Šo darbību drīkst veikt tikai administrators.");
    }

    public static void requireReader(Lietotajs actor) {
        requireSignedIn(actor);
        if (actor.getLoma() != Loma.READER) throw new SecurityException(
                "Šī darbība ir pieejama tikai lasītājam.");
    }

    public static void requireNotificationAccess(Lietotajs actor) {
        requireSignedIn(actor);
        if (actor.getLoma() != Loma.READER && !isStaff(actor)) {
            throw new SecurityException(
                    "Paziņojumi ir pieejami tikai lasītājam, bibliotekāram vai administratoram.");
        }
    }

    public static boolean isStaff(Lietotajs actor) {
        return actor != null && (actor.getLoma() == Loma.ADMIN || actor.getLoma() == Loma.LIBRARIAN);
    }

    public static boolean isAdmin(Lietotajs actor) {
        return actor != null && actor.getLoma() == Loma.ADMIN;
    }

    public static boolean isReader(Lietotajs actor) {
        return actor != null && actor.getLoma() == Loma.READER;
    }

    public static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String clean(String value) {
        return blank(value) ? null : value.trim();
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }

    public static void requireText(String value, String message) {
        if (blank(value)) throw new IllegalArgumentException(message);
    }

    public static void requireMaxLength(String value, int max, String fieldName) {
        if (value != null && value.trim().length() > max) {
            throw new IllegalArgumentException(fieldName + " nedrīkst pārsniegt " + max + " rakstzīmes.");
        }
    }

    // ============================================================
    // Paziņojumu atslēgu formāts (koplietots ar LietotajsDAO un GUI)
    // ============================================================

    public static final String NOTIFICATION_KEY_SEPARATOR = ":";

    public static String buildNotificationKey(String type, int id) {
        return type + NOTIFICATION_KEY_SEPARATOR + id;
    }

    // ============================================================
    // Vienkārša JSON lauku izvilkšana (Google Books API atbildei)
    // ============================================================

    public static String jsonValue(String json, String key) {
        Pattern scalar = Pattern.compile(
                "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
                Pattern.DOTALL);
        Matcher scalarMatcher = scalar.matcher(json);
        if (scalarMatcher.find()) return unescapeJson(scalarMatcher.group(1));

        Pattern array = Pattern.compile(
                "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
                Pattern.DOTALL);
        Matcher arrayMatcher = array.matcher(json);
        return arrayMatcher.find() ? unescapeJson(arrayMatcher.group(1)) : null;
    }

    public static String unescapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) { result.append(ch); continue; }
            char next = value.charAt(++i);
            switch (next) {
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case '"': result.append('"'); break;
                case '\\': result.append('\\'); break;
                case '/': result.append('/'); break;
                case 'u':
                    if (i + 4 < value.length()) {
                        try { result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16)); i += 4; }
                        catch (NumberFormatException ex) { result.append('u'); }
                    } else result.append('u');
                    break;
                default: result.append(next);
            }
        }
        return result.toString().trim();
    }
}
