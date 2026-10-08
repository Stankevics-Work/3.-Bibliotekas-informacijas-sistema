package pkg3.bibliotekas.informacijas.sistema;

import java.awt.EventQueue;
import java.sql.*;
import javax.swing.JOptionPane;
import pkg3.bibliotekas.informacijas.sistema.db.DatabaseManager;

/**
 * Lietotnes galvenā klase: vispirms sagatavo Derby datubāzi, pēc tam uzreiz atver LibInfo GUI.
 */
public class BibliotekasInformacijasSistema {
    public static void main(String[] args) {
        try (Connection c = DatabaseManager.getConnection()) {
            DatabaseManager.initialize(c);
            DatabaseManager.seedInitialData(c);
            if (!DatabaseManager.testConnection(c)) throw new SQLException("JDBC pārbaude neizdevās");
            System.out.println("JDBC savienojums ar Derby darbojas");
            System.out.println("Datubāzes inicializācija pabeigta");
            System.out.println("Derby versija: " + c.getMetaData().getDatabaseProductVersion());
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM gramata")) {
                rs.next(); System.out.println("Grāmatu skaits: " + rs.getInt(1));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            EventQueue.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Neizdevās sagatavot datubāzi:\n" + ex.getMessage(),
                    "LibInfo palaišanas kļūda", JOptionPane.ERROR_MESSAGE));
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                Class<?> guiClass = Class.forName("LibInfoSaskarne");
                guiClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Neizdevās atvērt LibInfo saskarni:\n" + ex.getMessage(),
                        "LibInfo palaišanas kļūda", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
