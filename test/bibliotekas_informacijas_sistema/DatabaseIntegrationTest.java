package bibliotekas_informacijas_sistema;



import dao.IzsniegumsDAO;
import dao.GramataDAO;
import dao.EksemplarsDAO;
import dao.LietotajsDAO;
import dao.RezervacijaDAO;
import model.Lietotajs;
import model.Loma;
import model.Eksemplars;
import model.Gramata;
import model.Rezervacija;
import model.Izsniegums;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Date;
import db.DatabaseManager;
import util.PasswordUtil;
import org.junit.*;
import static org.junit.Assert.*;

/** Pārbauda īstu Derby datubāzi, DAO un datu saglabāšanu */
public class DatabaseIntegrationTest {
    private Connection connection;
    private Path db;
    private String previousPath;
    private Lietotajs actor;

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

    private Lietotajs actor() throws SQLException {
        if (actor == null) {
            actor = new Lietotajs();
            actor.setVards("Testa"); actor.setUzvards("Bibliotekārs");
            actor.setLietotajvards("test_bibliotekars"); actor.setEpasts("test_bibliotekars@example.com");
            actor.setParole(PasswordUtil.hash("Test123!")); actor.setLoma(Loma.LIBRARIAN);
            new LietotajsDAO(connection).insert(actor);
        }
        return actor;
    }

    private Izsniegums loan(Lietotajs reader, Eksemplars copy) throws SQLException {
        Izsniegums value = new Izsniegums(); value.setLietotajs(reader);
        value.setEksemplars(copy); value.setTermins(new Date(System.currentTimeMillis() + 86400000L));
        new IzsniegumsDAO(connection).insert(value, actor()); return value;
    }

    @Test public void legacyPlaintextPasswordsAreMigratedToBcrypt() throws Exception {
        LietotajsDAO users = new LietotajsDAO(connection);
        String[][] credentials = {
            {"admin", "admin123"},
            {"bibliotekars", "Test123!"},
            {"lasitajs", "Test123!"},
            {"autors", "Test123!"}
        };
        for (String[] credential : credentials) {
            Lietotajs user = users.findByUsername(credential[0]);
            assertNotNull("Sākotnējam kontam jābūt pieejamam: " + credential[0], user);
            assertTrue("Parolei jābūt migrētai uz BCrypt: " + credential[0],
                    PasswordUtil.isBcryptHash(user.getParole()));
            assertTrue("Vecajai parolei jāturpina darboties: " + credential[0],
                    PasswordUtil.verify(credential[1], user.getParole()));
        }
    }

    @Test public void loginUpgradesLegacyPlaintextPasswordEvenIfStartupMigrationWasSkipped() throws Exception {
        // Simulē vecu datubāzi, kuras lietotāja parole joprojām ir atklātā tekstā.
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE lietotajs SET parole = ? WHERE lietotajvards = ?")) {
            ps.setString(1, "admin123");
            ps.setString(2, "admin");
            assertEquals(1, ps.executeUpdate());
        }

        Lietotajs authenticated = new LietotajsDAO().authenticate("ADMIN", "admin123");
        assertNotNull(authenticated);
        assertEquals("admin", authenticated.getLietotajvards());
        assertNull("Sesijas objektā paroli nedrīkst atstāt", authenticated.getParole());

        Lietotajs saved = new LietotajsDAO(connection).findByUsername("admin");
        assertTrue("Pēc pieteikšanās vecajai parolei jābūt pārveidotai par BCrypt",
                PasswordUtil.isBcryptHash(saved.getParole()));
        assertTrue(PasswordUtil.verify("admin123", saved.getParole()));
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

