/**
 * Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 *
 * PreparedStatementHandler底层依赖于java.sql.PreparedStatement对象来完成数据库的相关操作。
 * 在SimpleStatementHandler.parameterize（）方法中，会调用前面介绍的ParameterHandler.setParameters（）方法完成SQL语句的参数绑定
 */
public class PreparedStatementHandler extends BaseStatementHandler {

    public PreparedStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public int update(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        ps.execute();
        int rows = ps.getUpdateCount();
        Object parameterObject = boundSql.getParameterObject();
        KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
        keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
        return rows;
    }

    @Override
    public void batch(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        ps.addBatch();
    }

    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        ps.execute();
        return resultSetHandler.<E>handleResultSets(ps);
    }

    @Override
    public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        ps.execute();
        return resultSetHandler.<E>handleCursorResultSets(ps);
    }

    //PreparedStatementHandler.instantiateStatement（）方法直接调用JDBC Connection的prepareStatement（）方法创建PreparedStatement对象
    @Override
    protected Statement instantiateStatement(Connection connection) throws SQLException {
        String sql = boundSql.getSql();//获取执行的SQL语句
        //根据MappedStatement,keyGenerator字段的值，创建PreparedStatement对象
        if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
            String[] keyColumnNames = mappedStatement.getKeyColumns();
            if (keyColumnNames == null) {
                return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);//返回数据库生成的主键
            } else {
                //在insert语句执行完成之后，会将keyColumnNames指定的列返回
                return connection.prepareStatement(sql, keyColumnNames);
            }
        } else if (mappedStatement.getResultSetType() != null) {
            //设置结果集是否可以滚动以及其游标是否可以上下移动，设置结果集是否可更新
            return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
        } else {
            return connection.prepareStatement(sql);//创建普通的PreparedStatement对象
        }
    }

    @Override
    public void parameterize(Statement statement) throws SQLException {
        parameterHandler.setParameters((PreparedStatement) statement);
    }

}
