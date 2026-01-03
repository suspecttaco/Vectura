package com.vectura.server.db;

import com.vectura.server.util.UILogManager;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);
    // URL
    private static final String DB_URL = "jdbc:h2:./vectura_users;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    public void init() throws SQLException {
        LOG.info("Init Database Manager in: {}", DB_URL);
        UILogManager.log(String.format("[DB] Init Database Manager in: %s", DB_URL));

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sqlCreate = "CREATE TABLE IF NOT EXISTS users (" +
                    "username varchar(50) PRIMARY KEY," +
                    "password_hash varchar(255) NOT NULL," +
                    "home_dir varchar(255) NOT NULL," +
                    "is_active boolean default true)";

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sqlCreate);
            }

            // Crear usuario default
            if (shouldCreateAdmin(conn)) {
                createAdminUser(conn);
            }
        }
    }

    private boolean shouldCreateAdmin(Connection conn) throws SQLException {
        String sqlCount = "SELECT COUNT(*) FROM users";

        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sqlCount);
            if (rs.next()) {
                return rs.getInt(1) == 0; // True si la tabla esta vacia
            }
        }
        return false;
    }

    private void createAdminUser(Connection conn) throws SQLException {
        LOG.warn("Creating Admin User");
        UILogManager.log("[DB] Creating Admin User");


        String sqlInsert = "INSERT INTO users (username, password_hash, home_dir) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sqlInsert)) {
            stmt.setString(1, "admin");
            // Hash de contraseña
            String hash = BCrypt.hashpw("admin", BCrypt.gensalt());
            stmt.setString(2, hash);

            // Directorio root
            stmt.setString(3, "./sftp_root/admin");

            stmt.executeUpdate();
            LOG.info("Admin Created");
            UILogManager.log("[DB] Admin Created");

        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
