package pkg3.bibliotekas.informacijas.sistema.db;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import pkg3.bibliotekas.informacijas.sistema.dao.EksemplarsDAO;
import pkg3.bibliotekas.informacijas.sistema.dao.GramataDAO;
import pkg3.bibliotekas.informacijas.sistema.dao.LietotajsDAO;
import pkg3.bibliotekas.informacijas.sistema.model.Eksemplars;
import pkg3.bibliotekas.informacijas.sistema.model.Gramata;
import pkg3.bibliotekas.informacijas.sistema.model.Lietotajs;
import pkg3.bibliotekas.informacijas.sistema.model.Loma;
import pkg3.bibliotekas.informacijas.sistema.util.PasswordUtil;

/** Derby savienojuma pārvaldība, shēmas izveide un sākotnējo datu sagatavošana. */
public final class DatabaseManager {
    private static final String DB_PATH = "lib_db";
    private static final String[] TABLES = {"LIETOTAJS", "GRAMATA", "EKSEMPLARS", "IZSNIEGUMS", "REZERVACIJA"};

    // Visi dati, kas bija pārnestajā projekta DB. Admina parole apzināti nomainīta uz admin123.
    private static final String LEGACY_HASH = "$2a$12$S/4iFvK26WWpHziepOKIFe94sUJ1SOmNnnegW0jz/MDXobmpJHmke";
    private static final String ADMIN_HASH = "$2a$12$MvTNcSrTUIANNWjhVRdkke7aL.6jpHRdzhTMG3CnQnRt/uq8wACkq";

    private DatabaseManager() { }

