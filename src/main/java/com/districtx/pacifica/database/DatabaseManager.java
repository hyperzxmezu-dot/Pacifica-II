package com.districtx.pacifica.database;

import java.sql.Connection;

public interface DatabaseManager {
    void initialize();
    void close();
    Connection getConnection();
    void createTables();
    void backup();
    boolean isConnected();
}