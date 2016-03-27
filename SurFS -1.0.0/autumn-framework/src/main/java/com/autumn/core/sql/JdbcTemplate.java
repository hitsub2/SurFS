/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.autumn.core.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.naming.NameNotFoundException;
import javax.sql.DataSource;
import javax.sql.rowset.CachedRowSet;

/**
 *
 * <p>
 * Title: JDBC简单框架</p>
 *
 * <p>
 * Description: 执行查询/更新,线程安全</p>
 *
 * <p>
 * Copyright: Autumn Copyright (c) 2011</p>
 *
 * <p>
 * Company: Autumn </p>
 *
 * @author 刘社朋
 * @version 2.0
 */
public abstract class JdbcTemplate {

    public static String ROWSET_IMPL_CLASS = "com.sun.rowset.CachedRowSetImpl";
    private DataSource dataSource = null;
    private static final ThreadLocal<List<Parameter>> params = new ThreadLocal<List<Parameter>>();
    private int fetchSize = 0;
    private int maxRows = 0;
    private int queryTimeout = 0;
    private int fetchDirection = 0;

    /**
     * 创建
     *
     * @param datasourcename
     */
    public JdbcTemplate(String datasourcename) throws NameNotFoundException {
        this.dataSource = ConnectionFactory.lookup(datasourcename);
    }

    /**
     * 创建
     *
     * @param dataSource
     */
    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 添加参数
     *
     * @param cls Class
     */
    public void addNullParameter(Class cls) throws Exception {
        if (cls == null) {
            addParameter(JdbcUtils.TYPE_UNKNOWN, null);
        } else {
            addParameter(JdbcUtils.javaTypeToSqlParameterType(cls), null);
        }
    }

    /**
     * 添加参数
     *
     * @param value
     */
    public void addParameter(Object... value) {
        for (Object obj : value) {
            addParameter(obj);
        }
    }

    /**
     * 添加参数
     *
     * @param value
     */
    public void addParameter(Object value) {
        if (value == null) {
            addParameter(JdbcUtils.TYPE_UNKNOWN, value);
        } else {
            addParameter(JdbcUtils.javaTypeToSqlParameterType(value.getClass()), value);
        }
    }

    /**
     * 获取参数
     *
     * @return List<Parameter>
     */
    private List<Parameter> getParams() {
        return getParams(true);
    }

    /**
     * 获取参数
     *
     * @param create
     * @return List<Parameter>
     */
    private List<Parameter> getParams(boolean create) {
        List<Parameter> ps = params.get();
        if (ps == null) {
            if (create) {
                ps = new ArrayList<Parameter>();
                params.set(ps);
            }
        }
        return ps;
    }

    /**
     * 添加参数
     *
     * @param type 指定参数类型
     * @param value
     */
    public void addParameter(int type, Object value) {
        Parameter p = new Parameter(type, value);
        List<Parameter> ps = getParams();
        ps.add(p);
    }

    /**
     * 清除参数
     */
    public void clearParameters() {
        List<Parameter> ps = getParams(false);
        if (ps != null) {
            ps.clear();
        }
    }

    /**
     * 创建声明
     *
     * @param sql
     * @return PreparedStatement
     * @throws SQLException
     */
    protected PreparedStatement createPreparedStatement(Connection con, String sql, List<Parameter> param) throws SQLException {
        return createPreparedStatement(con, sql, false, param);
    }

    /**
     * 创建声明
     *
     * @param sql
     * @param autoGeneratedKeys
     * @return PreparedStatement
     * @throws SQLException
     */
    protected PreparedStatement createPreparedStatement(Connection con, String sql, boolean autoGeneratedKeys, List<Parameter> param) throws SQLException {
        PreparedStatement ps = null;
        try {
            if (autoGeneratedKeys) {
                ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            } else {
                ps = con.prepareStatement(sql);
            }
            if (fetchSize > 0) {
                ps.setFetchSize(fetchSize);
            }
            if (fetchDirection > 0) {
                ps.setFetchDirection(fetchDirection);
            }
            if (queryTimeout > 0) {
                ps.setQueryTimeout(queryTimeout);
            }
            if (maxRows > 0) {
                ps.setMaxRows(maxRows);
            }
            if (param != null) {
                for (int ii = 0, count = param.size(); ii < count; ii++) {
                    Parameter p = param.get(ii);
                    if (p.value == null) {
                        JdbcUtils.setNull(ps, ii + 1, p.type);
                    } else {
                        JdbcUtils.setValue(ps, ii + 1, p.value, p.type);
                    }
                }
            }
            return ps;
        } catch (SQLException r) {
            JdbcUtils.closeStatement(ps);
            throw r;
        }
    }

