package bibliotekas_informacijas_sistema;

import dao.*;
import db.DatabaseManager;
import model.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.*;
import static org.junit.Assert.*;
import util.PasswordUtil;

public class BoundaryAndInteractionTest {
    private Connection c;
    private Path db;
    private String before;

    @Before public void setup() throws Exception {
        before = System.getProperty("libinfo.db.path");
        db = Files.createTempDirectory("libinfo-boundaries-").resolve("db");
        System.setProperty("libinfo.db.path", db.toString());
        c = DatabaseManager.getConnection();
        DatabaseManager.initialize(c);
    }

    @After public void cleanup() throws Exception {
        if (c != null && !c.isClosed()) c.close();
        try { DriverManager.getConnection("jdbc:derby:" + db + ";shutdown=true"); }
        catch (SQLException expected) { assertEquals("08006", expected.getSQLState()); }
        if (before == null) System.clearProperty("libinfo.db.path");
        else System.setProperty("libinfo.db.path", before);
    }

    private Lietotajs user(String name, Loma role) throws Exception {
        Lietotajs v = new Lietotajs();
        v.setVards("Tests"); v.setUzvards(name);
        v.setLietotajvards(name); v.setEpasts(name + "@example.com");
        v.setParole(PasswordUtil.hash("Test123!")); v.setLoma(role);
        new LietotajsDAO(c).insert(v);
        return v;
    }

    private Gramata form(String title, int year, String isbn) {
        Gramata v = new Gramata(); v.setNosaukums(title);
        v.setIzdosanasGads(year); v.setIsbn(isbn); v.setAutors("Tests");
        return v;
    }

