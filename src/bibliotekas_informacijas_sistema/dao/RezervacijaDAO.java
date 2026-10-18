package bibliotekas_informacijas_sistema.dao;

import java.sql.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import bibliotekas_informacijas_sistema.db.DatabaseManager;
import bibliotekas_informacijas_sistema.model.*;
import static bibliotekas_informacijas_sistema.util.PasswordUtil.*;

/** Rezervacija datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class RezervacijaDAO {
    private final Connection connection;

    /** Ērtā konstrukcija lietotnes GUI slāņa izsaukumiem; katra darbība atver savu DB savienojumu. */
    public RezervacijaDAO() { this.connection = null; }

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public RezervacijaDAO(Connection connection) { this.connection = connection; }

    public static final String STATUS_ACTIVE = "aktīva";
    public static final String STATUS_COMPLETED = "izpildīta";
    public static final String STATUS_CANCELLED = "atcelta";

    /** Atpakaļsaderīgi atpazīst arī vecajās DB versijās lietotos statusus bez diakritiskajām zīmēm. */
    public static boolean isActiveStatus(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase(Locale.ROOT);
        return s.equals("aktīva") || s.equals("aktiva") || s.equals("active");
    }

    private static String activeStatusPredicate() {
        return "LOWER(TRIM(statuss)) IN ('aktīva', 'aktiva', 'active')";
    }

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
            if (!query("SELECT * FROM rezervacija WHERE lietotaja_id = ? AND gramatas_id = ? AND "
                    + activeStatusPredicate(), user.getLietotajaId(),
                    value.getGramata().getGramatasId()).isEmpty())
                throw new SQLException("Aktīva rezervācija jau pastāv");

            value.setDatums(new java.util.Date());
            value.setStatuss(STATUS_ACTIVE);

            int id;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO rezervacija (lietotaja_id, gramatas_id, datums, statuss) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, value); ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                    id = keys.getInt(1);
                }
            }
            connection.commit();
            value.setRezervacijasId(id);
            return id;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
            connection.setTransactionIsolation(previous);
        }
    }

    public List<Rezervacija> findActive() throws SQLException {
        return query("SELECT * FROM rezervacija WHERE " + activeStatusPredicate()
                + " ORDER BY datums, rezervacijas_id");
    }

    public List<Rezervacija> findActiveByBook(int bookId) throws SQLException {
        return query("SELECT * FROM rezervacija WHERE gramatas_id = ? AND " + activeStatusPredicate()
                + " ORDER BY datums, rezervacijas_id", bookId);
    }

    /** Atzīmē aktīvu rezervāciju kā izpildītu. */
    public boolean complete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE rezervacija SET statuss = ? WHERE rezervacijas_id = ? AND "
                + activeStatusPredicate())) {
            ps.setString(1, STATUS_COMPLETED);
            ps.setInt(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    /** Aktīvu rezervāciju var atcelt, izpildi reģistrē DAO metode complete */
    public boolean update(Rezervacija value) throws SQLException {
        if (!STATUS_CANCELLED.equals(value.getStatuss()))
            throw new SQLException("Ar šo metodi rezervāciju var tikai atcelt");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE rezervacija SET statuss = ? WHERE rezervacijas_id = ? AND "
                + activeStatusPredicate())) {
            ps.setString(1, STATUS_CANCELLED);
            ps.setInt(2, value.getRezervacijasId());
            return ps.executeUpdate() == 1;
        }
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM rezervacija WHERE rezervacijas_id = ? AND NOT ("
                + activeStatusPredicate() + ")")) {
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

    public List<Rezervacija> findReservations(Lietotajs actor) throws SQLException {
        requireSignedIn(actor);
        if (actor.getLoma() != Loma.READER && !isStaff(actor))
            throw new SecurityException("Rezervācijas nav pieejamas šai lomai.");
        return withConnection(connection -> {
            List<Rezervacija> reservations = new RezervacijaDAO(connection).findAll();
            if (actor.getLoma() == Loma.READER) {
                reservations.removeIf(reservation -> reservation.getLietotajs() == null
                        || reservation.getLietotajs().getLietotajaId() != actor.getLietotajaId());
            }
            return reservations;
        });
    }

    public List<Gramata> findReservationBooks(Lietotajs actor) throws SQLException {
        requireReader(actor);
        return withConnection(connection -> {
            List<Gramata> books = new GramataDAO(connection).findAll();
            Set<Integer> alreadyReserved = new HashSet<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT gramatas_id FROM rezervacija WHERE lietotaja_id = ? AND "
                    + activeStatusPredicate())) {
                ps.setInt(1, actor.getLietotajaId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) alreadyReserved.add(rs.getInt(1));
                }
            }
            books.removeIf(book -> alreadyReserved.contains(book.getGramatasId()));
            return books;
        });
    }

    public void createReservation(int bookId, Lietotajs actor) throws SQLException {
        requireReader(actor);
        withConnection(connection -> {
            Gramata book = new GramataDAO(connection).findById(bookId);
            Lietotajs user = new LietotajsDAO(connection).findById(actor.getLietotajaId());
            if (book == null) throw new SQLException("Grāmata nav atrasta.");
            if (user == null || user.getLoma() != Loma.READER)
                throw new SecurityException("Rezervāciju var izveidot tikai lasītājs.");
            Rezervacija reservation = new Rezervacija();
            reservation.setGramata(book);
            reservation.setLietotajs(user);
            new RezervacijaDAO(connection).insert(reservation);
            return null;
        });
    }

    public void cancelReservation(int reservationId, Lietotajs actor) throws SQLException {
        requireReader(actor);
        withConnection(connection -> {
            RezervacijaDAO dao = new RezervacijaDAO(connection);
            Rezervacija reservation = dao.findById(reservationId);
            if (reservation == null || reservation.getLietotajs() == null
                    || reservation.getLietotajs().getLietotajaId() != actor.getLietotajaId()) {
                throw new SQLException("Rezervācija nav atrasta vai tā nepieder pašreizējam lasītājam.");
            }
            if (!RezervacijaDAO.isActiveStatus(reservation.getStatuss()))
                throw new SQLException("Atcelt var tikai aktīvu rezervāciju.");
            reservation.setStatuss(STATUS_CANCELLED);
            if (!dao.update(reservation)) throw new SQLException("Rezervāciju neizdevās atcelt.");
            return null;
        });
    }

    public void completeReservation(int reservationId, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        boolean completed = withConnection(connection ->
                new RezervacijaDAO(connection).complete(reservationId));
        if (!completed) throw new SQLException("Rezervāciju nevar izpildīt. Pārbaudiet, vai tā vēl ir aktīva.");
    }

    /** Paziņojumu sarakstā redzamās aktīvās rezervācijas atbilstoši lietotāja lomai. */
    public List<Rezervacija> findNotificationReservations(Lietotajs actor) throws SQLException {
        requireNotificationAccess(actor);
        return withConnection(c -> {
            List<Rezervacija> result = new RezervacijaDAO(c).findActive();
            if (actor.getLoma() == Loma.READER) {
                result.removeIf(v -> v.getLietotajs() == null
                        || v.getLietotajs().getLietotajaId() != actor.getLietotajaId());
            }
            return result;
        });
    }

    /** Idempotenti izveido demonstrācijas datus šīs DAO atbildības tabulā. */
    void ensureInitialReservation(int userId, int bookId, Timestamp date, String status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM rezervacija WHERE lietotaja_id = ? AND gramatas_id = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, bookId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return; }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO rezervacija (lietotaja_id, gramatas_id, datums, statuss) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, bookId);
            ps.setTimestamp(3, date);
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }
}
