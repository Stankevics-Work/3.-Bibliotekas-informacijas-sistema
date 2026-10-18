package bibliotekas_informacijas_sistema.dao;

import bibliotekas_informacijas_sistema.model.Lietotajs;
import bibliotekas_informacijas_sistema.model.Gramata;
import bibliotekas_informacijas_sistema.model.Eksemplars;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import bibliotekas_informacijas_sistema.db.DatabaseManager;
import static bibliotekas_informacijas_sistema.util.PasswordUtil.*;

/** Eksemplars datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class EksemplarsDAO {
    private final Connection connection;

    /** Ērtā konstrukcija lietotnes GUI slāņa izsaukumiem; katra darbība atver savu DB savienojumu. */
    public EksemplarsDAO() { this.connection = null; }

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public EksemplarsDAO(Connection connection) { this.connection = connection; }

    /** Vienots SELECT ar JOIN, lai izvairītos no N+1 vaicājumiem. */
    private static final String BASE_SELECT =
            "SELECT e.*, "
            + "g.nosaukums AS g_nosaukums, g.autors AS g_autors, g.isbn AS g_isbn, "
            + "g.kategorija AS g_kategorija, g.izdosanas_gads AS g_izdosanas_gads, "
            + "g.pieejamiba AS g_pieejamiba "
            + "FROM eksemplars e "
            + "LEFT JOIN gramata g ON g.gramatas_id = e.gramatas_id ";

    private Eksemplars map(ResultSet rs) throws SQLException {
        Eksemplars value = new Eksemplars();
        value.setEksemplaraId(rs.getInt("eksemplara_id"));
        value.setBiblioteka(rs.getString("biblioteka"));
        value.setStatus(rs.getString("status"));

        int bookId = rs.getInt("gramatas_id");
        if (!rs.wasNull()) {
            Gramata g = new Gramata();
            g.setGramatasId(bookId);
            g.setNosaukums(rs.getString("g_nosaukums"));
            g.setAutors(rs.getString("g_autors"));
            g.setIsbn(rs.getString("g_isbn"));
            g.setKategorija(rs.getString("g_kategorija"));
            g.setIzdosanasGads((Integer) rs.getObject("g_izdosanas_gads"));
            g.setPieejamiba(rs.getBoolean("g_pieejamiba"));
            value.setGramata(g);
        }
        return value;
    }

    private List<Eksemplars> query(String sql, Object... args) throws SQLException {
        List<Eksemplars> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }

    public Eksemplars findById(int id) throws SQLException {
        List<Eksemplars> result = query(BASE_SELECT + "WHERE e.eksemplara_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Eksemplars> findAll() throws SQLException {
        return query(BASE_SELECT + "ORDER BY e.eksemplara_id");
    }

    private void bind(PreparedStatement ps, Eksemplars value) throws SQLException {
        ps.setInt(1, value.getGramata().getGramatasId());
        ps.setString(2, value.getBiblioteka());
        ps.setString(3, value.getStatus());
    }

    public int insert(Eksemplars value) throws SQLException {
        if (value.getStatus() == null) value.setStatus("pieejams");
        if (!"pieejams".equals(value.getStatus())) throw new SQLException("Jaunam eksemplāram jābūt pieejamam");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO eksemplars (gramatas_id, biblioteka, status) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, value); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                value.setEksemplaraId(keys.getInt(1)); return value.getEksemplaraId();
            }
        }
    }

    /** Rediģē tikai atrašanās vietu, grāmatas saite un aprites statuss paliek nemainīgi */
    public boolean update(Eksemplars value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE eksemplars SET biblioteka = ? WHERE eksemplara_id = ? AND gramatas_id = ? AND status = ?")) {
            ps.setString(1, value.getBiblioteka()); ps.setInt(2, value.getEksemplaraId());
            ps.setInt(3, value.getGramata().getGramatasId()); ps.setString(4, value.getStatus());
            return ps.executeUpdate() == 1;
        }
    }

    public List<Eksemplars> findByBook(int bookId) throws SQLException {
        return query(BASE_SELECT + "WHERE e.gramatas_id = ? ORDER BY e.eksemplara_id", bookId);
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM eksemplars WHERE eksemplara_id = ? AND status = 'pieejams'")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    private <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            return work.run(c);
        }
    }

    public List<Eksemplars> findAvailableCopies(Lietotajs actor) throws SQLException {
        requireStaff(actor);
        return withConnection(connection -> new EksemplarsDAO(connection).query(
                BASE_SELECT + "WHERE e.status = 'pieejams' AND e.gramatas_id IS NOT NULL "
                + "AND g.gramatas_id IS NOT NULL ORDER BY e.eksemplara_id"));
    }

    /** Idempotenti izveido demonstrācijas datus šīs DAO atbildības tabulā. */
    int ensureInitialCopy(int bookId, String library, String status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT eksemplara_id FROM eksemplars WHERE gramatas_id = ? ORDER BY eksemplara_id")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
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
}