    @Test public void startupKeepsChangedAdminPasswordAndSeedDataStable() throws Exception {
        LietotajsDAO users = new LietotajsDAO();
        users.initializeDatabase();
        Lietotajs admin = new LietotajsDAO(connection).findByUsername("admin");
        assertNotNull("Sākotnējā administratora kontam jābūt izveidotam", admin);

        String changedPassword = "AdminChanged123!";
        admin.setParole(PasswordUtil.hash(changedPassword));
        assertTrue(new LietotajsDAO(connection).update(admin));

        // Atkārtota inicializācija nedrīkst atiestatīt paroli vai dublēt demonstrācijas datus.
        users.initializeDatabase();
        Lietotajs saved = new LietotajsDAO(connection).findByUsername("admin");
        assertTrue("Administratora nomainītajai parolei jāsaglabājas", PasswordUtil.verify(changedPassword, saved.getParole()));
        assertEquals("Inicializācijai nevajadzētu dublēt sākotnējos izsniegumus", 1, new IzsniegumsDAO(connection).findAll().size());
        assertEquals("Inicializācijai nevajadzētu dublēt sākotnējās rezervācijas", 1, new RezervacijaDAO(connection).findAll().size());

        Lietotajs authenticated = users.authenticate("admin", changedPassword);
        assertNull("Autentificētās sesijas objektā nav jāglabā paroles jaucējvērtība", authenticated.getParole());
    }

