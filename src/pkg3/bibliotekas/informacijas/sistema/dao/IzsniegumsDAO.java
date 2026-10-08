package pkg3.bibliotekas.informacijas.sistema.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pkg3.bibliotekas.informacijas.sistema.model.*;

/** Izsniegums datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class IzsniegumsDAO {
    private final Connection connection;

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public IzsniegumsDAO(Connection connection) { this.connection = connection; }

    private Izsniegums map(ResultSet rs) throws SQLException {
        Izsniegums value = new Izsniegums();
        value.setIzsniegumaId(rs.getInt("izsnieguma_id"));
        value.setLietotajs(new LietotajsDAO(connection).findById(rs.getInt("lietotaja_id")));
        value.setEksemplars(new EksemplarsDAO(connection).findById(rs.getInt("eksemplara_id")));
        value.setIzsniegts(rs.getTimestamp("izsniegts"));
        value.setTermins(rs.getTimestamp("termins"));
        value.setAtgriezts(rs.getTimestamp("atgriezts"));
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
        List<Izsniegums> result = query("SELECT * FROM izsniegums WHERE izsnieguma_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Izsniegums> findAll() throws SQLException {
        return query("SELECT * FROM izsniegums ORDER BY izsnieguma_id");
    }

    private void bind(PreparedStatement ps, Izsniegums value) throws SQLException {
        ps.setInt(1, value.getLietotajs().getLietotajaId());
        ps.setInt(2, value.getEksemplars().getEksemplaraId());
        ps.setTimestamp(3, value.getIzsniegts() == null ? null : new Timestamp(value.getIzsniegts().getTime()));
        ps.setTimestamp(4, value.getTermins() == null ? null : new Timestamp(value.getTermins().getTime()));
        ps.setTimestamp(5, value.getAtgriezts() == null ? null : new Timestamp(value.getAtgriezts().getTime()));
    }

    /** Patstāvīga transakcija ar SERIALIZABLE izolāciju */
    public int insert(Izsniegums value) throws SQLException {
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
        if (!query("SELECT * FROM izsniegums WHERE eksemplara_id = ? AND atgriezts IS NULL", copy.getEksemplaraId()).isEmpty())
            throw new SQLException("Eksemplārs jau ir izsniegts");
        List<Rezervacija> queue = new RezervacijaDAO(connection).findActiveByBook(copy.getGramata().getGramatasId());
        if (!queue.isEmpty() && queue.get(0).getLietotajs().getLietotajaId() != user.getLietotajaId())
            throw new SQLException("Grāmatu gaida cits lasītājs");
        value.setIzsniegts(new java.util.Date()); value.setAtgriezts(null);

            int id;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO izsniegums (lietotaja_id, eksemplara_id, izsniegts, termins, atgriezts) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, value); ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                    id = keys.getInt(1);
                }
            }

            if (!queue.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement("UPDATE rezervacija SET statuss = 'izpildīta' WHERE rezervacijas_id = ?")) {
                    ps.setInt(1, queue.get(0).getRezervacijasId()); ps.executeUpdate();
                }
            }
            connection.commit(); value.setIzsniegumaId(id); return id;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback(); throw ex;
        } finally {
            connection.setAutoCommit(true); connection.setTransactionIsolation(previous);
        }
    }

    public List<Izsniegums> findActive() throws SQLException {
        return query("SELECT * FROM izsniegums WHERE atgriezts IS NULL ORDER BY termins");
    }
    public List<Izsniegums> findOverdue() throws SQLException {
        return query("SELECT * FROM izsniegums WHERE atgriezts IS NULL AND termins < CURRENT_TIMESTAMP ORDER BY termins");
    }
    /** Rediģē tikai termiņu, izsnieguma vēsturi un saites nepārraksta */
    public boolean update(Izsniegums value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE izsniegums SET termins = ? WHERE izsnieguma_id = ? AND atgriezts IS NULL")) {
            ps.setTimestamp(1, value.getTermins() == null ? null : new Timestamp(value.getTermins().getTime()));
            ps.setInt(2, value.getIzsniegumaId()); return ps.executeUpdate() == 1;
        }
    }
    /** Atgriež eksemplāru, atkārtota atgriešana nemaina saglabāto datumu */
    public boolean atgriezt(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE izsniegums SET atgriezts = CURRENT_TIMESTAMP WHERE izsnieguma_id = ? AND atgriezts IS NULL")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM izsniegums WHERE izsnieguma_id = ? AND atgriezts IS NOT NULL")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }
}
