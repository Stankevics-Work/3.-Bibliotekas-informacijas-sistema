package pkg3.bibliotekas.informacijas.sistema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Date;
import pkg3.bibliotekas.informacijas.sistema.dao.*;
import pkg3.bibliotekas.informacijas.sistema.db.DatabaseManager;
import pkg3.bibliotekas.informacijas.sistema.model.*;
import pkg3.bibliotekas.informacijas.sistema.util.PasswordUtil;
import org.junit.*;
import static org.junit.Assert.*;

/** Pārbauda īstu Derby datubāzi, DAO un datu saglabāšanu */
public class DatabaseIntegrationTest {
    private Connection connection;
    private Path db;
    private String previousPath;

    @Before public void prepare() throws Exception {
        db = Files.createTempDirectory("libinfo-test-").resolve("db");
        previousPath = System.getProperty("libinfo.db.path");
        System.setProperty("libinfo.db.path", db.toString());
        connection = DatabaseManager.getConnection();
        DatabaseManager.initialize(connection);
    }

    @After public void close() throws Exception {
        if (connection != null && !connection.isClosed()) connection.close();
        if (db != null) {
            try { DriverManager.getConnection("jdbc:derby:" + db + ";shutdown=true"); }
            catch (SQLException ex) { if (!"08006".equals(ex.getSQLState())) throw ex; }
        }
        if (previousPath == null) System.clearProperty("libinfo.db.path");
        else System.setProperty("libinfo.db.path", previousPath);
    }

    private Lietotajs reader(String name) throws SQLException {
        Lietotajs user = new Lietotajs();
        user.setVards("Janis"); user.setUzvards("Berzins");
        user.setLietotajvards(name); user.setEpasts(name + "@example.com");
        user.setParole(PasswordUtil.hash("Test123!")); user.setLoma(Loma.READER);
        new LietotajsDAO(connection).insert(user);
        return user;
    }

    private Gramata book(String name) throws SQLException {
        Gramata value = new Gramata(); value.setNosaukums(name);
        value.setAutors("Mara Liepa"); value.setKategorija("Macibu literatura");
        new GramataDAO(connection).insert(value); return value;
    }

    private Eksemplars copy(Gramata book) throws SQLException {
        Eksemplars value = new Eksemplars(); value.setGramata(book);
        value.setBiblioteka("DTTT biblioteka");
        new EksemplarsDAO(connection).insert(value); return value;
    }

    private Izsniegums loan(Lietotajs reader, Eksemplars copy) throws SQLException {
        Izsniegums value = new Izsniegums(); value.setLietotajs(reader);
        value.setEksemplars(copy); value.setTermins(new Date(System.currentTimeMillis() + 86400000L));
        new IzsniegumsDAO(connection).insert(value); return value;
    }

    @Test public void initializationAndPersistence() throws Exception {
        assertTrue(DatabaseManager.testConnection(connection));
        DatabaseManager.initialize(connection);
        Gramata value = book("Saglabata gramata");
        connection.close();
        // Pilna datubāzes apturēšana pārbauda saglabāšanu arī pēc dzinēja restartēšanas
        try { DriverManager.getConnection("jdbc:derby:" + db + ";shutdown=true"); }
        catch (SQLException ex) { assertEquals("08006", ex.getSQLState()); }
        connection = DatabaseManager.getConnection();
        DatabaseManager.initialize(connection);
        assertEquals("Saglabata gramata", new GramataDAO(connection).findById(value.getGramatasId()).getNosaukums());
        assertEquals(1, new GramataDAO(connection).findAll().size());
        assertNull(new GramataDAO(connection).findById(Integer.MAX_VALUE));
    }

