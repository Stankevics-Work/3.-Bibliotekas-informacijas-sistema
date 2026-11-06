package bibliotekas_informacijas_sistema;

import com.sun.net.httpserver.HttpServer;
import dao.GramataDAO;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import model.Gramata;
import org.junit.*;
import static org.junit.Assert.*;

/** Pārbauda īstu HTTP pieprasījumu pret kontrolētu lokālu API aizstājēju. */
public class IsbnIntegrationTest {
    private HttpServer server;
    private GramataDAO dao;
    private final AtomicReference<String> query = new AtomicReference<>();
    private int status = 200;
    private String response = "{\"items\":[{\"volumeInfo\":{\"title\":\"Testa grāmata\",\"authors\":[\"Testa Autors\"],\"publishedDate\":\"2018-01-01\",\"description\":\"Testa apraksts\",\"categories\":[\"Programmēšana\"]}}]}";

    @Before public void prepareHttpServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/volumes", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
            exchange.close();
        });
        server.start();
        dao = new GramataDAO() {
            @Override protected URI metadataRequestUri(String isbn) {
                return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/volumes?q=isbn:" + isbn.trim());
            }
        };
    }

    @After public void stopHttpServer() { if (server != null) server.stop(0); }

    @Test public void isbnMetadataIsImportedThroughHttp() throws Exception {
        Gramata book = dao.fetchBookMetadataByIsbn(" 9780134685991 ");
        assertEquals("q=isbn:9780134685991", query.get());
        assertEquals("9780134685991", book.getIsbn());
        assertEquals("Testa grāmata", book.getNosaukums());
        assertEquals("Testa Autors", book.getAutors());
        assertEquals(Integer.valueOf(2018), book.getIzdosanasGads());
        assertEquals("Testa apraksts", book.getApraksts());
        assertEquals("Programmēšana", book.getKategorija());
    }

    @Test public void rateLimitIsReportedAndLaterRequestCanSucceed() throws Exception {
        status = 429;
        try { dao.fetchBookMetadataByIsbn("9780134685991"); fail("HTTP 429 must be reported"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("429")); }
        status = 200;
        assertEquals("Testa grāmata", dao.fetchBookMetadataByIsbn("9780134685991").getNosaukums());
    }

    @Test public void emptyApiResultIsRejected() throws Exception {
        response = "{\"totalItems\":0,\"items\":[]}";
        try { dao.fetchBookMetadataByIsbn("9780134685991"); fail("Missing book accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("neatrada")); }
    }

    @Test public void emptyIsbnDoesNotSendRequest() throws Exception {
        try { dao.fetchBookMetadataByIsbn("   "); fail("Blank ISBN accepted"); }
        catch (IllegalArgumentException expected) { }
        assertNull(query.get());
    }
}
