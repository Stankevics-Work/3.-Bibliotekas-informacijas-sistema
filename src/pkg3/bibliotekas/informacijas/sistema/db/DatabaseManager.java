package pkg3.bibliotekas.informacijas.sistema.db;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

/** Derby savienojuma pārvaldība un sākotnējās shēmas izveide */
public final class DatabaseManager {
    private static final String[] TABLES = {"LIETOTAJS", "GRAMATA", "EKSEMPLARS", "IZSNIEGUMS", "REZERVACIJA"};
    private DatabaseManager() { }

    /** Atver iegulto Derby datubāzi, izsaucējs aizver savienojumu */
    public static Connection getConnection() throws SQLException {
        Path path = Paths.get(System.getProperty("libinfo.db.path", "data/LibInfo")).toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        } catch (IOException | ClassNotFoundException ex) {
            throw new SQLException("Neizdevās sagatavot Derby datubāzi", ex);
        }
        return DriverManager.getConnection("jdbc:derby:" + path + ";create=true");
    }

    /** Izveido shēmu tukšā datubāzē, atkārtota palaišana saglabā esošos datus */
    public static synchronized void initialize(Connection c) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Inicializācijai vajadzīgs autoCommit=true");
        int present = 0;
        for (String table : TABLES) {
            try (ResultSet rs = c.getMetaData().getTables(null, "APP", table, new String[]{"TABLE"})) {
                if (rs.next()) present++;
            }
        }
        if (present == TABLES.length) {
            // Vecajai SQL versijai pieejamiba bija skatā, nevis tabulā
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
        } catch (IOException ex) { throw new SQLException("Neizdevās nolasīt shēmu", ex); }
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            // Šajā shēmā nav komentāru vai semikolu teksta vērtībās
            for (String command : sql.split(";")) {
                if (!command.trim().isEmpty()) s.executeUpdate(command.trim());
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback(); throw ex;
        } finally { c.setAutoCommit(true); }
    }

    /** Pārbauda īstu JDBC vaicājumu */
    public static boolean testConnection(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("VALUES 1")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }
}
