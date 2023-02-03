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
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 * CachingExecutor是一个Executor接口的装饰器，它为Executor对象增加了二级缓存的相关功能
 */
public class CachingExecutor implements Executor {

    private final Executor delegate;
    private final TransactionalCacheManager tcm = new TransactionalCacheManager();

    public CachingExecutor(Executor delegate) {
        this.delegate = delegate;
        delegate.setExecutorWrapper(this);
    }

    @Override
    public Transaction getTransaction() {
        return delegate.getTransaction();
    }

    @Override
    public void close(boolean forceRollback) {
        try {
            //issues #499, #524 and #573
            if (forceRollback) {
                tcm.rollback();
            } else {
                tcm.commit();
            }
        } finally {
            delegate.close(forceRollback);
        }
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public int update(MappedStatement ms, Object parameterObject) throws SQLException {
        flushCacheIfRequired(ms);
        return delegate.update(ms, parameterObject);
    }

    /**
     * CachingExecutor.query（）方法执行查询操作的步骤如下：
     * （1）获取BoundSql对象，创建查询语句对应的CacheKey对象。
     *
     * （2）检测是否开启了二级缓存，如果没有开启二级缓存，则直接调用底层Executor对象的query（）方法查询数据库。如果开启了二级缓存，则继续后面的步骤。
     *
     * （3）检测查询操作是否包含输出类型的参数，如果是这种情况，则报错。
     *
     * （4）调用TransactionalCacheManager.getObject（）方法查询二级缓存，如果二级缓存中查找到相应的结果对象，则直接将该结果对象返回。
     *
     * （5）如果二级缓存没有相应的结果对象，则调用底层Executor对象的query（）方法，正如前面介绍的，它会先查询一级缓存，一级缓存未命中时，才会查询数据库。
     * 最后还会将得到的结果对象放入TransactionalCache.entriesToAddOnCommit集合中保存。
     */
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
        //步骤1：获取BoundSql对象
        BoundSql boundSql = ms.getBoundSql(parameterObject);
        //创建CacheKey对象
        CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
        return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    @Override
    public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
        flushCacheIfRequired(ms);
        return delegate.queryCursor(ms, parameter, rowBounds);
    }

    @Override
    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
            throws SQLException {
        Cache cache = ms.getCache();//获取查询语句所在命名空间对应的二级缓存
        if (cache != null) {//步骤2：是否开启了二级缓存功能
            flushCacheIfRequired(ms);//根据<select>节点的配置，决定是否需要清空二级缓存
            //检测SQL节点的useCache配置以及是否使用了resultHandler配置
            if (ms.isUseCache() && resultHandler == null) {
                //步骤3：二级缓存不能保存输出类型的参数，如果查询操作调用了包含输出参数的存储过程，则报错
                ensureNoOutParams(ms, boundSql);
                @SuppressWarnings("unchecked")
                //步骤4：查询二级缓存
                List<E> list = (List<E>) tcm.getObject(cache, key);
                if (list == null) {
                    //步骤5：二级缓存没有相应的结果对象，调用封装的Executor对象的query()方法
                    list = delegate.<E>query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
                    //将查询结果保存到TransactionalCache.entriesToAddOnCommit集合中
                    tcm.putObject(cache, key, list); // issue #578 and #116
                }
                return list;
            }
        }
        return delegate.<E>query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        return delegate.flushStatements();
    }

    @Override
    public void commit(boolean required) throws SQLException {
        delegate.commit(required);//调用底层的Executor提交事务
        tcm.commit();//遍历所有相关的TransactionalCache对象执行commit()方法
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        try {
            delegate.rollback(required);//调用底层的Executor回滚事务
        } finally {
            if (required) {
                tcm.rollback();//遍历所有相关的TransactionalCache对象执行rollback()方法
            }
        }
    }

    private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
        if (ms.getStatementType() == StatementType.CALLABLE) {
            for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                if (parameterMapping.getMode() != ParameterMode.IN) {
                    throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
                }
            }
        }
    }

    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
    }

    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return delegate.isCached(ms, key);
    }

    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
        delegate.deferLoad(ms, resultObject, property, key, targetType);
    }

    @Override
    public void clearLocalCache() {
        delegate.clearLocalCache();
    }

    private void flushCacheIfRequired(MappedStatement ms) {
        Cache cache = ms.getCache();
        if (cache != null && ms.isFlushCacheRequired()) {
            tcm.clear(cache);
        }
    }

    @Override
    public void setExecutorWrapper(Executor executor) {
        throw new UnsupportedOperationException("This method should not be called");
    }

}
