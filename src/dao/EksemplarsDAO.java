package dao;

import model.Gramata;
import model.Lietotajs;
import model.Eksemplars;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import db.DatabaseManager;
import static util.PasswordUtil.*;

/** Eksemplars datu piekluve ar parametrizetiem JDBC vaicajumiem */
public class EksemplarsDAO {
    private static final String DEFAULT_LIBRARY = "DTTT bibliotēka";

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
                value.setEksemplaraId(keys.getInt(1)); 
            }
        }
        refreshAvailability(value.getGramata().getGramatasId());
        return value.getEksemplaraId();
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
        int bookId = -1;
        try (PreparedStatement getBook = connection.prepareStatement("SELECT gramatas_id FROM eksemplars WHERE eksemplara_id = ?")) {
            getBook.setInt(1, id);
            try (ResultSet rs = getBook.executeQuery()) {
                if (rs.next()) bookId = rs.getInt(1);
            }
        }
        
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM eksemplars WHERE eksemplara_id = ? AND status = 'pieejams'")) {
            ps.setInt(1, id); 
            boolean deleted = ps.executeUpdate() == 1;
            if (deleted && bookId != -1) {
                refreshAvailability(bookId);
            }
            return deleted;
        }
    }

    /** Pārrēķina gramata.pieejamiba pēc eksemplāru statusiem. */
    void refreshAvailability(int bookId) throws SQLException {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE gramata SET pieejamiba = CASE WHEN EXISTS ("
                + "SELECT 1 FROM eksemplars WHERE gramatas_id = ? AND status = 'pieejams'"
                + ") THEN TRUE ELSE FALSE END WHERE gramatas_id = ?")) {
            ps.setInt(1, bookId);
            ps.setInt(2, bookId);
            ps.executeUpdate();
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

    // ============================================================
    // Eksemplāru pievienošana / noņemšana
    // ============================================================

    /**
     * Pievieno {@code count} jaunus pieejamus eksemplārus grāmatai.
     * Atver savu savienojumu un transakciju — izsauc no GUI.
     */
    public void addCopies(int bookId, int count, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        if (bookId <= 0) throw new IllegalArgumentException("Nederīgs grāmatas ID.");
        if (count < 1 || count > 100)
            throw new IllegalArgumentException("Eksemplāru skaitam jābūt no 1 līdz 100.");
        withConnection(c -> {
            c.setAutoCommit(false);
            try {
                new EksemplarsDAO(c).addCopiesInTransaction(bookId, count);
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
            return null;
        });
    }

    /** Iekšējā versija — izmanto jau atvērtu savienojumu/transakciju. */
    void addCopiesInTransaction(int bookId, int count) throws SQLException {
        if (connection == null)
            throw new SQLException("Iekšējai metodei nepieciešams savienojums.");
        for (int i = 0; i < count; i++) {
            Gramata g = new Gramata();
            g.setGramatasId(bookId);
            Eksemplars copy = new Eksemplars();
            copy.setGramata(g);
            copy.setBiblioteka(DEFAULT_LIBRARY);
            copy.setStatus("pieejams");
            insert(copy);
        }
        refreshAvailability(bookId);
    }

    /**
     * Noņem visus grāmatai pieejamos eksemplārus. Eksemplāri, kas šobrīd izsniegti
     * vai rezervēti, netiek aiztikti.
     */
    public void removeAvailableCopies(int bookId, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        if (bookId <= 0) throw new IllegalArgumentException("Nederīgs grāmatas ID.");
        withConnection(c -> {
            c.setAutoCommit(false);
            try {
                new EksemplarsDAO(c).removeAvailableCopiesInTransaction(bookId);
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
            return null;
        });
    }

    /** Iekšējā versija — izmanto jau atvērtu savienojumu/transakciju. */
    void removeAvailableCopiesInTransaction(int bookId) throws SQLException {
        if (connection == null)
            throw new SQLException("Iekšējai metodei nepieciešams savienojums.");
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM eksemplars WHERE gramatas_id = ? AND status = 'pieejams'")) {
            ps.setInt(1, bookId);
            try {
                ps.executeUpdate();
            } catch (SQLException ex) {
                if ("23503".equals(ex.getSQLState())) {
                    throw new SQLException(
                        "Dažiem pieejamiem eksemplāriem ir izsniegumu vēsture — tos nevar izdzēst, "
                        + "tāpēc grāmatu nevar padarīt nepieejamu. Sazinieties ar administratoru.", ex);
                }
                throw ex;
            }
        }
        refreshAvailability(bookId);
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