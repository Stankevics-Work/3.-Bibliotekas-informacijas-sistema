package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/** Vienots žurnāls visai lietotnei: logs/libinfo.log */
public final class LogUtil {
    private static final Logger LOGGER = Logger.getLogger("LibInfo");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static boolean initialized = false;

    private LogUtil() { }

    public static synchronized void init() {
        if (initialized) return;
        try {
            Path dir = Paths.get("logs");
            Files.createDirectories(dir);
            // 5 MB liels fails, saglabā 3 vecākās versijas, raksta papildus (true)
            FileHandler fh = new FileHandler(dir.resolve("libinfo.log").toString(), 5_000_000, 3, true); 
            fh.setLevel(Level.INFO);
            fh.setFormatter(new Formatter() {
                @Override public String format(LogRecord r) {
                    return String.format("%s [%s] %s: %s%n",
                        LocalDateTime.now().format(FMT), r.getLevel(),
                        r.getLoggerName(), formatMessage(r));
                }
            });
            LOGGER.addHandler(fh);
            LOGGER.setLevel(Level.INFO);
            LOGGER.setUseParentHandlers(false);
        } catch (IOException ex) {
            System.err.println("Neizdevās inicializēt žurnālu: " + ex.getMessage());
        }
        initialized = true;
    }

    public static void info(String msg) { init(); LOGGER.info(msg); }
    public static void warning(String msg) { init(); LOGGER.warning(msg); }
    public static void error(String msg, Throwable t) { init(); LOGGER.log(Level.SEVERE, msg, t); }
}