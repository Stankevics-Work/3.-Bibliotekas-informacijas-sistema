package dao;

import model.Gramata;
import model.Lietotajs;
import model.Loma;
import model.Eksemplars;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import db.DatabaseManager;
import static util.PasswordUtil.*;

/** Gramata datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class GramataDAO {
    private final Connection connection;

    /** Ērtā konstrukcija lietotnes GUI slāņa izsaukumiem; katra darbība atver savu DB savienojumu. */
    public GramataDAO() { this.connection = null; }

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public GramataDAO(Connection connection) { this.connection = connection; }

    private Gramata map(ResultSet rs) throws SQLException {
        Gramata value = new Gramata();
        value.setGramatasId(rs.getInt("gramatas_id"));
        value.setNosaukums(rs.getString("nosaukums"));
        value.setApraksts(rs.getString("apraksts"));
        value.setIzdosanasGads((Integer) rs.getObject("izdosanas_gads"));
        value.setIsbn(rs.getString("isbn"));
        value.setKategorija(rs.getString("kategorija"));
        value.setAutors(rs.getString("autors"));
        value.setPieejamiba(rs.getBoolean("pieejamiba"));
        return value;
    }

    private List<Gramata> query(String sql, Object... args) throws SQLException {
        List<Gramata> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }

    public Gramata findById(int id) throws SQLException {
        List<Gramata> result = query("SELECT * FROM gramata WHERE gramatas_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Gramata> findAll() throws SQLException {
        return query("SELECT * FROM gramata ORDER BY gramatas_id");
    }

    /** Piesaista visus 7 laukus, ieskaitot pieejamiba. */
    private void bind(PreparedStatement ps, Gramata value) throws SQLException {
        ps.setString(1, value.getNosaukums());
        ps.setString(2, value.getApraksts());
        if (value.getIzdosanasGads() == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, value.getIzdosanasGads());
        ps.setString(4, value.getIsbn());
        ps.setString(5, value.getKategorija());
        ps.setString(6, value.getAutors());
        ps.setBoolean(7, value.isPieejama());
    }

    public int insert(Gramata value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO gramata (nosaukums, apraksts, izdosanas_gads, isbn, kategorija, autors, pieejamiba) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, value); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                value.setGramatasId(keys.getInt(1)); return value.getGramatasId();
            }
        }
    }

    public boolean update(Gramata value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE gramata SET nosaukums = ?, apraksts = ?, izdosanas_gads = ?, isbn = ?, "
                + "kategorija = ?, autors = ?, pieejamiba = ? WHERE gramatas_id = ?")) {
            bind(ps, value); ps.setInt(8, value.getGramatasId());
            return ps.executeUpdate() == 1;
        }
    }

    public List<Gramata> search(String text) throws SQLException {
        String pattern = "%" + (text == null ? "" : text) + "%";
        return query("SELECT * FROM gramata WHERE LOWER(nosaukums) LIKE LOWER(?) OR LOWER(autors) LIKE LOWER(?) OR isbn LIKE ? ORDER BY nosaukums", pattern, pattern, pattern);
    }

    /** Meklē grāmatas pēc teksta un saskarnē izvēlētā filtra. */
    public List<Gramata> search(String text, String filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM gramata WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (filter != null && !"Visas grāmatas".equals(filter)) {
            if ("Pieejamās".equals(filter)) {
                sql.append(" AND pieejamiba = TRUE");
            } else if ("Nepieejamās".equals(filter)) {
                sql.append(" AND pieejamiba = FALSE");
            } else {
                sql.append(" AND LOWER(kategorija) = LOWER(?)");
                args.add(filter);
            }
        }

        String queryText = text == null ? "" : text.trim();
        if (!queryText.isEmpty()) {
            String pattern = "%" + queryText + "%";
            sql.append(" AND (LOWER(nosaukums) LIKE LOWER(?) OR LOWER(autors) LIKE LOWER(?) OR isbn LIKE ?)");
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        sql.append(" ORDER BY nosaukums");
        return query(sql.toString(), args.toArray());
    }

    public List<String> findCategories() throws SQLException {
        List<String> categories = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT DISTINCT kategorija FROM gramata " +
                "WHERE kategorija IS NOT NULL AND kategorija <> '' ORDER BY kategorija");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) categories.add(rs.getString("kategorija"));
        }
        return categories;
    }

    /** Null nozīmē, ka konkrētais filtrs nav jāpiemēro */
    public List<Gramata> filter(String kategorija, Integer gads, Boolean pieejamiba) throws SQLException {
        String sql = "SELECT * FROM gramata WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (kategorija != null) { sql += " AND kategorija = ?"; args.add(kategorija); }
        if (gads != null) { sql += " AND izdosanas_gads = ?"; args.add(gads); }
        if (pieejamiba != null) { sql += " AND pieejamiba = ?"; args.add(pieejamiba); }
        return query(sql + " ORDER BY nosaukums", args.toArray());
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM gramata WHERE gramatas_id = ?")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }

    private static final String DEFAULT_LIBRARY = "DTTT bibliotēka";

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    private <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            return work.run(c);
        }
    }

    public List<Gramata> searchBooks(String text, String filter, Lietotajs actor) throws SQLException {
        if (actor == null) throw new SecurityException("Vispirms piesakieties sistēmā.");
        return withConnection(connection -> {
            List<Gramata> books = new GramataDAO(connection).search(text, filter);
            if (actor.getLoma() == Loma.AUTHOR) {
                String author = (safe(actor.getVards()) + " " + safe(actor.getUzvards())).trim().toLowerCase(Locale.ROOT);
                books.removeIf(book -> author.isEmpty() || book.getAutors() == null
                        || !book.getAutors().toLowerCase(Locale.ROOT).contains(author));
            }
            return books;
        });
    }

    public List<String> findBookCategories() throws SQLException {
        return withConnection(connection -> new GramataDAO(connection).findCategories());
    }

    public Gramata findBookById(int id, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        return withConnection(connection -> new GramataDAO(connection).findById(id));
    }

    /**
     * Saglabā grāmatu (jaunu vai rediģētu).
     *
     * Parametrs {@code available} tagad tiek piemērots ABOS gadījumos:
     * <ul>
     *   <li>Jauna grāmata: ieraksta {@code pieejamiba} kolonnā un, ja {@code available == true},
     *       izveido arī sākotnējo eksemplāru ar statusu {@code 'pieejams'}.</li>
     *   <li>Rediģēšana: atjaunina {@code pieejamiba} kolonnu atbilstoši checkbox stāvoklim.</li>
     * </ul>
     */
    public Gramata saveBook(Gramata form, int editingId, boolean available, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        if (form == null) throw new IllegalArgumentException("Nav saņemti grāmatas dati.");
        requireText(form.getNosaukums(), "Grāmatas nosaukums ir obligāts.");
        if (form.getNosaukums().trim().length() > 200) throw new IllegalArgumentException("Grāmatas nosaukums nedrīkst pārsniegt 200 rakstzīmes.");
        if (form.getAutors() != null && form.getAutors().trim().length() > 100) throw new IllegalArgumentException("Autora nosaukums nedrīkst pārsniegt 100 rakstzīmes.");
        if (form.getKategorija() != null && form.getKategorija().trim().length() > 50) throw new IllegalArgumentException("Kategorija nedrīkst pārsniegt 50 rakstzīmes.");
        if (form.getIsbn() != null && form.getIsbn().trim().length() > 20) throw new IllegalArgumentException("ISBN nedrīkst pārsniegt 20 rakstzīmes.");
        if (form.getIzdosanasGads() != null && (form.getIzdosanasGads() < 1800 || form.getIzdosanasGads() > 2100)) {
            throw new IllegalArgumentException("Izdošanas gadam jābūt starp 1800 un 2100.");
        }
        // Tukšs neobligāts ISBN jāglabā kā NULL, citādi UNIQUE liedz saglabāt otru grāmatu bez ISBN.
        form.setNosaukums(form.getNosaukums().trim());
        form.setAutors(clean(form.getAutors()));
        form.setIsbn(clean(form.getIsbn()));
        form.setKategorija(clean(form.getKategorija()));
        form.setApraksts(clean(form.getApraksts()));

        // Pārnes UI checkbox vērtību uz modeļa lauku, ko izmanto bind().
        form.setPieejamiba(available);

        return withConnection(connection -> {
            connection.setAutoCommit(false);
            try {
                GramataDAO books = new GramataDAO(connection);
                EksemplarsDAO copies = new EksemplarsDAO(connection);
                Gramata saved;
                if (editingId < 0) {
                    saved = form;
                    saved.setGramatasId(0);
                    books.insert(saved);
                    if (available) {
                        Eksemplars copy = new Eksemplars();
                        copy.setGramata(saved);
                        copy.setBiblioteka(DEFAULT_LIBRARY);
                        copy.setStatus("pieejams");
                        copies.insert(copy);
                    }
                } else {
                    saved = books.findById(editingId);
                    if (saved == null) throw new SQLException("Grāmata nav atrasta.");
                    saved.setNosaukums(form.getNosaukums().trim());
                    saved.setAutors(clean(form.getAutors()));
                    saved.setIsbn(clean(form.getIsbn()));
                    saved.setKategorija(clean(form.getKategorija()));
                    saved.setApraksts(clean(form.getApraksts()));
                    saved.setIzdosanasGads(form.getIzdosanasGads());
                    // Checkbox stāvoklis tagad tiek saglabāts arī rediģēšanas režīmā.
                    saved.setPieejamiba(form.isPieejama());
                    if (!books.update(saved)) throw new SQLException("Grāmatas dati netika saglabāti.");
                }
                connection.commit();
                Gramata refreshed = books.findById(saved.getGramatasId());
                return refreshed == null ? saved : refreshed;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public boolean deleteBook(int id, Lietotajs actor) throws SQLException {
        requireStaff(actor);
        return withConnection(connection -> new GramataDAO(connection).delete(id));
    }

    public Gramata fetchBookMetadataByIsbn(String isbn) throws IOException, InterruptedException {
        requireText(isbn, "Ievadiet ISBN.");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:"
                + URLEncoder.encode(isbn.trim(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Google Books atgrieza HTTP " + response.statusCode() + ".");
        }
        String json = response.body();
        String title = jsonValue(json, "title");
        if (title == null || title.isBlank()) throw new IOException("Google Books neatrada grāmatu pēc šī ISBN.");
        Gramata book = new Gramata();
        book.setNosaukums(title);
        book.setIsbn(isbn.trim());
        book.setAutors(jsonValue(json, "authors"));
        book.setApraksts(jsonValue(json, "description"));
        book.setKategorija(jsonValue(json, "categories"));
        String published = jsonValue(json, "publishedDate");
        if (published != null && published.length() >= 4) {
            try { book.setIzdosanasGads(Integer.parseInt(published.substring(0, 4))); }
            catch (NumberFormatException ignored) { /* Nekorektu bibliogrāfisku gadu ignorē. */ }
        }
        return book;
    }

    /** Idempotenti izveido demonstrācijas datus šīs DAO atbildības tabulā. */
    int ensureInitialBook(String title, String description, Integer year,
                          String category, String isbn, String author, boolean available) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT gramatas_id FROM gramata WHERE nosaukums = ?")) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO gramata (nosaukums, apraksts, izdosanas_gads, kategorija, isbn, autors, pieejamiba) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, description);
            if (year == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, year);
            ps.setString(4, category);
            if (isbn == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, isbn);
            ps.setString(6, author);
            ps.setBoolean(7, available);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Neizdevās iegūt grāmatas ID");
                return keys.getInt(1);
            }
        }
    }
}