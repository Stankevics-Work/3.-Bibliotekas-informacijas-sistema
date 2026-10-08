package pkg3.bibliotekas.informacijas.sistema.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pkg3.bibliotekas.informacijas.sistema.model.*;

/** Gramata datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class GramataDAO {
    private final Connection connection;

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

    private void bind(PreparedStatement ps, Gramata value) throws SQLException {
        ps.setString(1, value.getNosaukums());
        ps.setString(2, value.getApraksts());
        if (value.getIzdosanasGads() == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, value.getIzdosanasGads());
        ps.setString(4, value.getIsbn());
        ps.setString(5, value.getKategorija());
        ps.setString(6, value.getAutors());
    }

    public int insert(Gramata value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO gramata (nosaukums, apraksts, izdosanas_gads, isbn, kategorija, autors) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, value); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                value.setGramatasId(keys.getInt(1)); return value.getGramatasId();
            }
        }
    }

    public boolean update(Gramata value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE gramata SET nosaukums = ?, apraksts = ?, izdosanas_gads = ?, isbn = ?, kategorija = ?, autors = ? WHERE gramatas_id = ?")) {
            bind(ps, value); ps.setInt(7, value.getGramatasId());
            return ps.executeUpdate() == 1;
        }
    }

    public List<Gramata> search(String text) throws SQLException {
        String pattern = "%" + (text == null ? "" : text) + "%";
        return query("SELECT * FROM gramata WHERE LOWER(nosaukums) LIKE LOWER(?) OR LOWER(autors) LIKE LOWER(?) OR isbn LIKE ? ORDER BY nosaukums", pattern, pattern, pattern);
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
}