    @Test public void crudAndLoanLifecycle() throws Exception {
        Lietotajs user = reader("lasitajs");
        LietotajsDAO users = new LietotajsDAO(connection);
        assertTrue(PasswordUtil.verify("Test123!", users.findByUsername("lasitajs").getParole()));
        user.setVards("Peteris"); assertTrue(users.update(user));
        assertEquals("Peteris", users.findById(user.getLietotajaId()).getVards());
        Gramata value = book("Java pamati");
        GramataDAO books = new GramataDAO(connection);
        assertNull(books.findById(value.getGramatasId()).getIzdosanasGads());
        assertFalse(books.findById(value.getGramatasId()).isPieejama());
        value.setIzdosanasGads(2025); value.setApraksts("Macibu gramata"); assertTrue(books.update(value));
        assertEquals(1, books.search("JAVA").size());
        Eksemplars copy = copy(value);
        EksemplarsDAO copies = new EksemplarsDAO(connection);
        assertTrue(books.findById(value.getGramatasId()).isPieejama());
        assertEquals(1, books.filter("Macibu literatura", 2025, true).size());
        assertEquals(1, copies.findByBook(value.getGramatasId()).size());
        copy.setBiblioteka("Centrala biblioteka"); assertTrue(copies.update(copy));
        assertEquals("Centrala biblioteka", copies.findById(copy.getEksemplaraId()).getBiblioteka());
        Izsniegums issued = loan(user, copy);
        IzsniegumsDAO loans = new IzsniegumsDAO(connection);
        assertEquals(1, loans.findActive().size());
        assertEquals(user.getLietotajaId(), loans.findById(issued.getIzsniegumaId()).getLietotajs().getLietotajaId());
        assertFalse(books.findById(value.getGramatasId()).isPieejama());
        assertEquals("izsniegts", copies.findById(copy.getEksemplaraId()).getStatus());
        assertFalse(copies.delete(copy.getEksemplaraId()));
        assertFalse(loans.delete(issued.getIzsniegumaId()));
        try { loan(user, copy); fail("Nedrīkst izsniegt divreiz"); } catch (SQLException expected) { }
        assertEquals(1, loans.findAll().size()); assertTrue(connection.getAutoCommit());
        issued.setTermins(new Date(System.currentTimeMillis() + 172800000L)); assertTrue(loans.update(issued));
        assertTrue(loans.atgriezt(issued.getIzsniegumaId()));
        Date returned = loans.findById(issued.getIzsniegumaId()).getAtgriezts();
        assertFalse(loans.atgriezt(issued.getIzsniegumaId()));
        assertEquals(returned, loans.findById(issued.getIzsniegumaId()).getAtgriezts());
        assertTrue(books.findById(value.getGramatasId()).isPieejama());
        try { users.delete(user.getLietotajaId()); fail("Ārējai atslēgai jāaizsargā vēsture"); } catch (SQLException expected) { }
        assertTrue(loans.delete(issued.getIzsniegumaId()));
        assertTrue(copies.delete(copy.getEksemplaraId()));
        assertFalse(books.findById(value.getGramatasId()).isPieejama());
        assertTrue(books.delete(value.getGramatasId())); assertTrue(users.delete(user.getLietotajaId()));
    }

    @Test public void reservationsAndQueue() throws Exception {
        Lietotajs first = reader("pirmais"), second = reader("otrais");
        Gramata book = book("Rezerveta gramata"); Eksemplars copy = copy(book);
        RezervacijaDAO dao = new RezervacijaDAO(connection);
        Rezervacija r1 = new Rezervacija(); r1.setLietotajs(first); r1.setGramata(book); dao.insert(r1);
        Rezervacija r2 = new Rezervacija(); r2.setLietotajs(second); r2.setGramata(book); dao.insert(r2);
        assertEquals(2, dao.findActive().size());
        assertEquals(first.getLietotajaId(), dao.findActiveByBook(book.getGramatasId()).get(0).getLietotajs().getLietotajaId());
        try { dao.insert(r1); fail("Atkārtota rezervācija nav atļauta"); } catch (SQLException expected) { }
        try { loan(second, copy); fail("Jāievēro rezervāciju rinda"); } catch (SQLException expected) { }
        assertTrue(new GramataDAO(connection).findById(book.getGramatasId()).isPieejama());
        loan(first, copy);
        assertEquals("izpildīta", dao.findById(r1.getRezervacijasId()).getStatuss());
        r2.setStatuss("atcelta"); assertTrue(dao.update(r2));
        assertTrue(dao.findActive().isEmpty());
        assertTrue(dao.delete(r1.getRezervacijasId())); assertTrue(dao.delete(r2.getRezervacijasId()));
    }

    @Test public void overdueAndConstraints() throws Exception {
        Lietotajs user = reader("kavetajs");
        Gramata book = book("Kaveta gramata"); Eksemplars copy = copy(book);
        Izsniegums loan = loan(user, copy);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE izsniegums SET izsniegts = ?, termins = ? WHERE izsnieguma_id = ?")) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - 172800000L));
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis() - 86400000L));
            ps.setInt(3, loan.getIzsniegumaId()); ps.executeUpdate();
        }
        IzsniegumsDAO loans = new IzsniegumsDAO(connection);
        assertEquals(1, loans.findOverdue().size()); assertTrue(loans.findOverdue().get(0).isKavejies());
        loans.atgriezt(loan.getIzsniegumaId()); assertTrue(loans.findOverdue().isEmpty());
        book.setIzdosanasGads(1700);
        try { new GramataDAO(connection).update(book); fail("Jāpārbauda izdošanas gads"); } catch (SQLException expected) { }
        assertNull(new GramataDAO(connection).findById(book.getGramatasId()).getIzdosanasGads());
        user.setParole("plain-text");
        try { new LietotajsDAO(connection).update(user); fail("Jāglabā BCrypt"); } catch (SQLException expected) { }
    }
}