    @Test public void startupDoesNotRecreateRenamedSeedAccounts() throws Exception {
        LietotajsDAO users = new LietotajsDAO();
        users.initializeDatabase();
        Lietotajs admin = new LietotajsDAO(connection).findByUsername("admin");
        assertNotNull(admin);
        admin.setLietotajvards("mainitais_admin");
        assertTrue(new LietotajsDAO(connection).update(admin));

        users.initializeDatabase();
        assertNull("Pārdēvēto kontu nedrīkst aizvietot ar noklusēto administratoru",
                new LietotajsDAO(connection).findByUsername("admin"));
        Lietotajs renamed = new LietotajsDAO(connection).findByUsername("mainitais_admin");
        assertNotNull(renamed);
        assertEquals(Loma.ADMIN, renamed.getLoma());
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM lietotajs WHERE loma = 'ADMIN'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("Pēc atkārtotas inicializācijas nedrīkst dublēt administratoru", 1, rs.getInt(1));
        }
    }

    @Test public void cannotRemoveOwnAdminRoleAndMalformedPasswordHashesFailClosed() throws Exception {
        LietotajsDAO users = new LietotajsDAO();
        users.initializeDatabase();
        Lietotajs admin = new LietotajsDAO(connection).findByUsername("admin");
        assertNotNull(admin);
        try {
            users.changeUserRole(admin.getLietotajaId(), Loma.READER, admin);
            fail("Administratoram nedrīkst atņemt pašam savu lomu");
        } catch (IllegalArgumentException expected) { }
        Lietotajs selfEdit = new Lietotajs();
        selfEdit.setVards(admin.getVards()); selfEdit.setUzvards(admin.getUzvards());
        selfEdit.setLietotajvards(admin.getLietotajvards()); selfEdit.setEpasts(admin.getEpasts());
        selfEdit.setLoma(Loma.READER);
        try {
            users.saveManagedUser(selfEdit, admin.getLietotajaId(), "", admin);
            fail("Administratoram nedrīkst mainīt pašam savu lomu ar lietotāja rediģēšanas formu");
        } catch (IllegalArgumentException expected) { }
        assertFalse(PasswordUtil.verify("anything", "not-a-bcrypt-hash"));
        assertFalse(PasswordUtil.verify(null, "$2a$12$invalid"));
        try {
            PasswordUtil.hash("   ");
            fail("Tukšu vai tikai atstarpju paroli nedrīkst jaukt");
        } catch (IllegalArgumentException expected) { }
        try {
            PasswordUtil.hash("a".repeat(73));
            fail("BCrypt ievades garums nedrīkst pārsniegt 72 UTF-8 baitus");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void librarianCannotOverwriteAdministratorThroughEditForm() throws Exception {
        LietotajsDAO users = new LietotajsDAO();
        users.initializeDatabase();
        Lietotajs admin = new LietotajsDAO(connection).findByUsername("admin");
        String originalHash = admin.getParole();
        String originalName = admin.getVards();
        admin.setVards("Changed");
        try {
            users.saveManagedUser(admin, admin.getLietotajaId(), "Replacement123!", actor());
            fail("Bibliotekārs nedrīkst pārrakstīt administratora kontu");
        } catch (SecurityException expected) { }
        Lietotajs unchanged = new LietotajsDAO(connection).findById(admin.getLietotajaId());
        assertEquals(originalHash, unchanged.getParole());
        assertEquals(originalName, unchanged.getVards());
        Lietotajs reader = reader("editable_reader");
        reader.setVards("Updated");
        users.saveManagedUser(reader, reader.getLietotajaId(), "", actor());
        assertEquals("Updated", new LietotajsDAO(connection).findById(reader.getLietotajaId()).getVards());
    }

    @Test public void booksWithoutIsbnCanBeSavedAndUpdatedIndependently() throws Exception {
        GramataDAO books = new GramataDAO();
        Gramata first = new Gramata();
        first.setNosaukums("  First  "); first.setIsbn("");
        Gramata second = new Gramata();
        second.setNosaukums("Second"); second.setIsbn("  ");
        Gramata savedFirst = books.saveBook(first, -1, false, actor());
        Gramata savedSecond = books.saveBook(second, -1, false, actor());
        assertNull(savedFirst.getIsbn());
        assertNull(savedSecond.getIsbn());
        assertEquals("First", savedFirst.getNosaukums());
        second.setNosaukums("Second updated");
        books.saveBook(second, savedSecond.getGramatasId(), false, actor());
        GramataDAO query = new GramataDAO(connection);
        assertEquals("Second updated", query.findById(savedSecond.getGramatasId()).getNosaukums());
        assertEquals("First", query.findById(savedFirst.getGramatasId()).getNosaukums());
    }

    @Test public void crudAndLoanLifecycle() throws Exception {
        Lietotajs user = reader("crud_reader");
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

    @Test public void existingFiveDaosDriveCompleteWorkflow() throws Exception {
        LietotajsDAO userDao = new LietotajsDAO();
        GramataDAO bookDao = new GramataDAO();
        EksemplarsDAO copyDao = new EksemplarsDAO();
        IzsniegumsDAO loanDao = new IzsniegumsDAO();
        RezervacijaDAO reservationDao = new RezervacijaDAO();

        userDao.initializeDatabase();
        // Emulē vecāku DB versiju: startēšanas laikā atjauno tikai nolasīto paziņojumu tabulu.
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE pazinojums_izlasits");
        }
        userDao.initializeDatabase();

        Lietotajs librarian = new LietotajsDAO(connection).findByUsername("bibliotekars");
        assertNotNull("Sākotnējā bibliotekāra kontam jābūt izveidotam", librarian);
        Lietotajs reader = userDao.registerUser("Testa", "Lasītājs", "dao_workflow_reader",
                "Password123!", "Password123!", "dao_workflow_reader@example.com");
        assertNotNull(reader);
        assertEquals(reader.getLietotajaId(), userDao.authenticate("dao_workflow_reader", "Password123!").getLietotajaId());

        Gramata form = new Gramata();
        form.setNosaukums("DAO darbplūsmas grāmata");
        form.setAutors("Māra Liepa");
        form.setKategorija("Testi");
        form.setApraksts("Pārbauda darbības caur piecām esošajām DAO klasēm.");
        form.setIzdosanasGads(java.time.LocalDate.now().getYear());
        Gramata saved = bookDao.saveBook(form, -1, true, librarian);
        assertNotNull(saved);
        assertTrue("Jaunajai grāmatai jābūt pieejamai pēc eksemplāra pievienošanas", saved.isPieejama());

        Eksemplars copy = null;
        for (Eksemplars candidate : copyDao.findAvailableCopies(librarian)) {
            if (candidate.getGramata() != null && candidate.getGramata().getGramatasId() == saved.getGramatasId()) {
                copy = candidate;
                break;
            }
        }
        assertNotNull("DAO jāsaglabā un jāatgriež eksemplārs", copy);
        loanDao.issueBook(reader.getLietotajaId(), copy.getEksemplaraId(),
                java.time.LocalDate.now().plusDays(7).toString(), librarian);

        Izsniegums activeLoan = null;
        for (Izsniegums candidate : loanDao.findLoans(false, librarian)) {
            if (candidate.getEksemplars() != null && candidate.getEksemplars().getEksemplaraId() == copy.getEksemplaraId()) {
                activeLoan = candidate;
                break;
            }
        }
        assertNotNull("Izsniegšanai jābūt redzamai aktīvo izsniegumu sarakstā", activeLoan);
        loanDao.returnLoan(activeLoan.getIzsniegumaId(), librarian);
        for (Izsniegums candidate : loanDao.findLoans(false, librarian)) {
            assertFalse("Atgrieztais eksemplārs vairs nedrīkst būt aktīvs", candidate.getIzsniegumaId() == activeLoan.getIzsniegumaId());
        }

        reservationDao.createReservation(saved.getGramatasId(), reader);
        Rezervacija created = null;
        for (Rezervacija reservation : reservationDao.findReservations(reader)) {
            if (reservation.getGramata() != null && reservation.getGramata().getGramatasId() == saved.getGramatasId()) {
                created = reservation;
                break;
            }
        }
        assertNotNull("Jauna rezervācija jāsaglabā Derby datubāzē", created);
        assertEquals("aktīva", created.getStatuss());
        for (Gramata selectable : reservationDao.findReservationBooks(reader)) {
            assertNotEquals("Grāmatu ar jau aktīvu rezervāciju nedrīkst piedāvāt otrreiz",
                    saved.getGramatasId(), selectable.getGramatasId());
        }
        userDao.markNotificationRead("reservation", created.getRezervacijasId(), reader);
        assertTrue("Izlasīšanas statuss jāsaglabā Derby un jāielādē atkārtoti",
                userDao.findReadNotificationKeys(reader).contains("reservation:" + created.getRezervacijasId()));

        // Vecākās instalācijās aktīvais statuss var būt saglabāts bez diakritiskās zīmes.
        assertTrue("DAO atpazīst arī veco aktiva statusu", RezervacijaDAO.isActiveStatus("aktiva"));
        reservationDao.cancelReservation(created.getRezervacijasId(), reader);
        boolean cancellationPersisted = false;
        for (Rezervacija reservation : reservationDao.findReservations(reader)) {
            if (reservation.getRezervacijasId() == created.getRezervacijasId()) {
                cancellationPersisted = "atcelta".equals(reservation.getStatuss());
                break;
            }
        }
        assertTrue("Rezervācijas atcelšana jāsaglabā datubāzē", cancellationPersisted);
        boolean selectableAgain = false;
        for (Gramata selectable : reservationDao.findReservationBooks(reader)) {
            if (selectable.getGramatasId() == saved.getGramatasId()) selectableAgain = true;
        }
        assertTrue("Pēc atcelšanas grāmata atkal ir pieejama jaunai rezervācijai", selectableAgain);
        assertTrue("Grāmatu meklēšanai jāizmanto DAO", !bookDao.searchBooks("DAO darbplūsmas grāmata", "Visas grāmatas", librarian).isEmpty());
        assertTrue("Lietotāju meklēšanai jāizmanto DAO", !userDao.searchUsers("dao_workflow_reader", librarian).isEmpty());
        Lietotajs author = new LietotajsDAO(connection).findByUsername("autors");
        try {
            loanDao.findNotificationLoans(author);
            fail("Autora lomai nevajadzētu redzēt citu lietotāju paziņojumus");
        } catch (SecurityException expected) { }
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
