/*******************************************************************************
 *     Copyright (C) 2017 wysohn
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package io.github.wysohn.rapidframework.database.mysql;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import io.github.wysohn.rapidframework.database.Database;
import io.github.wysohn.rapidframework.database.MiniConnectionPoolManager;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple key/value pair Mysql implementation.
 * 
 * @author wysohn
 *
 * @param <T>
 */
public class DatabaseMysql<T> extends Database<T> {
    private String dbName;
    private String tablename;

    private final String KEY = "dbkey";
    private final String VALUE = "dbval";

    private final MysqlConnectionPoolDataSource ds;
    private final MiniConnectionPoolManager pool;

    public DatabaseMysql(Class<T> type, String address, String dbName, String tablename, String userName, String password)
            throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        super(type);

        this.dbName = dbName;
        this.tablename = tablename;

		ds = new MysqlConnectionPoolDataSource();
		ds.setURL("jdbc:mysql://" + address + "/" + dbName + "?autoReconnect=true");
		ds.setUser(userName);
		ds.setPassword(password);
		ds.setCharacterEncoding("UTF-8");
		ds.setUseUnicode(true);
        ds.setAutoReconnectForPools(true);
        ds.setAutoReconnect(true);
        ds.setAutoReconnectForConnectionPools(true);
        ds.setUseSSL(false);

        pool = new MiniConnectionPoolManager(ds, 2);

        Connection conn = createConnection();
        initTable(conn);
        conn.close();
    }

    private final String CREATEDATABASEQUARY = "" + "CREATE DATABASE IF NOT EXISTS %s";

    private Connection createConnection() {
        Connection conn = null;

        try {
            conn = pool.getConnection();
        } catch (SQLException e) {
            // e.printStackTrace();
        } finally {
            if (conn == null)
                conn = pool.getValidConnection();
        }

        return conn;
    }

    private final String CREATETABLEQUARY = "" + "CREATE TABLE IF NOT EXISTS %s (" + "" + KEY
            + " CHAR(128) PRIMARY KEY," + "" + VALUE + " MEDIUMBLOB" + ")";

    private void initTable(Connection conn) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(String.format(CREATETABLEQUARY, tablename));
        pstmt.executeUpdate();
        pstmt.close();
    }

    private final String SELECTKEY = "" + "SELECT " + VALUE + " FROM %s WHERE " + KEY + " = ?";

    @Override
    public synchronized T load(String key, T def) {
        Connection conn = null;
        T result = def;

        try {
            conn = createConnection();

            PreparedStatement pstmt = conn.prepareStatement(String.format(SELECTKEY, tablename));
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                InputStream input = rs.getBinaryStream(VALUE);
                InputStreamReader isr = new InputStreamReader(input, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);

                String ser = br.readLine();
                result = (T) deserialize(ser, type);
            }
            pstmt.close();

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    private final String UPDATEQUARY = "INSERT INTO %s VALUES (" + "?," + "?" + ") " + "ON DUPLICATE KEY UPDATE " + ""
            + VALUE + " = VALUES(" + VALUE + ")";
    private final String DELETEQUARY = "DELETE FROM %s WHERE " + KEY + " = ?";

    @Override
    public synchronized void save(String key, T value) {
        Connection conn = null;
        try {
            conn = createConnection();

            if (value != null) {
                String ser = serialize(value, type);
                InputStream input = new ByteArrayInputStream(ser.getBytes(StandardCharsets.UTF_8));

                PreparedStatement pstmt = conn.prepareStatement(String.format(UPDATEQUARY, tablename));
                pstmt.setString(1, key);
                pstmt.setBinaryStream(2, input);
                pstmt.executeUpdate();
                pstmt.close();
            } else {
                PreparedStatement pstmt = conn.prepareStatement(String.format(DELETEQUARY, tablename));
                pstmt.setString(1, key);
                pstmt.executeUpdate();
                pstmt.close();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private final String SELECTKEYS = "" + "SELECT " + KEY + " FROM %s";

    @Override
    public synchronized Set<String> getKeys() {
        Set<String> keys = new HashSet<String>();

        Connection conn = null;
        try {
            conn = createConnection();

            PreparedStatement pstmt = conn.prepareStatement(String.format(SELECTKEYS, tablename));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                keys.add(rs.getString(KEY));
            }
            rs.close();
            pstmt.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return keys;
    }

    private final String SELECTKEYSWHERE = "" + "SELECT " + KEY + " FROM %s WHERE " + KEY + " = ?";

    @Override
    public synchronized boolean has(String key) {
        boolean result = false;

        Connection conn = null;
        try {
            conn = createConnection();

            PreparedStatement pstmt = conn.prepareStatement(String.format(SELECTKEYSWHERE, tablename));
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();

            result = rs.next();

            rs.close();
            pstmt.close();
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    @Override
    protected void finalize() throws Throwable {
        if (pool != null)
            pool.dispose();
        super.finalize();
    }

    private final String DELETEALL = "" + "DELETE FROM %s";

    @Override
    public void clear() {
        Connection conn = null;
        try {
            conn = createConnection();

            PreparedStatement pstmt = conn.prepareStatement(String.format(DELETEALL, tablename));
            pstmt.executeLargeUpdate();

            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

}
