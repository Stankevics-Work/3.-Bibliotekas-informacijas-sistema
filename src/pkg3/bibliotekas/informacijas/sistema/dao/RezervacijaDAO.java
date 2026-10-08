package pkg3.bibliotekas.informacijas.sistema.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pkg3.bibliotekas.informacijas.sistema.model.*;

/** Rezervacija datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class RezervacijaDAO {
    private final Connection connection;

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public RezervacijaDAO(Connection connection) { this.connection = connection; }

    private Rezervacija map(ResultSet rs) throws SQLException {
        Rezervacija value = new Rezervacija();
        value.setRezervacijasId(rs.getInt("rezervacijas_id"));
        value.setLietotajs(new LietotajsDAO(connection).findById(rs.getInt("lietotaja_id")));
        value.setGramata(new GramataDAO(connection).findById(rs.getInt("gramatas_id")));
        value.setDatums(rs.getTimestamp("datums"));
        value.setStatuss(rs.getString("statuss"));
        return value;
    }

    private List<Rezervacija> query(String sql, Object... args) throws SQLException {
        List<Rezervacija> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }

    public Rezervacija findById(int id) throws SQLException {
        List<Rezervacija> result = query("SELECT * FROM rezervacija WHERE rezervacijas_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Rezervacija> findAll() throws SQLException {
        return query("SELECT * FROM rezervacija ORDER BY rezervacijas_id");
    }

    private void bind(PreparedStatement ps, Rezervacija value) throws SQLException {
        ps.setInt(1, value.getLietotajs().getLietotajaId());
        ps.setInt(2, value.getGramata().getGramatasId());
        ps.setTimestamp(3, value.getDatums() == null ? null : new Timestamp(value.getDatums().getTime()));
        ps.setString(4, value.getStatuss());
    }

    /** Patstāvīga transakcija ar SERIALIZABLE izolāciju */
    public int insert(Rezervacija value) throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Šī metode pati pārvalda transakciju");
        int previous = connection.getTransactionIsolation();
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        connection.setAutoCommit(false);
        try {

        if (value.getLietotajs() == null) throw new SQLException("Nav norādīts lasītājs");
        Lietotajs user = new LietotajsDAO(connection).findById(value.getLietotajs().getLietotajaId());
        if (user == null || user.getLoma() != Loma.READER) throw new SQLException("Darbība atļauta tikai lasītājam");

        if (value.getGramata() == null) throw new SQLException("Nav norādīta grāmata");
        if (!query("SELECT * FROM rezervacija WHERE lietotaja_id = ? AND gramatas_id = ? AND statuss = 'aktīva'", user.getLietotajaId(), value.getGramata().getGramatasId()).isEmpty())
            throw new SQLException("Aktīva rezervācija jau pastāv");
        value.setDatums(new java.util.Date()); value.setStatuss("aktīva");

            int id;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO rezervacija (lietotaja_id, gramatas_id, datums, statuss) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, value); ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                    id = keys.getInt(1);
                }
            }
            connection.commit(); value.setRezervacijasId(id); return id;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback(); throw ex;
        } finally {
            connection.setAutoCommit(true); connection.setTransactionIsolation(previous);
        }
    }

    public List<Rezervacija> findActive() throws SQLException {
        return query("SELECT * FROM rezervacija WHERE statuss = 'aktīva' ORDER BY datums, rezervacijas_id");
    }
    public List<Rezervacija> findActiveByBook(int bookId) throws SQLException {
        return query("SELECT * FROM rezervacija WHERE gramatas_id = ? AND statuss = 'aktīva' ORDER BY datums, rezervacijas_id", bookId);
    }
    /** Aktīvu rezervāciju var atcelt, izpildi reģistrē IzsniegumsDAO */
    public boolean update(Rezervacija value) throws SQLException {
        if (!"atcelta".equals(value.getStatuss())) throw new SQLException("Ar šo metodi rezervāciju var tikai atcelt");
        try (PreparedStatement ps = connection.prepareStatement("UPDATE rezervacija SET statuss = 'atcelta' WHERE rezervacijas_id = ? AND statuss = 'aktīva'")) {
            ps.setInt(1, value.getRezervacijasId()); return ps.executeUpdate() == 1;
        }
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM rezervacija WHERE rezervacijas_id = ? AND statuss <> 'aktīva'")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }
}
