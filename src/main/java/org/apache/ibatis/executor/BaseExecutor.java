/**
 * Copyright 2009-2018 the original author or authors.
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

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 *
 * BaseExecutor是一个实现了Executor接口的抽象类，它实现了Executor接口的大部分方法，其中就使用了模板方法模式。
 * BaseExecutor中主要提供了缓存管理和事务管理的基本功能，继承BaseExecutor的子类只要实现四个基本方法来完成数据
 * 库的相关操作即可，这四个方法分别是：doUpdate（）方法、doQuery（）方法、doQueryCursor（）方法、doFlushStatement（）方法，
 * 其余的功能在BaseExecutor中实现。
 */
public abstract class BaseExecutor implements Executor {

    private static final Log log = LogFactory.getLog(BaseExecutor.class);

    protected Transaction transaction;//Transaction对象，实现事务的提交、回滚和关闭操作
    protected Executor wrapper;//其中封装的Executor对象

    protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;//延时加载队列

    //一级缓存，用于缓存该Executor对象查询结果集映射得到的结果对象
    protected PerpetualCache localCache;//一级缓存，用于缓存输出类型的参数
    protected PerpetualCache localOutputParameterCache;
    protected Configuration configuration;

    //用来记录嵌套查询的层数
    protected int queryStack;
    private boolean closed;

    protected BaseExecutor(Configuration configuration, Transaction transaction) {
        this.transaction = transaction;
        this.deferredLoads = new ConcurrentLinkedQueue<DeferredLoad>();
        this.localCache = new PerpetualCache("LocalCache");
        this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
        this.closed = false;
        this.configuration = configuration;
        this.wrapper = this;
    }

    @Override
    public Transaction getTransaction() {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        return transaction;
    }

    @Override
    public void close(boolean forceRollback) {
        try {
            try {
                rollback(forceRollback);
            } finally {
                if (transaction != null) {
                    transaction.close();
                }
            }
        } catch (SQLException e) {
            // Ignore.  There's nothing that can be done at this point.
            log.warn("Unexpected exception on closing transaction.  Cause: " + e);
        } finally {
            transaction = null;
            deferredLoads = null;
            localCache = null;
            localOutputParameterCache = null;
            closed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * BaseExecutor.update（）方法负责执行insert、update、delete三类SQL语句，它是调用doUpdate（）模板方法实现的。
     * 在调用doUpdate（）方法之前会清空缓存，因为执行SQL语句之后，数据库中的数据已经更新，一级缓存的内容与数据库中的数据
     * 可能已经不一致了，所以需要调用clearLocalCache（）方法清空一级缓存中的“脏数据”。
     */
    @Override
    public int update(MappedStatement ms, Object parameter) throws SQLException {
        ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
        //检测Executor是否关闭
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        //clearLocalCache()方法中会调用localCache、localOutputParameterCache两个
        //缓存的clear()方法完成清理工作。这是影响一级缓存中数据存活时长的第三个方而
        clearLocalCache();
        //调用doUpdate()方法执行SQL语句，该方法也是前面介绍的4个基本方法之一
        return doUpdate(ms, parameter);
    }

    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        return flushStatements(false);
    }

    public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        return doFlushStatements(isRollBack);
    }

