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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 * <p>
 * TransactionalCache继承了Cache接口，主要用于保存在某个SqlSession的某个事务中需要向某个二级缓存中添加的缓存数据
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

    private static final Log log = LogFactory.getLog(TransactionalCache.class);

    private final Cache delegate;//底层封装的二级缓存所对应的Cache对象
    private boolean clearOnCommit;//当该字段为true时，则表示当前TransactionalCache不可查询，且提交事务时会将底层Cache清空
    private final Map<Object, Object> entriesToAddOnCommit;//暂时记录添加到TransactionalCache中的数据。在事务捉交时，会将其中的数据添加到二级缓存中
    private final Set<Object> entriesMissedInCache;//记录缓存未命中的CacheKey对象

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
        this.clearOnCommit = false;
        this.entriesToAddOnCommit = new HashMap<Object, Object>();
        this.entriesMissedInCache = new HashSet<Object>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    /**
     * TransactionalCache.getObject（）方法，它首先会查询底层的二级缓存，并将未命中的key
     * 记录到entriesMissedInCache中，之后会根据clearOnCommit字段的值决定具体的返回值
     */
    @Override
    public Object getObject(Object key) {
        // issue #116
        //查询底层的Cache是否包含指定的key
        Object object = delegate.getObject(key);
        if (object == null) {
            //如果底层缓存对象中不包含该缓存项，则将该key记录到entriesMissedInCache集合中
            entriesMissedInCache.add(key);
        }
        // issue #146
        //如果clearOnCommit为true,则当前TransactionalCache不可查询，始终返回null
        if (clearOnCommit) {
            return null;
        } else {
            return object;//返回从底层Cache中查询到的对象
        }
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return null;
    }

    /**
     * TransactionalCache.putObject（）方法并没有直接将结果对象记录到其封装的二级缓存中，
     * 而是暂时保存在entriesToAddOnCommit集合中，在事务提交时才会将这些结果对象从entriesToAddOnCommit集合添加到二级缓存中
     */
    @Override
    public void putObject(Object key, Object object) {
        entriesToAddOnCommit.put(key, object);//将缓存项暂存在entriesToAddonCommit集合中
    }

    @Override
    public Object removeObject(Object key) {
        return null;
    }

    //TransactionalCache.clear（）方法会清空entriesToAddOnCommit集合，并设置clearOnCommit 为true
    @Override
    public void clear() {
        clearOnCommit = true;
        entriesToAddOnCommit.clear();
    }

    //TransactionalCache.commit（）方法会根据clearOnCommit字段的值决定是否清空二级缓存，
    // 然后调用flushPendingEntries（）方法将entriesToAddOnCommit集合中记录的结果对象保存到二级缓存中
    public void commit() {
        if (clearOnCommit) {//在事务提交前，清空二级缓存
            delegate.clear();
        }
        flushPendingEntries();//将entriesToAddOnCommit集合中的数据保存到二级缓存
        //重置clearOnCommit为false,并清空entriesToAddOnCommit、entriesMissedInCache集合
        reset();
    }

    //TransactionalCache.rollback（）方法会将entriesMissedInCache集合中记录的缓存项从二级缓存中删除，
    // 并清空entriesToAddOnCommit集合和entriesMissedInCache集合
    public void rollback() {
        //将entriesMissedInCache集合中记录的缓存项从二级缓存中剧除
        unlockMissedEntries();
        //重置clearOnCommit为false,并清空entriesToAddOnCommit、entriesMissedInCache集合
        reset();
    }

    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
        entriesMissedInCache.clear();
    }

    private void flushPendingEntries() {
        //遍历entriesToAddOnCommit集合，将其中记录的缓存项添加到二级缓存中
        for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
            delegate.putObject(entry.getKey(), entry.getValue());
        }
        //遍历entriesMissedInCache集合，将entriesToAddonCommit集合中不包含的缓存项添加到二级缓存中
        for (Object entry : entriesMissedInCache) {
            if (!entriesToAddOnCommit.containsKey(entry)) {
                delegate.putObject(entry, null);
            }
        }
    }

    private void unlockMissedEntries() {
        for (Object entry : entriesMissedInCache) {
            try {
                delegate.removeObject(entry);
            } catch (Exception e) {
                log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
                        + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
            }
        }
    }

}
