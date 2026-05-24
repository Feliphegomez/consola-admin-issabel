module dn.demedallo.admin {
    requires java.desktop;
    requires java.net.http;
    requires java.prefs;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    requires java.xml;
    requires java.sql;
    requires org.mariadb.jdbc;
    requires com.jcraft.jsch;
    requires org.apache.poi.ooxml;
    requires com.github.librepdf.openpdf;

    exports dn.demedallo.admin.ui;
}
