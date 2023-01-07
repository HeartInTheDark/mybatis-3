/**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.logging.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * Connection proxy to add logging
 *封装了Connection对象
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class ConnectionLogger extends BaseJdbcLogger implements InvocationHandler {

    private final Connection connection;

    private ConnectionLogger(Connection conn, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.connection = conn;
    }

    //核心方法
    //为 prepareStatement() 、prepareCall()等方法提供代理
    @Override
    public Object invoke(Object proxy, Method method, Object[] params)
            throws Throwable {
        try {
            //如果调用的是Object继承的方法，直接调用，不做处理
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, params);
            }
            //如果调用的是prepareStatement等方法，则在创建相应Statement对象后，为其创建代理对象并返回该代理对象
            if ("prepareStatement".equals(method.getName())) {
                if (isDebugEnabled()) {//日志输出
                    debug(" Preparing: " + removeBreakingWhitespace((String) params[0]), true);
                }
                //调用底层封装的Connection对象的prepareStatement()方法，得到PreparedStatement对象
                PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
                //为该PreparedStatement对象创建代理对象
                stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
                return stmt;
            } else if ("prepareCall".equals(method.getName())) {
                if (isDebugEnabled()) {//日志输出
                    debug(" Preparing: " + removeBreakingWhitespace((String) params[0]), true);
                }
                PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
                stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
                return stmt;
            } else if ("createStatement".equals(method.getName())) {
                Statement stmt = (Statement) method.invoke(connection, params);
                stmt = StatementLogger.newInstance(stmt, statementLog, queryStack);
                return stmt;
            } else {
                //其他方法直接调用底层Connection对象的相应方法
                return method.invoke(connection, params);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    /*
     * Creates a logging version of a connection
     *
     * @param conn - the original connection
     * @return - the connection with logging
     */
    public static Connection newInstance(Connection conn, Log statementLog, int queryStack) {
        //使用jdk动态代理的方式创建代理对象
        InvocationHandler handler = new ConnectionLogger(conn, statementLog, queryStack);
        ClassLoader cl = Connection.class.getClassLoader();
        return (Connection) Proxy.newProxyInstance(cl, new Class[]{Connection.class}, handler);
    }

    /*
     * return the wrapped connection
     *
     * @return the connection
     */
    public Connection getConnection() {
        return connection;
    }

}