    //BaseExecutor.query（）方法会首先创建CacheKey对象，并根据该CacheKey对象查找一级缓存，
    // 如果缓存命中则返回缓存中记录的结果对象，如果缓存未命中则查询数据库得到结果集，之后将结果
    // 集映射成结果对象并保存到一级缓存中，同时返回结果对象
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);//获取BoundSql对象
        CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql); //创建CacheKey对象
        return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }

    //该重载会根据前面创建的CacheKey对象查询一级缓存，如果缓存命中则将缓存中记录的结果对象返回，如果缓存未命中，
    // 则调用doQuery（）方法完成数据库的查询操作并得到结果对象，之后将结果对象记录到一级缓存中
    @SuppressWarnings("unchecked")
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
        //检测当前Executor是否关闭
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        if (queryStack == 0 && ms.isFlushCacheRequired()) {
            //非嵌套查询，并且<select>节点配置的flushCache属性为true时，才会清空一级缓存
            //flushCache配置项是影响一级缓存中结果对象存活时长的第一个方面
            clearLocalCache();
        }
        List<E> list;
        try {
            queryStack++;//增加查询层数
            //查询一级缓存
            list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
            if (list != null) {
                //针对存储过程调用的处理，其功能是：在一级缓存命中时，获取缓存中保存的输出类型参数，
                //并设置到用户传入的实参（parameter)对象中，代码就不贴出来了，读者可以查看源码学习
                handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
            } else {
                //其中会调用doQuery()方法完成数据库查询，并得到映射后的结果对象，doQuery()方法是
                //一个抽象方法，也是上述4个基本方法之一，由BaseExecutor的子类具体实现。
                list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
            }
        } finally {
            queryStack--;//当前查询完成，查询层数减少
        }
        if (queryStack == 0) {
            //在最外层的查询结束时，所有嵌套查询也已经完成，相关缓存项也已经完全加载，所以在这里可以
            //触发DeferredLoad加载一级缓存中记录的嵌套查询的结果对象
            for (DeferredLoad deferredLoad : deferredLoads) {
                deferredLoad.load();
            }
            // issue #601
            deferredLoads.clear();//加载完成后，清空deferredLoads集合
            if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
                // issue #482
                //根据localCacheScope配置决定是否清空一级缓存，localCacheScope配置是影响一级缓存中结果对象存活时长的第二个方面
                clearLocalCache();
            }
        }
        return list;
    }

    //BaseExecutor.queryCursor（）方法的主要功能也是查询数据库，这一点与query（）方法类似，
    // 但它不会直接将结果集映射为结果对象，而是将结果集封装成Cursor对并返回，待用户遍历Cursor时才真正完成结果集的映射操作
    @Override
    public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        return doQueryCursor(ms, parameter, rowBounds, boundSql);
    }

    //负责创建DeferredLoad对象并将其添加到deferredLoads集合中
    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
        //检测
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        //创建DeferredLoad对象
        DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
        if (deferredLoad.canLoad()) {
            //一级缓存中已经记录了指定查询的结果对象，直接从缓存中加载对象，并设置到外层对象中
            deferredLoad.load();
        } else {
            //将DeferredLoad对象添加到deferredLoads队列中，待整个外层查询结束后，再加载该结果对象
            deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
        }
    }

    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        //检测当前Executor是否关闭
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        CacheKey cacheKey = new CacheKey();//创建CacheKey对象
        cacheKey.update(ms.getId());//将MappedStatement的id添加到CacheKey对象中
        cacheKey.update(rowBounds.getOffset());//将offset添加到CacheKey对象中
        cacheKey.update(rowBounds.getLimit());//将limit添加到CacheKey对象中
        cacheKey.update(boundSql.getSql());//将SQL语句添加到CacheKey对象中
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
        // mimic DefaultParameterHandler logic
        //获取用户传入的实参，并添加到CacheKey对象中
        for (ParameterMapping parameterMapping : parameterMappings) {
            if (parameterMapping.getMode() != ParameterMode.OUT) {//过滤掉输出类型的参数
                Object value;
                String propertyName = parameterMapping.getProperty();
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameterObject == null) {
                    value = null;
                } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                    value = parameterObject;
                } else {
                    MetaObject metaObject = configuration.newMetaObject(parameterObject);
                    value = metaObject.getValue(propertyName);
                }
                cacheKey.update(value);//将实参添加到CacheKey对象中
            }
        }
        //如果Environment的id不为空，则将其添加到CacheKey中
        if (configuration.getEnvironment() != null) {
            // issue #176
            cacheKey.update(configuration.getEnvironment().getId());
        }
        return cacheKey;
    }

    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return localCache.getObject(key) != null;//检测缓存中是否缓存了CacheKey对应的对象
    }

    @Override
    public void commit(boolean required) throws SQLException {
        if (closed) {
            throw new ExecutorException("Cannot commit, transaction is already closed");
        }
        clearLocalCache();
        flushStatements();
        if (required) {
            transaction.commit();
        }
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        if (!closed) {
            try {
                clearLocalCache();
                flushStatements(true);
            } finally {
                if (required) {
                    transaction.rollback();
                }
            }
        }
    }

    @Override
    public void clearLocalCache() {
        if (!closed) {
            localCache.clear();
            localOutputParameterCache.clear();
        }
    }

    protected abstract int doUpdate(MappedStatement ms, Object parameter)
            throws SQLException;

    protected abstract List<BatchResult> doFlushStatements(boolean isRollback)
            throws SQLException;

    protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException;

    protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
            throws SQLException;

    protected void closeStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /**
     * Apply a transaction timeout.
     * @param statement a current statement
     * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
     * @since 3.4.0
     * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
     */
    protected void applyTransactionTimeout(Statement statement) throws SQLException {
        StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
    }

    private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
        if (ms.getStatementType() == StatementType.CALLABLE) {
            final Object cachedParameter = localOutputParameterCache.getObject(key);
            if (cachedParameter != null && parameter != null) {
                final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
                final MetaObject metaParameter = configuration.newMetaObject(parameter);
                for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                    if (parameterMapping.getMode() != ParameterMode.IN) {
                        final String parameterName = parameterMapping.getProperty();
                        final Object cachedValue = metaCachedParameter.getValue(parameterName);
                        metaParameter.setValue(parameterName, cachedValue);
                    }
                }
            }
        }
    }

    private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        List<E> list;
        localCache.putObject(key, EXECUTION_PLACEHOLDER);//在缓存中添加占位符
        try {
            //调用doQuery()方法（抽象方法），完成数据库查询操作，并返回结果对象
            list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
        } finally {
            localCache.removeObject(key);//清除占位符
        }
        localCache.putObject(key, list);//将真正的结果对象添加到一级缓存中
        if (ms.getStatementType() == StatementType.CALLABLE) {//是否为存储过程调用
            localOutputParameterCache.putObject(key, parameter);//缓存输出类型的参数
        }
        return list;
    }

    protected Connection getConnection(Log statementLog) throws SQLException {
        Connection connection = transaction.getConnection();
        if (statementLog.isDebugEnabled()) {
            return ConnectionLogger.newInstance(connection, statementLog, queryStack);
        } else {
            return connection;
        }
    }

    @Override
    public void setExecutorWrapper(Executor wrapper) {
        this.wrapper = wrapper;
    }

    private static class DeferredLoad {

        private final MetaObject resultObject;//外层对象对应的MetaObject对象
        private final String property;//延迟加载的属性名称
        private final Class<?> targetType;//延迟加载的属性的类型
        private final CacheKey key;//延迟加载的结果对象在一级缓存中相应的CacheKey对象
        private final PerpetualCache localCache;//一级缓存，与BaseExecutor.localCache字段指向同一PerpetualCache对象
        private final ObjectFactory objectFactory;
        private final ResultExtractor resultExtractor;//ResultExtractor负责结果对象的类型转换

        // issue #781
        public DeferredLoad(MetaObject resultObject,
                            String property,
                            CacheKey key,
                            PerpetualCache localCache,
                            Configuration configuration,
                            Class<?> targetType) {
            this.resultObject = resultObject;
            this.property = property;
            this.key = key;
            this.localCache = localCache;
            this.objectFactory = configuration.getObjectFactory();
            this.resultExtractor = new ResultExtractor(configuration, objectFactory);
            this.targetType = targetType;
        }


        /*
        DeferredLoad.canLoad（）方法负责检测缓存项是否已经完全加载到了缓存中

        首先要说明“完全加载”的含义：BaseExecutor.queryFromDatabase（）方法中，
        开始查询调用doQuery（）方法查询数据库之前，会先在localCache中添加占位符，
        待查询完成之后，才将真正的结果对象放到localCache中缓存，此时该缓存项才算“完全加载”
         */
        public boolean canLoad() {
            return localCache.getObject(key) != null && //检测缓存是否存在指定的结果对象
                    localCache.getObject(key) != EXECUTION_PLACEHOLDER;//检测是否为占位符
        }

        //DeferredLoad.load（）方法负责从缓存中加载结果对象，并设置到外层对象的相应属性中
        public void load() {
            @SuppressWarnings("unchecked")
            // we suppose we get back a List
            //从缓存中查询指定的结果对象
            List<Object> list = (List<Object>) localCache.getObject(key);
            //将缓存的结果对象转换成指定类型
            Object value = resultExtractor.extractObjectFromList(list, targetType);
            resultObject.setValue(property, value);//设置到外层对象的对应属性
        }

    }

}
