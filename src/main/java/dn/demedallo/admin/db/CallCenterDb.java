package dn.demedallo.admin.db;

import dn.demedallo.admin.util.AdminDbSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class CallCenterDb {

    private final AdminDbSettings settings;

    public CallCenterDb(AdminDbSettings settings) {
        this.settings = settings;
    }

    public Connection open() throws SQLException {
        if (!settings.isConfigured()) {
            throw new SQLException("Base de datos call_center no configurada en el login.");
        }
        return DriverManager.getConnection(
                settings.jdbcUrl(), settings.dbUser, settings.dbPassword);
    }
}
