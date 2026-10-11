package pkg3.bibliotekas.informacijas.sistema.dao;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pkg3.bibliotekas.informacijas.sistema.db.DatabaseManager;
import pkg3.bibliotekas.informacijas.sistema.model.*;
import pkg3.bibliotekas.informacijas.sistema.util.PasswordUtil;
import static pkg3.bibliotekas.informacijas.sistema.util.PasswordUtil.*;

/** Lietotajs datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class LietotajsDAO {
    private final Connection connection;

    /** Ērtā konstrukcija lietotnes GUI slāņa izsaukumiem; katra darbība atver savu DB savienojumu. */
    public LietotajsDAO() { this.connection = null; }

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public LietotajsDAO(Connection connection) { this.connection = connection; }

    private Lietotajs map(ResultSet rs) throws SQLException {
        Lietotajs value = new Lietotajs();
        value.setLietotajaId(rs.getInt("lietotaja_id"));
        value.setVards(rs.getString("vards"));
        value.setUzvards(rs.getString("uzvards"));
        value.setLietotajvards(rs.getString("lietotajvards"));
        value.setParole(rs.getString("parole"));
        value.setEpasts(rs.getString("epasts"));
        value.setLoma(Loma.valueOf(rs.getString("loma")));
        return value;
    }

    private List<Lietotajs> query(String sql, Object... args) throws SQLException {
        List<Lietotajs> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }

    public Lietotajs findById(int id) throws SQLException {
        List<Lietotajs> result = query("SELECT * FROM lietotajs WHERE lietotaja_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Lietotajs> findAll() throws SQLException {
        return query("SELECT * FROM lietotajs ORDER BY lietotaja_id");
    }

    private void bind(PreparedStatement ps, Lietotajs value) throws SQLException {
        ps.setString(1, value.getVards());
        ps.setString(2, value.getUzvards());
        ps.setString(3, value.getLietotajvards());
        ps.setString(4, value.getParole());
        ps.setString(5, value.getEpasts());
        ps.setString(6, value.getLoma().name());
    }

    public int insert(Lietotajs value) throws SQLException {
        if (value.getLoma() == null) value.setLoma(Loma.READER);
        if (!isBcryptHash(value.getParole()))
            throw new SQLException("Parolei jābūt BCrypt jaucējvērtībai");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO lietotajs (vards, uzvards, lietotajvards, parole, epasts, loma) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, value); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                value.setLietotajaId(keys.getInt(1)); return value.getLietotajaId();
            }
        }
    }

    public boolean update(Lietotajs value) throws SQLException {
        if (value.getLoma() == null) value.setLoma(Loma.READER);
        if (!isBcryptHash(value.getParole()))
            throw new SQLException("Parolei jābūt BCrypt jaucējvērtībai");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE lietotajs SET vards = ?, uzvards = ?, lietotajvards = ?, parole = ?, epasts = ?, loma = ? WHERE lietotaja_id = ?")) {
            bind(ps, value); ps.setInt(7, value.getLietotajaId());
            return ps.executeUpdate() == 1;
        }
    }

    public Lietotajs findByUsername(String username) throws SQLException {
        List<Lietotajs> result = query("SELECT * FROM lietotajs WHERE lietotajvards = ?", username);
        return result.isEmpty() ? null : result.get(0);
    }

    public boolean existsByEmail(String email) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM lietotajs WHERE LOWER(epasts) = LOWER(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Pārbauda lietotājvārda unikalitāti; exceptId ļauj saglabāt paša rediģētā lietotāja vērtību. */
    public boolean existsByUsername(String username, int exceptId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM lietotajs WHERE LOWER(lietotajvards) = LOWER(?) AND lietotaja_id <> ?")) {
            ps.setString(1, username);
            ps.setInt(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** Pārbauda e-pasta unikalitāti, neieskaitot pašreiz rediģēto lietotāju. */
    public boolean existsByEmail(String email, int exceptId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM lietotajs WHERE LOWER(epasts) = LOWER(?) AND lietotaja_id <> ?")) {
            ps.setString(1, email);
            ps.setInt(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** Atgriež lomām izmantotās vērtības; GUI izvēlnē tās validē pret Loma enum. */
    public List<String> findDistinctRoleNames() throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT DISTINCT loma FROM lietotajs WHERE loma IS NOT NULL ORDER BY loma");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString("loma"));
        }
        return result;
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM lietotajs WHERE lietotaja_id = ?")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }

    private static final String LEGACY_HASH = "$2a$12$S/4iFvK26WWpHziepOKIFe94sUJ1SOmNnnegW0jz/MDXobmpJHmke";
    private static final String ADMIN_HASH = "$2a$12$MvTNcSrTUIANNWjhVRdkke7aL.6jpHRdzhTMG3CnQnRt/uq8wACkq";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    private <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            return work.run(c);
        }
    }

    private static void validatePassword(String password) {
        requireText(password, "Parole ir obligāta.");
        if (password.length() < 8) throw new IllegalArgumentException("Parolei jābūt vismaz 8 rakstzīmes garai.");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Parole ir pārāk gara (BCrypt atbalsta līdz 72 UTF-8 baitiem).");
        }
    }

    private static void ensureAnotherAdmin(Connection connection, int excludedUserId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM lietotajs WHERE loma = 'ADMIN' AND lietotaja_id <> ?")) {
            ps.setInt(1, excludedUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) < 1) {
                    throw new IllegalArgumentException("Sistēmā jāsaglabā vismaz viens administrators.");
                }
            }
        }
    }

    public void initializeDatabase() throws SQLException {
        withConnection(connection -> {
            DatabaseManager.initialize(connection);
            seedInitialData(connection);
            return null;
        });
    }

    private static void seedInitialData(Connection c) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Sākotnējo datu ievietošanai vajadzīgs autoCommit=true");

        LietotajsDAO users = new LietotajsDAO(c);
        ensureUser(c, users, "Anna", "Ozola", "admin", ADMIN_HASH, "admin@example.com", Loma.ADMIN);
        ensureUser(c, users, "Ilze", "Kalnina", "bibliotekars", LEGACY_HASH, "library@example.com", Loma.LIBRARIAN);
        ensureUser(c, users, "Janis", "Berzins", "lasitajs", LEGACY_HASH, "reader@example.com", Loma.READER);
        ensureUser(c, users, "Mara", "Liepa", "autors", LEGACY_HASH, "author@example.com", Loma.AUTHOR);

        GramataDAO books = new GramataDAO(c);
        EksemplarsDAO copies = new EksemplarsDAO(c);
        IzsniegumsDAO loans = new IzsniegumsDAO(c);
        RezervacijaDAO reservations = new RezervacijaDAO(c);

        int book1 = books.ensureInitialBook("Mazais celojums", "Stasts par celojumu.", 2024,
                "Stasti", null, "Mara Liepa", true);
        int book2 = books.ensureInitialBook("Java pamati", "Ievads programmesana.", 2025,
                "Macibu literatura", null, "Peteris Ozols", false);
        Lietotajs demoReader = users.findByUsername("lasitajs");
        int copy1 = copies.ensureInitialCopy(book1, "DTTT biblioteka", "pieejams");
        // Ja DB jau ir cits lasītāja konts un sākotnējais demo konts nav vajadzīgs,
        // neveido eksemplāru ar "izsniegts" statusu bez atbilstoša izsnieguma.
        int copy2 = copies.ensureInitialCopy(book2, "DTTT biblioteka",
                demoReader == null ? "pieejams" : "izsniegts");

        if (demoReader != null) {
            int readerId = demoReader.getLietotajaId();
            LocalDateTime demoIssued = LocalDateTime.now().minusDays(1).withNano(0);
            LocalDateTime demoDue = demoIssued.plusDays(14).withHour(23).withMinute(59).withSecond(59).withNano(0);
            loans.ensureInitialLoan(readerId, copy2, Timestamp.valueOf(demoIssued), Timestamp.valueOf(demoDue));
            reservations.ensureInitialReservation(readerId, book1,
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(10).withNano(0)), "akt\u012bva");
        }
    }

    private static void ensureUser(Connection c, LietotajsDAO users, String firstName, String lastName,
                                   String username, String passwordHash, String email, Loma role) throws SQLException {
        // Noklusējuma kontu pievieno tikai tad, ja attiecīgajai lomai nav neviena konta.
        // Tas novērš pārdēvētu kontu dublēšanu un nekad nepārraksta esošas paroles.
        if (users.findByUsername(username) != null) return;
        try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM lietotajs WHERE loma = ?")) {
            check.setString(1, role.name());
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO lietotajs (vards, uzvards, lietotajvards, parole, epasts, loma) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, username);
            ps.setString(4, passwordHash);
            ps.setString(5, email);
            ps.setString(6, role.name());
            ps.executeUpdate();
        }
    }

    public Lietotajs authenticate(String username, String password) throws SQLException {
        if (blank(username) || blank(password)) throw new IllegalArgumentException("Ievadiet lietotājvārdu un paroli.");
        return withConnection(connection -> {
            Lietotajs user = new LietotajsDAO(connection).findByUsername(username.trim());
            if (user == null || !PasswordUtil.verify(password, user.getParole())) {
                throw new SecurityException("Nepareizs lietotājvārds vai parole.");
            }
            user.setParole(null);
            return user;
        });
    }

    public Lietotajs registerUser(String firstName, String lastName, String username,
            String password, String confirmPassword, String email) throws SQLException {
        requireText(firstName, "Vārds ir obligāts.");
        requireText(lastName, "Uzvārds ir obligāts.");
        requireText(username, "Lietotājvārds ir obligāts.");
        requireText(password, "Parole ir obligāta.");
        requireText(confirmPassword, "Apstipriniet paroli.");
        requireText(email, "E-pasts ir obligāts.");
        requireMaxLength(firstName, 50, "Vārds");
        requireMaxLength(lastName, 50, "Uzvārds");
        requireMaxLength(username, 50, "Lietotājvārds");
        requireMaxLength(email, 100, "E-pasts");
        validatePassword(password);
        if (!password.equals(confirmPassword)) throw new IllegalArgumentException("Paroles nesakrīt.");
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) throw new IllegalArgumentException("E-pasta adrese nav derīga.");

        return withConnection(connection -> {
            LietotajsDAO dao = new LietotajsDAO(connection);
            if (dao.existsByUsername(username.trim(), -1)) throw new SQLException("Lietotājvārds jau ir aizņemts.");
            if (dao.existsByEmail(email.trim(), -1)) throw new SQLException("E-pasts jau ir aizņemts.");
            Lietotajs user = new Lietotajs();
            user.setVards(firstName.trim());
            user.setUzvards(lastName.trim());
            user.setLietotajvards(username.trim());
            user.setParole(PasswordUtil.hash(password));
            user.setEpasts(email.trim());
            user.setLoma(Loma.READER);
            dao.insert(user);
            return user;
        });
    }

    public List<Lietotajs> searchUsers(String query, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        String q = safe(query).trim().toLowerCase(Locale.ROOT);
        return withConnection(connection -> {
            if (q.isEmpty()) return new LietotajsDAO(connection).findAll();
            String pattern = "%" + q + "%";
            return new LietotajsDAO(connection).query(
                    "SELECT * FROM lietotajs WHERE LOWER(vards) LIKE ? OR LOWER(uzvards) LIKE ? "
                    + "OR LOWER(lietotajvards) LIKE ? OR LOWER(epasts) LIKE ? ORDER BY lietotaja_id",
                    pattern, pattern, pattern, pattern);
        });
    }

    public Lietotajs findUserById(int id, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        return withConnection(connection -> new LietotajsDAO(connection).findById(id));
    }

    public Lietotajs saveManagedUser(Lietotajs form, int editingId, String newPassword, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        if (form == null) throw new IllegalArgumentException("Nav saņemti lietotāja dati.");
        requireText(form.getVards(), "Vārds ir obligāts.");
        requireText(form.getUzvards(), "Uzvārds ir obligāts.");
        requireText(form.getLietotajvards(), "Lietotājvārds ir obligāts.");
        requireText(form.getEpasts(), "E-pasts ir obligāts.");
        requireMaxLength(form.getVards(), 50, "Vārds");
        requireMaxLength(form.getUzvards(), 50, "Uzvārds");
        requireMaxLength(form.getLietotajvards(), 50, "Lietotājvārds");
        requireMaxLength(form.getEpasts(), 100, "E-pasts");
        if (!EMAIL_PATTERN.matcher(form.getEpasts().trim()).matches()) throw new IllegalArgumentException("E-pasta adrese nav derīga.");
        if (newPassword != null && !newPassword.isBlank()) validatePassword(newPassword);
        else if (editingId < 0) throw new IllegalArgumentException("Jaunam lietotājam parole ir obligāta.");

        return withConnection(connection -> {
            LietotajsDAO users = new LietotajsDAO(connection);
            Lietotajs value = editingId < 0 ? new Lietotajs() : users.findById(editingId);
            if (value == null) throw new SQLException("Lietotājs nav atrasts.");
            String username = form.getLietotajvards().trim();
            String email = form.getEpasts().trim();
            if (users.existsByUsername(username, editingId)) throw new SQLException("Lietotājvārds jau ir aizņemts.");
            if (users.existsByEmail(email, editingId)) throw new SQLException("E-pasts jau ir aizņemts.");

            value.setVards(form.getVards().trim());
            value.setUzvards(form.getUzvards().trim());
            value.setLietotajvards(username);
            value.setEpasts(email);
            if (actor.getLoma() == Loma.ADMIN) {
                Loma requestedRole = form.getLoma() == null ? Loma.READER : form.getLoma();
                if (editingId >= 0 && editingId == actor.getLietotajaId()
                        && requestedRole != value.getLoma()) {
                    throw new IllegalArgumentException("Pašreizējā lietotāja lomu mainīt nevar.");
                }
                if (editingId >= 0 && value.getLoma() == Loma.ADMIN && requestedRole != Loma.ADMIN) {
                    ensureAnotherAdmin(connection, value.getLietotajaId());
                }
                value.setLoma(requestedRole);
            } else if (editingId < 0) {
                value.setLoma(Loma.READER);
            }
            if (newPassword != null && !newPassword.isBlank()) {
                value.setParole(PasswordUtil.hash(newPassword));
            }
            if (editingId < 0) users.insert(value);
            else if (!users.update(value)) throw new SQLException("Lietotāja dati netika saglabāti.");
            return value;
        });
    }

    public boolean deleteUser(int id, Lietotajs actor) throws SQLException {
        requireAdmin(actor);
        if (id == actor.getLietotajaId()) throw new IllegalArgumentException("Pašreizējo lietotāju dzēst nevar.");
        return withConnection(connection -> {
            LietotajsDAO users = new LietotajsDAO(connection);
            Lietotajs target = users.findById(id);
            if (target != null && target.getLoma() == Loma.ADMIN) ensureAnotherAdmin(connection, id);
            return users.delete(id);
        });
    }

    public void changeUserRole(int id, Loma role, Lietotajs actor) throws SQLException {
        requireAdmin(actor);
        if (id == actor.getLietotajaId()) throw new IllegalArgumentException("Pašreizējam lietotājam lomu mainīt nevar.");
        if (role == null) throw new IllegalArgumentException("Izvēlieties lomu.");
        withConnection(connection -> {
            LietotajsDAO users = new LietotajsDAO(connection);
            Lietotajs target = users.findById(id);
            if (target == null) throw new SQLException("Lietotājs nav atrasts.");
            if (target.getLoma() == Loma.ADMIN && role != Loma.ADMIN) ensureAnotherAdmin(connection, id);
            target.setLoma(role);
            if (!users.update(target)) throw new SQLException("Lomu neizdevās saglabāt.");
            return null;
        });
    }

    public List<Loma> getAvailableRoles(Lietotajs actor) {
        requireStaff(actor);
        if (actor.getLoma() == Loma.ADMIN) return List.of(Loma.values());
        return List.of(Loma.READER);
    }

    public List<Lietotajs> findReaderUsers(Lietotajs actor) throws SQLException {
        requireStaff(actor);
        return withConnection(connection -> {
            List<Lietotajs> users = new LietotajsDAO(connection).findAll();
            users.removeIf(user -> user.getLoma() != Loma.READER);
            return users;
        });
    }

    public Set<String> findReadNotificationKeys(Lietotajs actor) throws SQLException {
        requireNotificationAccess(actor);
        return withConnection(connection -> {
            Set<String> keys = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT tips, pazinojuma_id FROM pazinojums_izlasits WHERE lietotaja_id = ?")) {
                statement.setInt(1, actor.getLietotajaId());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        keys.add(buildNotificationKey(rs.getString("tips"), rs.getInt("pazinojuma_id")));
                    }
                }
            }
            return keys;
        });
    }

    public void markNotificationRead(String type, int notificationId, Lietotajs actor) throws SQLException {
        requireNotificationAccess(actor);
        if (!"loan".equals(type) && !"reservation".equals(type)) {
            throw new IllegalArgumentException("Nezināms paziņojuma veids.");
        }
        if (notificationId <= 0) throw new IllegalArgumentException("Nederīgs paziņojuma ID.");
        withConnection(connection -> {
            boolean visible = false;
            if ("loan".equals(type)) {
                for (Izsniegums loan : new IzsniegumsDAO(connection).findOverdue()) {
                    if (loan.getIzsniegumaId() == notificationId
                            && (actor.getLoma() != Loma.READER || (loan.getLietotajs() != null
                            && loan.getLietotajs().getLietotajaId() == actor.getLietotajaId()))) {
                        visible = true;
                        break;
                    }
                }
            } else {
                for (Rezervacija reservation : new RezervacijaDAO(connection).findActive()) {
                    if (reservation.getRezervacijasId() == notificationId
                            && (actor.getLoma() != Loma.READER || (reservation.getLietotajs() != null
                            && reservation.getLietotajs().getLietotajaId() == actor.getLietotajaId()))) {
                        visible = true;
                        break;
                    }
                }
            }
            if (!visible) throw new SQLException("Paziņojums vairs nav aktīvs vai nav pieejams šim lietotājam.");

            boolean exists;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM pazinojums_izlasits WHERE lietotaja_id = ? AND tips = ? AND pazinojuma_id = ?")) {
                query.setInt(1, actor.getLietotajaId());
                query.setString(2, type);
                query.setInt(3, notificationId);
                try (ResultSet rs = query.executeQuery()) { exists = rs.next(); }
            }
            if (exists) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE pazinojums_izlasits SET izlasits_datums = CURRENT_TIMESTAMP "
                        + "WHERE lietotaja_id = ? AND tips = ? AND pazinojuma_id = ?")) {
                    update.setInt(1, actor.getLietotajaId());
                    update.setString(2, type);
                    update.setInt(3, notificationId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO pazinojums_izlasits (lietotaja_id, tips, pazinojuma_id) VALUES (?, ?, ?)")) {
                    insert.setInt(1, actor.getLietotajaId());
                    insert.setString(2, type);
                    insert.setInt(3, notificationId);
                    insert.executeUpdate();
                }
            }
            return null;
        });
    }
}