package pkg3.bibliotekas.informacijas.sistema;

import java.awt.EventQueue;
import javax.swing.JOptionPane;
import pkg3.bibliotekas.informacijas.sistema.dao.LietotajsDAO;

/** Lietotnes palaišanas punkts: sākotnējās datu sagatavošanas metodi izsauc DAO. */
public class BibliotekasInformacijasSistema {
    public static void main(String[] args) {
        try {
            new LietotajsDAO().initializeDatabase();
        } catch (Exception ex) {
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
                JOptionPane.showMessageDialog(null,
                        "Neizdevās atvērt LibInfo saskarni:\n" + ex.getMessage(),
                        "LibInfo palaišanas kļūda", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
