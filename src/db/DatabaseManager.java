package db;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import util.PasswordUtil;

/** Derby savienojuma pārvaldība un shēmas izveide/migrācija. */
public final class DatabaseManager {
    private static final String DB_PATH = "lib_db";
    private static final String[] TABLES = {"LIETOTAJS", "GRAMATA", "EKSEMPLARS", "IZSNIEGUMS", "REZERVACIJA"};

    private DatabaseManager() { }

    /**
     * Atrod lietotnes pamatmapi, lai datubāze neatrastos pagaidu build/ vai dist/ mapē.
     * Tādējādi "ant clean" neizdzēš lietotāja datus. Instalētam atsevišķam JAR fallback
     * gadījumā datubāze tiek glabāta blakus JAR failam.
     */
    private static Path getApplicationDirectory() {
        try {
            Path location = Paths.get(DatabaseManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path base = Files.isRegularFile(location) ? location.getParent() : location;
            if (base == null) return Paths.get(".").toAbsolutePath();

            // NetBeans/Ant: .../build/classes -> projekta sakne.
            if (base.getFileName() != null && "classes".equals(base.getFileName().toString())
                    && base.getParent() != null && base.getParent().getFileName() != null
                    && "build".equals(base.getParent().getFileName().toString())) {
                Path candidate = base.getParent().getParent();
                if (candidate != null && Files.isRegularFile(candidate.resolve("build.xml"))) return candidate;
            }
            // Izplatītā JAR: .../dist/app.jar -> projekta sakne, ja pieejama.
            if (base.getFileName() != null && "dist".equals(base.getFileName().toString())) {
                Path candidate = base.getParent();
                if (candidate != null && Files.isRegularFile(candidate.resolve("build.xml"))) return candidate;
            }
            return base;
        } catch (Exception ex) {
            return Paths.get(".").toAbsolutePath();
        }
    }

    /**
     * Atver projekta Derby datubāzi lib_db mapē. Ja DB vēl neeksistē, Derby to izveido.
     * Ceļš ir stabils arī pēc build tīrīšanas; to var pārrakstīt ar -Dlibinfo.db.path=...
     */
    public static Connection getConnection() throws SQLException {
        String override = System.getProperty("libinfo.db.path");
        Path path = (override != null && !override.isBlank())
                ? Paths.get(override).toAbsolutePath()
                : getApplicationDirectory().resolve(DB_PATH).toAbsolutePath();
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
    private static final String PLACEHOLDER_README = "README_DO_NOT_TOUCH_FILES.txt";
    
    private static void removeZipPlaceholderIfNeeded(Path path) throws IOException {
    if (!Files.isDirectory(path) || Files.exists(path.resolve("service.properties"))) return;
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (!name.equals(PLACEHOLDER_README) && !name.equals("README.txt")) return;
        }
    }
    Files.deleteIfExists(path.resolve(PLACEHOLDER_README));
    Files.deleteIfExists(path.resolve("README.txt"));
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
            ensureNotificationReadTable(c);
            migrateLegacyPasswords(c);
            return;
        }
        if (present != 0) throw new SQLException("Datubāzes shēma ir nepilnīga, pārbaudiet tabulas");

        String sql;
        try (InputStream in = DatabaseManager.class.getResourceAsStream("/bibliotekas_informacijas_sistema/schema.sql")) {
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
            sql = sql.replaceAll("(?m)^--.*$", ""); 
            for (String command : sql.split(";")) {
                if (!command.trim().isEmpty()) {
                    s.executeUpdate(command.trim());
                }
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
        ensureNotificationReadTable(c);
        migrateLegacyPasswords(c);
    }

    /**
     * Migrē vecajās instalācijās glabātās paroles no atklāta teksta uz BCrypt.
     * Parole pati netiek mainīta, tāpēc lietotāji var pieteikties ar līdzšinējo paroli.
     */
    private static void migrateLegacyPasswords(Connection c) throws SQLException {
        List<LegacyPassword> legacyPasswords = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT lietotaja_id, parole FROM lietotajs");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String password = rs.getString("parole");
                if (password != null && !password.isBlank()
                        && !PasswordUtil.isBcryptHash(password)
                        && !password.startsWith("$2")) {
                    legacyPasswords.add(new LegacyPassword(rs.getInt("lietotaja_id"), password));
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE lietotajs SET parole = ? WHERE lietotaja_id = ? AND parole = ?")) {
            for (LegacyPassword legacy : legacyPasswords) {
                final String hash;
                try {
                    hash = PasswordUtil.hash(legacy.password);
                } catch (IllegalArgumentException ex) {
                    // Neatbalstāmās vērtības nemainām, lai datubāzes palaišana netiktu bloķēta.
                    continue;
                }
                ps.setString(1, hash);
                ps.setInt(2, legacy.userId);
                ps.setString(3, legacy.password);
                ps.executeUpdate();
            }
        }
    }

    private static final class LegacyPassword {
        private final int userId;
        private final String password;

        private LegacyPassword(int userId, String password) {
            this.userId = userId;
            this.password = password;
        }
    }

    /** Atpakaļsaderīga shēmas migrācija vecākām instalācijām. */
    private static void ensureNotificationReadTable(Connection c) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, "APP", "PAZINOJUMS_IZLASITS", new String[]{"TABLE"})) {
            if (rs.next()) return;
        }
        try (Statement statement = c.createStatement()) {
            statement.executeUpdate("CREATE TABLE pazinojums_izlasits ("
                    + "lietotaja_id INTEGER NOT NULL REFERENCES lietotajs(lietotaja_id) ON DELETE CASCADE, "
                    + "tips VARCHAR(20) NOT NULL, pazinojuma_id INTEGER NOT NULL, "
                    + "izlasits_datums TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                    + "PRIMARY KEY (lietotaja_id, tips, pazinojuma_id), "
                    + "CHECK (tips IN ('loan', 'reservation')), CHECK (pazinojuma_id > 0))");
        }
    }

    /** Pārbauda īstu JDBC vaicājumu. */
    public static boolean testConnection(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("VALUES 1")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }
}