    private void rejected(Runnable task) {
        try { task.run(); fail("Kļūdaina ievade nedrīkst tikt pieņemta"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }

    @Test public void inputBoundariesAndEndToEndConsistency() throws Exception {
        Lietotajs staff = user("staff_bound", Loma.LIBRARIAN);
        Lietotajs r1 = user("reader_bound1", Loma.READER);
        Lietotajs r2 = user("reader_bound2", Loma.READER);
        GramataDAO books = new GramataDAO();
        EksemplarsDAO copies = new EksemplarsDAO(c);
        IzsniegumsDAO loans = new IzsniegumsDAO();
        RezervacijaDAO reservations = new RezervacijaDAO();

        // Sākumstāvoklis: 13 demonstrācijas grāmatas no schema.sql
        final int initialCount = books.searchBooks("", "Visas grāmatas", staff).size();

        rejected(() -> { try { books.saveBook(form("A", 1799, null), -1, true, 1, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { books.saveBook(form("A", 2101, null), -1, true, 1, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { books.saveBook(form(" ", 1800, null), -1, true, 1, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { books.saveBook(form("X".repeat(201), 1800, null), -1, true, 1, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { books.saveBook(form("A", 1800, null), -1, true, 101, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { books.saveBook(form("A", 1800, null), -1, true, -1, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { books.saveBook(form("A", 1800, null), -1, true, 0, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        assertEquals("Kļūdainai ievadei nedrīkst radīt DB ierakstus",
                initialCount, books.searchBooks("", "Visas grāmatas", staff).size());   // <-- izlabots

        Gramata two = books.saveBook(form("Robežtestu grāmata", 1800, "ISBN-QA-123"), -1, true, 2, staff);
        assertEquals(2, copies.findByBook(two.getGramatasId()).size());
        assertTrue(new GramataDAO(c).findById(two.getGramatasId()).isPieejama());
        Gramata none = books.saveBook(form("Bez eksemplāriem", 2100, null), -1, false, 0, staff);
        assertTrue(copies.findByBook(none.getGramatasId()).isEmpty());
        assertFalse(new GramataDAO(c).findById(none.getGramatasId()).isPieejama());
        assertTrue(books.deleteBook(none.getGramatasId(), staff));
        assertNull(new GramataDAO(c).findById(none.getGramatasId()));
        try { books.saveBook(form("Dublēts ISBN", 2000, "ISBN-QA-123"), -1, true, 2, staff);
              fail("Dublēta ISBN transakcija jāatceļ"); }
        catch (SQLException expected) { }
        assertEquals(initialCount + 1, books.searchBooks("", "Visas grāmatas", staff).size());   // <-- izlabots
        assertEquals(2, copies.findByBook(two.getGramatasId()).size());
        List<Eksemplars> copiesForBook = copies.findByBook(two.getGramatasId());
        int firstCopyId = copiesForBook.get(0).getEksemplaraId();
        int secondCopyId = copiesForBook.get(1).getEksemplaraId();

        rejected(() -> { try { loans.issueBook(r1.getLietotajaId(), firstCopyId, "2026-50-50", staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { loans.issueBook(r1.getLietotajaId(), firstCopyId, "2027-02-30", staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { loans.issueBook(r1.getLietotajaId(), firstCopyId, null, staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { loans.issueBook(r1.getLietotajaId(), firstCopyId,
              LocalDate.now().minusDays(1).toString(), staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        rejected(() -> { try { loans.issueBook(r1.getLietotajaId(), firstCopyId,
              LocalDate.now().plusYears(2).toString(), staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        assertEquals(0, loans.findMyLoans(r1).size());
        loans.issueBook(r1.getLietotajaId(), firstCopyId, LocalDate.now().plusDays(7).toString(), staff);
        assertEquals(1, loans.findMyLoans(r1).size());
        assertEquals(0, loans.findMyLoans(r2).size());
        assertEquals("izsniegts", copies.findById(firstCopyId).getStatus());
        rejected(() -> { try { reservations.createReservation(two.getGramatasId(), r1); }
            catch (SQLException e) { throw new AssertionError(e); } });
        assertFalse(reservations.findReservationBooks(r1).stream()
                .anyMatch(v -> v.getGramatasId() == two.getGramatasId()));
        try { books.deleteBook(two.getGramatasId(), staff); fail("Izsniegtu grāmatu nedrīkst dzēst"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("izsniegumu")); }

        reservations.createReservation(two.getGramatasId(), r2);
        Rezervacija booked = reservations.findReservations(r2).stream()
                .filter(v -> v.getGramata().getGramatasId() == two.getGramatasId())
                .findFirst().orElseThrow();
        try { new RezervacijaDAO(c).complete(booked.getRezervacijasId()); fail("Nevar izpildīt bez izsniegšanas"); }
        catch (SQLException expected) { }
        rejected(() -> { try { reservations.fulfillReservation(booked.getRezervacijasId(), firstCopyId,
                LocalDate.now().plusDays(7).toString(), staff); }
            catch (SQLException e) { throw new AssertionError(e); } });
        assertEquals("aktīva", new RezervacijaDAO(c).findById(booked.getRezervacijasId()).getStatuss());
        reservations.fulfillReservation(booked.getRezervacijasId(), secondCopyId,
                LocalDate.now().plusDays(7).toString(), staff);
        assertEquals("izpildīta", new RezervacijaDAO(c).findById(booked.getRezervacijasId()).getStatuss());
        assertEquals(1, loans.findMyLoans(r2).size());
        assertFalse(new GramataDAO(c).findById(two.getGramatasId()).isPieejama());
        // Rēķinām tikai mūsu grāmatas aktīvos izsniegumus (seed satur 1 aktīvo demo izsniegumu)
        assertEquals(2, new IzsniegumsDAO(c).findActive().stream()
                .filter(l -> l.getEksemplars() != null
                        && l.getEksemplars().getGramata() != null
                        && l.getEksemplars().getGramata().getGramatasId() == two.getGramatasId())
                .count());
        assertTrue(reservations.findReservations(r1).isEmpty());
        assertEquals(1, reservations.findReservations(r2).size());
        loans.returnLoan(loans.findMyLoans(r1).get(0).getIzsniegumaId(), staff);
        assertTrue(loans.findMyLoans(r1).isEmpty());
        assertEquals(1, loans.findMyLoans(r2).size());
        assertTrue(new GramataDAO(c).findById(two.getGramatasId()).isPieejama());
    }
}