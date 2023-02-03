/**
 * Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 * <p>
 * 在传统的JDBC编程中，重用Statement对象是常用的一种优化手段，该优化手段可以减少SQL预编译的开销以及创建和销毁Statement对象的开销，从而提高性能。
 * <p>
 * ReuseExecutor提供了Statement重用的功能，ReuseExecutor中通过statementMap字段（HashMap＜String, Statement＞类型）
 * 缓存使用过的Statement对象，key是SQL语句，value是SQL对应的Statement对象
 * <p>
 * ReuseExecutor.doQuery（）、doQueryCursor（）、doUpdate（）方法的实现与SimpleExecutor中对应方法的实现一样，区别在
 * 于其中调用的prepareStatement（）方法，SimpleExecutor每次都会通过JDBC Connection创建新的Statement对象，而ReuseExecutor则
 * 会先尝试重用StatementMap中缓存的Statement对象
 */
public class ReuseExecutor extends BaseExecutor {

    private final Map<String, Statement> statementMap = new HashMap<String, Statement>();

    public ReuseExecutor(Configuration configuration, Transaction transaction) {
        super(configuration, transaction);
    }

    @Override
    public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
        Configuration configuration = ms.getConfiguration();
        StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
        Statement stmt = prepareStatement(handler, ms.getStatementLog());
        return handler.update(stmt);
    }

    //ReuseExecutor.query（）方法，在select语句执行之后，会立即将结果集映射成结果对象
    @Override
    public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        Configuration configuration = ms.getConfiguration();
        StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
        Statement stmt = prepareStatement(handler, ms.getStatementLog());
        return handler.<E>query(stmt, resultHandler);
    }

    //queryCursor（）方法返回的是Cursor对象，在用户迭代Cursor对象时，才会真正遍历结果集对象并进行映射操作，
    // 这就可能导致使用前面创建的Cursor对象中封装的结果集关闭
    @Override
    protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
        Configuration configuration = ms.getConfiguration();
        StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
        Statement stmt = prepareStatement(handler, ms.getStatementLog());
        return handler.<E>queryCursor(stmt);
    }

    //当事务提交或回滚、连接关闭时，都需要关闭这些缓存的Statement对象。前面介绍BaseExecutor.commit（）、
    // rollback（）和close（）方法时提到，其中都会调用doFlushStatements（）方法，所以在该方法中实现关闭Statement对象的逻辑非常合适
    @Override
    public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
        for (Statement stmt : statementMap.values()) {
            closeStatement(stmt);//遍历statementMap集合并关闭其中的Statement对象
        }
        statementMap.clear();//清空statementMap缓存
        return Collections.emptyList();//返回空集合
    }

    private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
        Statement stmt;
        BoundSql boundSql = handler.getBoundSql();
        String sql = boundSql.getSql();//获取SQL语句
        if (hasStatementFor(sql)) {//检测是否缓存了相同模式的SQL语句所对应的Statement对象
            stmt = getStatement(sql);//获取statementMap集合中缓存的Statement对象
            applyTransactionTimeout(stmt);//修改超时时间
        } else {
            Connection connection = getConnection(statementLog);//获取数据库连接
            //创建新的Statement对象，并缓存到statementMap集合中
            stmt = handler.prepare(connection, transaction.getTimeout());
            putStatement(sql, stmt);
        }
        handler.parameterize(stmt);//处理占位符
        return stmt;
    }

    private boolean hasStatementFor(String sql) {
        try {
            return statementMap.keySet().contains(sql) && !statementMap.get(sql).getConnection().isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private Statement getStatement(String s) {
        return statementMap.get(s);
    }

    private void putStatement(String sql, Statement stmt) {
        statementMap.put(sql, stmt);
    }

}