    /**
     * 获取链接
     *
     * @return Connection
     * @throws SQLException
     */
    private Connection getConnection() throws SQLException {
        if (dataSource instanceof SmartDataSource) {
            SmartDataSource ds = (SmartDataSource) dataSource;
            return ds.getConnection(JdbcTemplate.class.getName());
        } else {
            return dataSource.getConnection();
        }
    }

    /**
     * 执行查询
     *
     * @param sql
     * @return ResultSet
     * @throws com.autumn.core.sql.SortedSQLException
     */
    public ResultSet query(String sql) throws SortedSQLException {
        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = getConnection();
            ps = createPreparedStatement(con, sql, getParams(false));
            ResultSet rs = ps.executeQuery();
            CachedRowSet cacherowset = (CachedRowSet) Class.forName(ROWSET_IMPL_CLASS).newInstance();
            cacherowset.populate(rs);
            return cacherowset;
        } catch (Exception r) {
            throw r instanceof SortedSQLException ? (SortedSQLException) r : new SortedSQLException(con, r);
        } finally {
            clearParameters();
            JdbcUtils.closeStatement(ps);
            JdbcUtils.closeConnect(con);
        }
    }

    /**
     * 执行更新
     *
     * @param sql
     * @return int
     * @throws SQLException
     */
    public int update(String sql) throws SortedSQLException {
        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = getConnection();
            ps = createPreparedStatement(con, sql, getParams(false));
            return ps.executeUpdate();
        } catch (Exception r) {
            throw r instanceof SortedSQLException ? (SortedSQLException) r : new SortedSQLException(con, r);
        } finally {
            clearParameters();
            JdbcUtils.closeStatement(ps);
            JdbcUtils.closeConnect(con);
        }
    }

    /**
     * 执行写入，返回增量值
     *
     * @param sql
     * @return long
     * @throws com.autumn.core.sql.SortedSQLException
     */
    public long insert(String sql) throws SortedSQLException {
        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = getConnection();
            ps = createPreparedStatement(con, sql, true, getParams(false));
            int rc = ps.executeUpdate();
            if (rc < 1) {
                return 0;
            } else {
                ResultSet rs = ps.getGeneratedKeys();
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception r) {
            throw r instanceof SortedSQLException ? (SortedSQLException) r : new SortedSQLException(con, r);
        } finally {
            clearParameters();
            JdbcUtils.closeStatement(ps);
            JdbcUtils.closeConnect(con);
        }
    }

    /**
     * @return the fetchSize
     */
    public int getFetchSize() {
        return fetchSize;
    }

    /**
     * @param fetchSize the fetchSize to set
     */
    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    /**
     * @return the maxRows
     */
    public int getMaxRows() {
        return maxRows;
    }

    /**
     * @param maxRows the maxRows to set
     */
    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    /**
     * @return the queryTimeout
     */
    public int getQueryTimeout() {
        return queryTimeout;
    }

    /**
     * @param queryTimeout the queryTimeout to set
     */
    public void setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    /**
     * @return the fetchDirection
     */
    public int getFetchDirection() {
        return fetchDirection;
    }

    /**
     * @param fetchDirection the fetchDirection to set
     */
    public void setFetchDirection(int fetchDirection) {
        if (fetchDirection == ResultSet.FETCH_FORWARD || fetchDirection == ResultSet.FETCH_REVERSE || fetchDirection == ResultSet.FETCH_UNKNOWN) {
            this.fetchDirection = fetchDirection;
        }
    }

    public class Parameter {

        public Integer type = null;//类型
        public Object value = null;//值

        public Parameter(int t, Object obj) {
            type = t;
            value = obj;
        }
    }
}
