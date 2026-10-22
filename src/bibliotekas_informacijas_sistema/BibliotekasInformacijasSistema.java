package bibliotekas_informacijas_sistema;

import java.sql.SQLException;
import dao.LietotajsDAO;

/** Datu sagatavošana. Vienīgais lietotnes main ir LibInfoSaskarne klasē. */
public final class BibliotekasInformacijasSistema {
    private BibliotekasInformacijasSistema() { }

    public static void initializeDatabase() throws SQLException {
        new LietotajsDAO().initializeDatabase();
    }
}
