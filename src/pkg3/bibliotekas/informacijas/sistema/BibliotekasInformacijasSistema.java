package pkg3.bibliotekas.informacijas.sistema;

import java.sql.*;
import pkg3.bibliotekas.informacijas.sistema.db.DatabaseManager;

/** Palaiž inicializāciju un JDBC savienojuma pārbaudi */
public class BibliotekasInformacijasSistema {
    public static void main(String[] args) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            DatabaseManager.initialize(c);
            if (!DatabaseManager.testConnection(c)) throw new SQLException("JDBC pārbaude neizdevās");
            System.out.println("JDBC savienojums ar Derby darbojas");
            System.out.println("Datubāzes inicializācija pabeigta");
            System.out.println("Derby versija: " + c.getMetaData().getDatabaseProductVersion());
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM gramata")) {
                rs.next(); System.out.println("Grāmatu skaits: " + rs.getInt(1));
            }
        }
    }
}
