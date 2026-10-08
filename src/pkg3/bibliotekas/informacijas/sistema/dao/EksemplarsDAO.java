package pkg3.bibliotekas.informacijas.sistema.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pkg3.bibliotekas.informacijas.sistema.model.*;

/** Eksemplars datu piekļuve ar parametrizētiem JDBC vaicājumiem */
public class EksemplarsDAO {
    private final Connection connection;

    /** Savienojumu aizver izsaucējs, vienu savienojumu neizmanto vairākos pavedienos */
    public EksemplarsDAO(Connection connection) { this.connection = connection; }

    private Eksemplars map(ResultSet rs) throws SQLException {
        Eksemplars value = new Eksemplars();
        value.setEksemplaraId(rs.getInt("eksemplara_id"));
        value.setGramata(new GramataDAO(connection).findById(rs.getInt("gramatas_id")));
        value.setBiblioteka(rs.getString("biblioteka"));
        value.setStatus(rs.getString("status"));
        return value;
    }

    private List<Eksemplars> query(String sql, Object... args) throws SQLException {
        List<Eksemplars> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }

    public Eksemplars findById(int id) throws SQLException {
        List<Eksemplars> result = query("SELECT * FROM eksemplars WHERE eksemplara_id = ?", id);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<Eksemplars> findAll() throws SQLException {
        return query("SELECT * FROM eksemplars ORDER BY eksemplara_id");
    }

    private void bind(PreparedStatement ps, Eksemplars value) throws SQLException {
        ps.setInt(1, value.getGramata().getGramatasId());
        ps.setString(2, value.getBiblioteka());
        ps.setString(3, value.getStatus());
    }

    public int insert(Eksemplars value) throws SQLException {
        if (value.getStatus() == null) value.setStatus("pieejams");
        if (!"pieejams".equals(value.getStatus())) throw new SQLException("Jaunam eksemplāram jābūt pieejamam");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO eksemplars (gramatas_id, biblioteka, status) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, value); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Nav iegūts ieraksta ID");
                value.setEksemplaraId(keys.getInt(1)); return value.getEksemplaraId();
            }
        }
    }

    /** Rediģē tikai atrašanās vietu, grāmatas saite un aprites statuss paliek nemainīgi */
    public boolean update(Eksemplars value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE eksemplars SET biblioteka = ? WHERE eksemplara_id = ? AND gramatas_id = ? AND status = ?")) {
            ps.setString(1, value.getBiblioteka()); ps.setInt(2, value.getEksemplaraId());
            ps.setInt(3, value.getGramata().getGramatasId()); ps.setString(4, value.getStatus());
            return ps.executeUpdate() == 1;
        }
    }

    public List<Eksemplars> findByBook(int bookId) throws SQLException {
        return query("SELECT * FROM eksemplars WHERE gramatas_id = ? ORDER BY eksemplara_id", bookId);
    }

    /** Dzēš ierakstu, ārējās atslēgas aizsargā saistītos datus */
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM eksemplars WHERE eksemplara_id = ? AND status = 'pieejams'")) {
            ps.setInt(1, id); return ps.executeUpdate() == 1;
        }
    }
}
