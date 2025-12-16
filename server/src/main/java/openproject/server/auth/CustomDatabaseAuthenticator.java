package openproject.server.auth;

import openproject.server.db.DatabaseManager;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CustomDatabaseAuthenticator implements PasswordAuthenticator {
    private static final Logger LOG = LoggerFactory.getLogger(CustomDatabaseAuthenticator.class);
    private final DatabaseManager dbManager;

    public CustomDatabaseAuthenticator(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public boolean authenticate(String username, String password, ServerSession serverSession) throws PasswordChangeRequiredException, AsyncAuthException {
        LOG.info("Authentication attempt for '{}' from '{}'", username, serverSession.getUsername());

        try (Connection conn = dbManager.getConnection()) {
            // Busqueda de hash y usuario activo
            String query = "select password_hash from users where username = ? and is_active = TRUE";

            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String passwordHash = rs.getString("password_hash");

                        // BCrypt verifica el hash
                        if (BCrypt.checkpw(password, passwordHash)) {
                            LOG.info("Authentication successful for '{}'", username);
                            return true;
                        } else {
                            LOG.warn("Authentication failed for '{}'", username);
                        }
                    } else {
                        LOG.warn("User '{}' not found or inactive", username);
                    }
                }
            }

        } catch (Exception e) {
            LOG.error("Database error", e);
        }

        return false;
    }
}
