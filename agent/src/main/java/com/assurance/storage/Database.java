package com.assurance.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Small single-application SQLite store; each operation owns its connection. */
public final class Database {
    private final String url;
    public Database(Path file) {
        try {
            Path absolute=file.toAbsolutePath().normalize();
            Files.createDirectories(absolute.getParent());
            url="jdbc:sqlite:"+absolute;
            try(var c=open(); var s=c.createStatement()) {s.execute("PRAGMA journal_mode=WAL");}
        } catch(Exception e){throw new IllegalStateException("本地数据存储不可用");}
    }
    public Connection open() throws SQLException {
        Connection c=DriverManager.getConnection(url);
        try(var s=c.createStatement()){s.execute("PRAGMA busy_timeout=5000");s.execute("PRAGMA foreign_keys=ON");}
        return c;
    }
    public void execute(String sql) {
        try(var c=open();var s=c.createStatement()){s.execute(sql);}
        catch(SQLException e){throw new IllegalStateException("本地数据存储不可用");}
    }
}
