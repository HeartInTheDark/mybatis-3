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
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * @author Clinton Begin
 *
 * TransactionalCacheManager用于管理CachingExecutor使用的二级缓存对象，其中只定义了
 * 一个transactionalCaches字段（HashMap＜Cache, TransactionalCache＞类型），它的key是对应的CachingExecutor
 * 使用的二级缓存对象，value是相应的TransactionalCache对象，在该TransactionalCache中封装了对应的二级缓存对象，也就是这里的key
 *
 *
 *  clear（）方法、putObject（）方法、getObject（）方法：调用指定二级缓存对应的TransactionalCache对象的对应方法，
 *  如果transactionalCaches集合中没有对应TransactionalCache对象，则通过getTransactionalCache（）方法创建
 */
public class TransactionalCacheManager {

    private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<Cache, TransactionalCache>();

    public void clear(Cache cache) {
        getTransactionalCache(cache).clear();
    }

    public Object getObject(Cache cache, CacheKey key) {
        return getTransactionalCache(cache).getObject(key);
    }

    public void putObject(Cache cache, CacheKey key, Object value) {
        getTransactionalCache(cache).putObject(key, value);
    }

    public void commit() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.commit();
        }
    }

    public void rollback() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.rollback();
        }
    }

    private TransactionalCache getTransactionalCache(Cache cache) {
        TransactionalCache txCache = transactionalCaches.get(cache);
        if (txCache == null) {
            txCache = new TransactionalCache(cache);//创建TransactionalCache对象
            transactionalCaches.put(cache, txCache);//添加到transactionalCaches集合
        }
        return txCache;
    }

}
