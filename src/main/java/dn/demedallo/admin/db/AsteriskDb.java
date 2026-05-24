package dn.demedallo.admin.db;

import dn.demedallo.admin.util.AdminDbSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** JDBC access to Issabel/FreePBX {@code asterisk} database. */
public final class AsteriskDb {

    private final AdminDbSettings settings;

    public AsteriskDb(AdminDbSettings settings) {
        this.settings = settings;
    }

    public Connection open() throws SQLException {
        return openPbx();
    }

    public Connection openPbx() throws SQLException {
        if (!settings.isConfigured()) {
            throw new SQLException("MySQL no configurado en el login.");
        }
        return DriverManager.getConnection(
                settings.jdbcUrlPbx(), settings.dbUser, settings.dbPassword);
    }

    public Connection openCdr() throws SQLException {
        if (!settings.isConfigured()) {
            throw new SQLException("MySQL no configurado en el login.");
        }
        return DriverManager.getConnection(
                settings.jdbcUrlCdr(), settings.dbUser, settings.dbPassword);
    }
}
