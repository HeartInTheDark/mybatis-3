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
package org.apache.ibatis.session;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * @author Larry Meadors
 *
 * SqlSessionManager同时实现了SqlSession接口和SqlSessionFactory接口，
 * 也就同时提供了SqlSessionFactory创建SqlSession对象以及SqlSession操纵数据库的功能
 */
public class SqlSessionManager implements SqlSessionFactory, SqlSession {

    private final SqlSessionFactory sqlSessionFactory;//底层封装的SqlSessionFactory对象

    //localSqlSession中记录的SqlSession对象的代理对象，在SqlSessionManager初始化时，
    //会使用JDK动态代理的方式为localSqlSession创建代理对象
    private final SqlSession sqlSessionProxy;

    private final ThreadLocal<SqlSession> localSqlSession = new ThreadLocal<SqlSession>();//ThreadLocal变量，记录一个与当前线程绑定的SqlSession对象

    private SqlSessionManager(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        //使用动态代理的方式生成Sg1 Session的代理对象
        this.sqlSessionProxy = (SqlSession) Proxy.newProxyInstance(
                SqlSessionFactory.class.getClassLoader(),
                new Class[]{SqlSession.class},
                new SqlSessionInterceptor());
    }

    //通过newInstance()方法创建SqlSessionManager对象
    public static SqlSessionManager newInstance(Reader reader) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, null, null));
    }

    public static SqlSessionManager newInstance(Reader reader, String environment) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, environment, null));
    }

    public static SqlSessionManager newInstance(Reader reader, Properties properties) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, null, properties));
    }

    public static SqlSessionManager newInstance(InputStream inputStream) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, null, null));
    }

    public static SqlSessionManager newInstance(InputStream inputStream, String environment) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, environment, null));
    }

    public static SqlSessionManager newInstance(InputStream inputStream, Properties properties) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, null, properties));
    }

    public static SqlSessionManager newInstance(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionManager(sqlSessionFactory);
    }

    public void startManagedSession() {
        this.localSqlSession.set(openSession());
    }

    public void startManagedSession(boolean autoCommit) {
        this.localSqlSession.set(openSession(autoCommit));
    }

    public void startManagedSession(Connection connection) {
        this.localSqlSession.set(openSession(connection));
    }

    public void startManagedSession(TransactionIsolationLevel level) {
        this.localSqlSession.set(openSession(level));
    }

    public void startManagedSession(ExecutorType execType) {
        this.localSqlSession.set(openSession(execType));
    }

    public void startManagedSession(ExecutorType execType, boolean autoCommit) {
        this.localSqlSession.set(openSession(execType, autoCommit));
    }

    public void startManagedSession(ExecutorType execType, TransactionIsolationLevel level) {
        this.localSqlSession.set(openSession(execType, level));
    }

    public void startManagedSession(ExecutorType execType, Connection connection) {
        this.localSqlSession.set(openSession(execType, connection));
    }

    public boolean isManagedSessionStarted() {
        return this.localSqlSession.get() != null;
    }

    @Override
    public SqlSession openSession() {
        return sqlSessionFactory.openSession();
    }

    @Override
    public SqlSession openSession(boolean autoCommit) {
        return sqlSessionFactory.openSession(autoCommit);
    }

    @Override
    public SqlSession openSession(Connection connection) {
        return sqlSessionFactory.openSession(connection);
    }

    @Override
    public SqlSession openSession(TransactionIsolationLevel level) {
        return sqlSessionFactory.openSession(level);
    }

    @Override
    public SqlSession openSession(ExecutorType execType) {
        return sqlSessionFactory.openSession(execType);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
        return sqlSessionFactory.openSession(execType, autoCommit);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
        return sqlSessionFactory.openSession(execType, level);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, Connection connection) {
        return sqlSessionFactory.openSession(execType, connection);
    }

    @Override
    public Configuration getConfiguration() {
        return sqlSessionFactory.getConfiguration();
    }

    @Override
    public <T> T selectOne(String statement) {
        return sqlSessionProxy.<T>selectOne(statement);
    }

    @Override
    public <T> T selectOne(String statement, Object parameter) {
        return sqlSessionProxy.<T>selectOne(statement, parameter);
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
        return sqlSessionProxy.<K, V>selectMap(statement, mapKey);
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
        return sqlSessionProxy.<K, V>selectMap(statement, parameter, mapKey);
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
        return sqlSessionProxy.<K, V>selectMap(statement, parameter, mapKey, rowBounds);
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement) {
        return sqlSessionProxy.selectCursor(statement);
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement, Object parameter) {
        return sqlSessionProxy.selectCursor(statement, parameter);
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
        return sqlSessionProxy.selectCursor(statement, parameter, rowBounds);
    }

    @Override
    public <E> List<E> selectList(String statement) {
        return sqlSessionProxy.<E>selectList(statement);
    }

    @Override
    public <E> List<E> selectList(String statement, Object parameter) {
        return sqlSessionProxy.<E>selectList(statement, parameter);
    }

    @Override
    public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
        return sqlSessionProxy.<E>selectList(statement, parameter, rowBounds);
    }

    @Override
    public void select(String statement, ResultHandler handler) {
        sqlSessionProxy.select(statement, handler);
    }

    @Override
    public void select(String statement, Object parameter, ResultHandler handler) {
        sqlSessionProxy.select(statement, parameter, handler);
    }

    @Override
    public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
        sqlSessionProxy.select(statement, parameter, rowBounds, handler);
    }

    @Override
    public int insert(String statement) {
        return sqlSessionProxy.insert(statement);
    }

    @Override
    public int insert(String statement, Object parameter) {
        return sqlSessionProxy.insert(statement, parameter);
    }

    @Override
    public int update(String statement) {
        return sqlSessionProxy.update(statement);
    }

    @Override
    public int update(String statement, Object parameter) {
        return sqlSessionProxy.update(statement, parameter);
    }

    @Override
    public int delete(String statement) {
        return sqlSessionProxy.delete(statement);
    }

    @Override
    public int delete(String statement, Object parameter) {
        return sqlSessionProxy.delete(statement, parameter);
    }

    @Override
    public <T> T getMapper(Class<T> type) {
        return getConfiguration().getMapper(type, this);
    }

    @Override
    public Connection getConnection() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot get connection.  No managed session is started.");
        }
        return sqlSession.getConnection();
    }

    @Override
    public void clearCache() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot clear the cache.  No managed session is started.");
        }
        sqlSession.clearCache();
    }

    @Override
    public void commit() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot commit.  No managed session is started.");
        }
        sqlSession.commit();
    }

    @Override
    public void commit(boolean force) {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot commit.  No managed session is started.");
        }
        sqlSession.commit(force);
    }

    @Override
    public void rollback() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
        }
        sqlSession.rollback();
    }

    @Override
    public void rollback(boolean force) {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
        }
        sqlSession.rollback(force);
    }

    @Override
    public List<BatchResult> flushStatements() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
        }
        return sqlSession.flushStatements();
    }

    @Override
    public void close() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot close.  No managed session is started.");
        }
        try {
            sqlSession.close();
        } finally {
            localSqlSession.set(null);
        }
    }

    /**
     * SqlSessionManager中实现的SqlSession接口方法，例如select*（）、update（）等，都是直接调用sqlSessionProxy
     * 字段记录的SqlSession代理对象的相应方法实现的。在创建该代理对象时使用的InvocationHandler对象是SqlSessionInterceptor对象
     */
    private class SqlSessionInterceptor implements InvocationHandler {
        public SqlSessionInterceptor() {
            // Prevent Synthetic Access
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //获取当前线程绑定的SqlSession对象
            final SqlSession sqlSession = SqlSessionManager.this.localSqlSession.get();
            if (sqlSession != null) {//第二种模式
                try {
                    //调用真正的SqlSession对象，完成数据库的相关操作
                    return method.invoke(sqlSession, args);
                } catch (Throwable t) {
                    throw ExceptionUtil.unwrapThrowable(t);
                }
            } else {//第一种模式
                //如果当前线程未绑定SqlSession对象，则创建新的SqlSession对象
                final SqlSession autoSqlSession = openSession();
                try {
                    //通过新建的SqlSession对象完成数据库操作
                    final Object result = method.invoke(autoSqlSession, args);
                    autoSqlSession.commit();//提交事务
                    return result;
                } catch (Throwable t) {
                    autoSqlSession.rollback();//回滚事务
                    throw ExceptionUtil.unwrapThrowable(t);
                } finally {
                    autoSqlSession.close();//关闭上面创建的SqlSession对象
                }
            }
        }
    }

}
