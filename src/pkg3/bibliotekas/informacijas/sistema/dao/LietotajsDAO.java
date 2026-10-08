package pkg3.bibliotekas.informacijas.sistema.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pkg3.bibliotekas.informacijas.sistema.model.*;

/** Lietotajs datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class LietotajsDAO {
    private final Connection connection;

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public LietotajsDAO(Connection connection) { this.connection = connection; }

    private Lietotajs map(ResultSet rs) throws SQLException {
        Lietotajs value = new Lietotajs();
        value.setLietotajaId(rs.getInt("lietotaja_id"));
        value.setVards(rs.getString("vards"));
        value.setUzvards(rs.getString("uzvards"));
        value.setLietotajvards(rs.getString("lietotajvards"));
        value.setParole(rs.getString("parole"));
        value.setEpasts(rs.getString("epasts"));
        value.setLoma(Loma.valueOf(rs.getString("loma")));
        return value;
    }

    private List<Lietotajs> query(String sql, Object... args) throws SQLException {
        List<Lietotajs> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }

    public Lietotajs findById(int id) throws SQLException {
        List<Lietotajs> result = query("SELECT * FROM lietotajs WHERE lietotaja_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Lietotajs> findAll() throws SQLException {
        return query("SELECT * FROM lietotajs ORDER BY lietotaja_id");
    }

    private void bind(PreparedStatement ps, Lietotajs value) throws SQLException {
        ps.setString(1, value.getVards());
        ps.setString(2, value.getUzvards());
        ps.setString(3, value.getLietotajvards());
        ps.setString(4, value.getParole());
        ps.setString(5, value.getEpasts());
        ps.setString(6, value.getLoma().name());
    }

    public int insert(Lietotajs value) throws SQLException {
        if (value.getLoma() == null) value.setLoma(Loma.READER);
        if (value.getParole() == null || !value.getParole().matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"))
            throw new SQLException("Parolei jābūt BCrypt jaucējvērtībai");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO lietotajs (vards, uzvards, lietotajvards, parole, epasts, loma) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, value); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                value.setLietotajaId(keys.getInt(1)); return value.getLietotajaId();
            }
        }
    }

    public boolean update(Lietotajs value) throws SQLException {
        if (value.getLoma() == null) value.setLoma(Loma.READER);
        if (value.getParole() == null || !value.getParole().matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"))
            throw new SQLException("Parolei jābūt BCrypt jaucējvērtībai");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE lietotajs SET vards = ?, uzvards = ?, lietotajvards = ?, parole = ?, epasts = ?, loma = ? WHERE lietotaja_id = ?")) {
            bind(ps, value); ps.setInt(7, value.getLietotajaId());
            return ps.executeUpdate() == 1;
        }
    }

    public Lietotajs findByUsername(String username) throws SQLException {
        List<Lietotajs> result = query("SELECT * FROM lietotajs WHERE lietotajvards = ?", username);
        return result.isEmpty() ? null : result.get(0);
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM lietotajs WHERE lietotaja_id = ?")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }
}