    /**
     * Atver vienīgo projekta Derby datubāzi lib_db mapē.
     * Ja mape vai DB vēl neeksistē, Derby tās automātiski izveido.
     */
    public static Connection getConnection() throws SQLException {
        Path path = Paths.get(System.getProperty("libinfo.db.path", DB_PATH)).toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            removeZipPlaceholderIfNeeded(path);
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        } catch (IOException | ClassNotFoundException ex) {
            throw new SQLException("Neizdevās sagatavot Derby datubāzi", ex);
        }
        return DriverManager.getConnection("jdbc:derby:" + path + ";create=true");
    }

    /** Noņem tikai ZIP arhīva tukšo placeholder mapi, lai Derby varētu izveidot DB ar create=true. */
    private static void removeZipPlaceholderIfNeeded(Path path) throws IOException {
        if (!Files.isDirectory(path) || Files.exists(path.resolve("service.properties"))) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
            for (Path entry : entries) {
                if (!entry.getFileName().toString().equals("README.txt")) return;
            }
        }
        Path readme = path.resolve("README.txt");
        Files.deleteIfExists(readme);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
            if (entries.iterator().hasNext()) return;
        }
        Files.deleteIfExists(path);
    }

    /** Izveido shēmu tukšā datubāzē; atkārtota palaišana esošos datus nedzēš. */
    public static synchronized void initialize(Connection c) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Inicializācijai vajadzīgs autoCommit=true");
        int present = 0;
        for (String table : TABLES) {
            try (ResultSet rs = c.getMetaData().getTables(null, "APP", table, new String[]{"TABLE"})) {
                if (rs.next()) present++;
            }
        }
        if (present == TABLES.length) {
            try (Statement s = c.createStatement()) {
                s.executeQuery("SELECT pieejamiba FROM gramata WHERE 1=0").close();
                s.executeQuery("SELECT * FROM gramatu_katalogs WHERE 1=0").close();
            }
            return;
        }
        if (present != 0) throw new SQLException("Datubāzes shēma ir nepilnīga, pārbaudiet tabulas");

        String sql;
        try (InputStream in = DatabaseManager.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IOException("Nav atrasts schema.sql");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            sql = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new SQLException("Neizdevās nolasīt shēmu", ex);
        }

        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.trim().isEmpty()) s.executeUpdate(command.trim());
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /**
     * Pirmās palaišanas laikā nodrošina visas sākotnējās lietotnes datu rindas.
     * Metode ir idempotenta: esošie dati netiek dublēti vai dzēsti.
     */
    public static synchronized void seedInitialData(Connection c) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Sākotnējo datu ievietošanai vajadzīgs autoCommit=true");

        LietotajsDAO users = new LietotajsDAO(c);
        ensureUser(c, users, "Anna", "Ozola", "admin", ADMIN_HASH, "admin@example.com", Loma.ADMIN, true);
        ensureUser(c, users, "Ilze", "Kalnina", "bibliotekars", LEGACY_HASH, "library@example.com", Loma.LIBRARIAN, false);
        ensureUser(c, users, "Janis", "Berzins", "lasitajs", LEGACY_HASH, "reader@example.com", Loma.READER, false);
        ensureUser(c, users, "Mara", "Liepa", "autors", LEGACY_HASH, "author@example.com", Loma.AUTHOR, false);

        int book1 = ensureBook(c, "Mazais celojums", "Stasts par celojumu.", 2024, "Stasti", null, "Mara Liepa", true);
        int book2 = ensureBook(c, "Java pamati", "Ievads programmesana.", 2025, "Macibu literatura", null, "Peteris Ozols", false);

        int copy1 = ensureCopy(c, book1, "DTTT biblioteka", "pieejams");
        int copy2 = ensureCopy(c, book2, "DTTT biblioteka", "izsniegts");

        int readerId = users.findByUsername("lasitajs").getLietotajaId();
        ensureLoan(c, readerId, copy2,
                Timestamp.valueOf("2026-10-01 10:24:11.879"),
                Timestamp.valueOf("2026-10-15 10:24:11.879"));
        ensureReservation(c, readerId, book1,
                Timestamp.valueOf("2026-10-01 10:24:11.916"), "akt\u012bva");
    }

    private static void ensureUser(Connection c, LietotajsDAO users, String firstName, String lastName,
                                   String username, String passwordHash, String email, Loma role,
                                   boolean forceAdminPassword) throws SQLException {
        Lietotajs existing = users.findByUsername(username);
        if (existing == null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO lietotajs (vards, uzvards, lietotajvards, parole, epasts, loma, izveidots) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, firstName);
                ps.setString(2, lastName);
                ps.setString(3, username);
                ps.setString(4, passwordHash);
                ps.setString(5, email);
                ps.setString(6, role.name());
                ps.setTimestamp(7, Timestamp.valueOf("2026-10-01 10:24:11.843"));
                ps.executeUpdate();
            }
            return;
        }
        if (forceAdminPassword && !PasswordUtil.verify("admin123", existing.getParole())) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE lietotajs SET parole = ? WHERE lietotaja_id = ?")) {
                ps.setString(1, passwordHash);
                ps.setInt(2, existing.getLietotajaId());
                ps.executeUpdate();
            }
        }
    }

    private static int ensureBook(Connection c, String title, String description, Integer year,
                                  String category, String isbn, String author, boolean available) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT gramatas_id FROM gramata WHERE nosaukums = ?")) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO gramata (nosaukums, apraksts, izdosanas_gads, kategorija, isbn, autors, pieejamiba) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, description);
            if (year == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, year);
            ps.setString(4, category);
            if (isbn == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, isbn);
            ps.setString(6, author);
            ps.setBoolean(7, available);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Neizdevās iegūt grāmatas ID");
                return keys.getInt(1);
            }
        }
    }

    private static int ensureCopy(Connection c, int bookId, String library, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT eksemplara_id FROM eksemplars WHERE gramatas_id = ? ORDER BY eksemplara_id")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO eksemplars (gramatas_id, biblioteka, status) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookId);
            ps.setString(2, library);
            ps.setString(3, status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Neizdevās iegūt eksemplāra ID");
                return keys.getInt(1);
            }
        }
    }

    private static void ensureLoan(Connection c, int userId, int copyId, Timestamp issued, Timestamp due) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM izsniegums WHERE lietotaja_id = ? AND eksemplara_id = ? AND izsniegts = ?")) {
            ps.setInt(1, userId); ps.setInt(2, copyId); ps.setTimestamp(3, issued);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return; }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO izsniegums (lietotaja_id, eksemplara_id, izsniegts, termins, atgriezts) VALUES (?, ?, ?, ?, NULL)")) {
            ps.setInt(1, userId); ps.setInt(2, copyId); ps.setTimestamp(3, issued); ps.setTimestamp(4, due); ps.executeUpdate();
        }
    }

    private static void ensureReservation(Connection c, int userId, int bookId, Timestamp date, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM rezervacija WHERE lietotaja_id = ? AND gramatas_id = ? AND datums = ?")) {
            ps.setInt(1, userId); ps.setInt(2, bookId); ps.setTimestamp(3, date);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return; }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO rezervacija (lietotaja_id, gramatas_id, datums, statuss) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, userId); ps.setInt(2, bookId); ps.setTimestamp(3, date); ps.setString(4, status); ps.executeUpdate();
        }
    }

    /** Pārbauda īstu JDBC vaicājumu. */
    public static boolean testConnection(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("VALUES 1")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }
}
