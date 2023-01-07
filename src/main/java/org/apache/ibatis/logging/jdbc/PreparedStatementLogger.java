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
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * PreparedStatement proxy to add logging
 * 封装了PreparedStatement，其中部分方法与ConnectionLogger中类似，可参考ConnectionLogger中注释
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class PreparedStatementLogger extends BaseJdbcLogger implements InvocationHandler {

    private final PreparedStatement statement;

    private PreparedStatementLogger(PreparedStatement stmt, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.statement = stmt;
    }

    //该方法会为 SET_METHODS 集合和 EXECUTE_METHODS 集合中的方法、getResultSet()等方法提供代理
    @Override
    public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
        try {
            if (Object.class.equals(method.getDeclaringClass())) {
                //如果调用的是Object继承的方法，直接调用，不做处理
                return method.invoke(this, params);
            }
            if (EXECUTE_METHODS.contains(method.getName())) {//调用了EXECUTE_METHODS 集合中的方法
                if (isDebugEnabled()) {
                    //日志输出  输出的是参数值以及参数类型
                    debug("Parameters: " + getParameterValueString(), true);
                }
                clearColumnInfo(); //清空 BaseJdbcLogger 中定义的三个 column*集合
                if ("executeQuery".equals(method.getName())) {
                    //如果调用的是 executeQuery() 方法，则为ResultSet创建代理对象
                    ResultSet rs = (ResultSet) method.invoke(statement, params);
                    return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
                } else {
                    //不是executeQuery方法则直接返回结果
                    return method.invoke(statement, params);
                }
            } else if (SET_METHODS.contains(method.getName())) {
                //如果调用的是 SET_METHODS 集合中方法 则通过setColumn 方法记录到 BaseJdbcLogger 中
                //定义的三个 column 集合
                if ("setNull".equals(method.getName())) {
                    setColumn(params[0], null);
                } else {
                    setColumn(params[0], params[1]);
                }
                return method.invoke(statement, params);
            } else if ("getResultSet".equals(method.getName())) {
                //如果调用的是 getResultSet 方法，则为ResultSet创建代理对象
                ResultSet rs = (ResultSet) method.invoke(statement, params);
                return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
            } else if ("getUpdateCount".equals(method.getName())) {
                //如果调用getUpdateCount方法，则通过日志框架输出其结果
                int updateCount = (Integer) method.invoke(statement, params);
                if (updateCount != -1) {
                    debug("   Updates: " + updateCount, false);
                }
                return updateCount;
            } else {
                return method.invoke(statement, params);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    /*
     * Creates a logging version of a PreparedStatement
     *
     * @param stmt - the statement
     * @param sql  - the sql statement
     * @return - the proxy
     */
    public static PreparedStatement newInstance(PreparedStatement stmt, Log statementLog, int queryStack) {
        InvocationHandler handler = new PreparedStatementLogger(stmt, statementLog, queryStack);
        ClassLoader cl = PreparedStatement.class.getClassLoader();
        return (PreparedStatement) Proxy.newProxyInstance(cl, new Class[]{PreparedStatement.class, CallableStatement.class}, handler);
    }

    /*
     * Return the wrapped prepared statement
     *
     * @return the PreparedStatement
     */
    public PreparedStatement getPreparedStatement() {
        return statement;
    }

}
