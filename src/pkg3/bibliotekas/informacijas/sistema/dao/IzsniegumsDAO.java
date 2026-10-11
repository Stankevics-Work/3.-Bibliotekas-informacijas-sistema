package pkg3.bibliotekas.informacijas.sistema.dao;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import pkg3.bibliotekas.informacijas.sistema.db.DatabaseManager;
import pkg3.bibliotekas.informacijas.sistema.model.*;
import static pkg3.bibliotekas.informacijas.sistema.util.PasswordUtil.*;

/** Izsniegums datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class IzsniegumsDAO {
    private final Connection connection;

    /** Ērtā konstrukcija lietotnes GUI slāņa izsaukumiem; katra darbība atver savu DB savienojumu. */
    public IzsniegumsDAO() { this.connection = null; }

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public IzsniegumsDAO(Connection connection) { this.connection = connection; }

    /** Vienots SELECT ar JOIN — novērš N+1 vaicājumus. */
    private static final String BASE_SELECT =
            "SELECT i.*, "
            + "u.vards AS u_vards, u.uzvards AS u_uzvards, u.lietotajvards AS u_lietotajvards, "
            + "u.epasts AS u_epasts, u.loma AS u_loma, "
            + "e.biblioteka AS e_biblioteka, e.status AS e_status, e.gramatas_id AS g_id, "
            + "g.nosaukums AS g_nosaukums, g.autors AS g_autors, g.isbn AS g_isbn, "
            + "g.kategorija AS g_kategorija, g.izdosanas_gads AS g_izdosanas_gads, "
            + "g.pieejamiba AS g_pieejamiba "
            + "FROM izsniegums i "
            + "LEFT JOIN lietotajs u ON u.lietotaja_id = i.lietotaja_id "
            + "LEFT JOIN eksemplars e ON e.eksemplara_id = i.eksemplara_id "
            + "LEFT JOIN gramata g ON g.gramatas_id = e.gramatas_id ";

    private Izsniegums map(ResultSet rs) throws SQLException {
        Izsniegums value = new Izsniegums();
        value.setIzsniegumaId(rs.getInt("izsnieguma_id"));
        value.setIzsniegts(rs.getTimestamp("izsniegts"));
        value.setTermins(rs.getTimestamp("termins"));
        value.setAtgriezts(rs.getTimestamp("atgriezts"));

        Lietotajs u = new Lietotajs();
        u.setLietotajaId(rs.getInt("lietotaja_id"));
        u.setVards(rs.getString("u_vards"));
        u.setUzvards(rs.getString("u_uzvards"));
        u.setLietotajvards(rs.getString("u_lietotajvards"));
        u.setEpasts(rs.getString("u_epasts"));
        String loma = rs.getString("u_loma");
        if (loma != null) u.setLoma(Loma.valueOf(loma));
        value.setLietotajs(u);

        Eksemplars e = new Eksemplars();
        e.setEksemplaraId(rs.getInt("eksemplara_id"));
        e.setBiblioteka(rs.getString("e_biblioteka"));
        e.setStatus(rs.getString("e_status"));
        int gId = rs.getInt("g_id");
        if (!rs.wasNull()) {
            Gramata g = new Gramata();
            g.setGramatasId(gId);
            g.setNosaukums(rs.getString("g_nosaukums"));
            g.setAutors(rs.getString("g_autors"));
            g.setIsbn(rs.getString("g_isbn"));
            g.setKategorija(rs.getString("g_kategorija"));
            g.setIzdosanasGads((Integer) rs.getObject("g_izdosanas_gads"));
            g.setPieejamiba(rs.getBoolean("g_pieejamiba"));
            e.setGramata(g);
        }
        value.setEksemplars(e);
        return value;
    }

    private List<Izsniegums> query(String sql, Object... args) throws SQLException {
        List<Izsniegums> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }

    public Izsniegums findById(int id) throws SQLException {
        List<Izsniegums> result = query(BASE_SELECT + "WHERE i.izsnieguma_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Izsniegums> findAll() throws SQLException {
        return query(BASE_SELECT + "ORDER BY i.izsnieguma_id");
    }

    private void bind(PreparedStatement ps, Izsniegums value) throws SQLException {
        ps.setInt(1, value.getLietotajs().getLietotajaId());
        ps.setInt(2, value.getEksemplars().getEksemplaraId());
        ps.setTimestamp(3, value.getIzsniegts() == null ? null : new Timestamp(value.getIzsniegts().getTime()));
        ps.setTimestamp(4, value.getTermins() == null ? null : new Timestamp(value.getTermins().getTime()));
        ps.setTimestamp(5, value.getAtgriezts() == null ? null : new Timestamp(value.getAtgriezts().getTime()));
    }

    /** Patstāvīga transakcija ar SERIALIZABLE izolāciju. Aktora loma tiek pārbaudīta. */
    public int insert(Izsniegums value, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        if (!connection.getAutoCommit()) throw new SQLException("Šī metode pati pārvalda transakciju");
        int previous = connection.getTransactionIsolation();
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        connection.setAutoCommit(false);
        try {
            if (value.getLietotajs() == null) throw new SQLException("Nav norādīts lasītājs");
            Lietotajs user = new LietotajsDAO(connection).findById(value.getLietotajs().getLietotajaId());
            if (user == null || user.getLoma() != Loma.READER) throw new SQLException("Darbība atļauta tikai lasītājam");

            if (value.getEksemplars() == null || value.getTermins() == null) throw new SQLException("Nav norādīts eksemplārs vai termiņš");
            Eksemplars copy = new EksemplarsDAO(connection).findById(value.getEksemplars().getEksemplaraId());
            if (copy == null || !"pieejams".equals(copy.getStatus())) throw new SQLException("Eksemplārs nav pieejams");

            // Pārbauda, vai eksemplārs jau nav izsniegts
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM izsniegums WHERE eksemplara_id = ? AND atgriezts IS NULL")) {
                check.setInt(1, copy.getEksemplaraId());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) throw new SQLException("Eksemplārs jau ir izsniegts");
                }
            }

            List<Rezervacija> queue = new RezervacijaDAO(connection)
                    .findActiveByBook(copy.getGramata().getGramatasId());
            if (!queue.isEmpty() && queue.get(0).getLietotajs().getLietotajaId() != user.getLietotajaId())
                throw new SQLException("Grāmatu gaida cits lasītājs");

            value.setIzsniegts(new java.util.Date());
            value.setAtgriezts(null);

            int id;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO izsniegums (lietotaja_id, eksemplara_id, izsniegts, termins, atgriezts) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, value); ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                    id = keys.getInt(1);
                }
            }

            if (!queue.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE rezervacija SET statuss = 'izpildīta' WHERE rezervacijas_id = ?")) {
                    ps.setInt(1, queue.get(0).getRezervacijasId());
                    ps.executeUpdate();
                }
            }
            connection.commit();
            value.setIzsniegumaId(id);
            return id;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
            connection.setTransactionIsolation(previous);
        }
    }

    public List<Izsniegums> findActive() throws SQLException {
        return query(BASE_SELECT + "WHERE i.atgriezts IS NULL ORDER BY i.termins");
    }

    public List<Izsniegums> findOverdue() throws SQLException {
        return query(BASE_SELECT + "WHERE i.atgriezts IS NULL AND i.termins < CURRENT_TIMESTAMP ORDER BY i.termins");
    }

    /** Rediģē tikai termiņu, izsnieguma vēsturi un saites nepārraksta */
    public boolean update(Izsniegums value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE izsniegums SET termins = ? WHERE izsnieguma_id = ? AND atgriezts IS NULL")) {
            ps.setTimestamp(1, value.getTermins() == null ? null : new Timestamp(value.getTermins().getTime()));
            ps.setInt(2, value.getIzsniegumaId());
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Atgriež eksemplāru transakcijā:
     *  1) atzīmē izsnieguma atgriezts = CURRENT_TIMESTAMP
     *  2) atjaunina eksemplāra statusu uz 'pieejams'
     * Atkārtota atgriešana nemaina saglabāto datumu.
     */
    public boolean atgriezt(int id) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        if (autoCommit) connection.setAutoCommit(false);
        try {
            int updated;
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE izsniegums SET atgriezts = CURRENT_TIMESTAMP "
                    + "WHERE izsnieguma_id = ? AND atgriezts IS NULL")) {
                ps.setInt(1, id);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                if (autoCommit) connection.commit();
                return false;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE eksemplars SET status = 'pieejams' WHERE eksemplara_id = "
                    + "(SELECT eksemplara_id FROM izsniegums WHERE izsnieguma_id = ?)")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            if (autoCommit) connection.commit();
            return true;
        } catch (SQLException ex) {
            if (autoCommit) connection.rollback();
            throw ex;
        } finally {
            if (autoCommit) connection.setAutoCommit(true);
        }
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM izsniegums WHERE izsnieguma_id = ? AND atgriezts IS NOT NULL")) {
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

    public void issueBook(int readerId, int copyId, String dueDateText, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        final LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(dueDateText, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Termiņam jābūt formātā GGGG-MM-DD.", ex);
        }
        if (dueDate.isBefore(LocalDate.now())) throw new IllegalArgumentException("Termiņš nevar būt pagātnē.");
        withConnection(connection -> {
            Lietotajs reader = new LietotajsDAO(connection).findById(readerId);
            Eksemplars copy = new EksemplarsDAO(connection).findById(copyId);
            if (reader == null || reader.getLoma() != Loma.READER) throw new SQLException("Izvēlētais lasītājs nav derīgs.");
            if (copy == null) throw new SQLException("Izvēlētais eksemplārs nav atrasts.");
            Izsniegums loan = new Izsniegums();
            loan.setLietotajs(reader);
            loan.setEksemplars(copy);
            loan.setTermins(Timestamp.valueOf(dueDate.atTime(23, 59, 59)));
            new IzsniegumsDAO(connection).insert(loan, actor);
            return null;
        });
    }

    public List<Izsniegums> findLoans(boolean overdueOnly, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        return withConnection(connection -> overdueOnly
                ? new IzsniegumsDAO(connection).findOverdue()
                : new IzsniegumsDAO(connection).findActive());
    }

    public void returnLoan(int loanId, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        boolean returned = withConnection(connection -> new IzsniegumsDAO(connection).atgriezt(loanId));
        if (!returned) throw new SQLException("Izsniegumu neizdevās atgriezt. Iespējams, tas jau ir atgriezts.");
    }

    /** Paziņojumu sarakstā redzamie kavētie izsniegumi atbilstoši lietotāja lomai. */
    public List<Izsniegums> findNotificationLoans(Lietotajs actor) throws SQLException {
        requireNotificationAccess(actor);
        return withConnection(c -> {
            List<Izsniegums> result = new IzsniegumsDAO(c).findOverdue();
            if (actor.getLoma() == Loma.READER) {
                result.removeIf(v -> v.getLietotajs() == null
                        || v.getLietotajs().getLietotajaId() != actor.getLietotajaId());
            }
            return result;
        });
    }

    /** Idempotenti izveido demonstrācijas datus šīs DAO atbildības tabulā. */
    void ensureInitialLoan(int userId, int copyId, Timestamp issued, Timestamp due) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM izsniegums WHERE lietotaja_id = ? AND eksemplara_id = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, copyId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return; }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO izsniegums (lietotaja_id, eksemplara_id, izsniegts, termins, atgriezts) VALUES (?, ?, ?, ?, NULL)")) {
            ps.setInt(1, userId);
            ps.setInt(2, copyId);
            ps.setTimestamp(3, issued);
            ps.setTimestamp(4, due);
            ps.executeUpdate();
        }
    }
}