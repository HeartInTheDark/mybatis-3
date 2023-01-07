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
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator
 * 最少使用算法进行缓存的装饰器
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

    private final Cache delegate;// 被装饰的底层Cache对象
    private Map<Object, Object> keyMap;//是一个有序的HashMap，用于记录key最近的使用情况
    private Object eldestKey;//记录最少被使用的缓存项

    public LruCache(Cache delegate) {
        this.delegate = delegate;
        setSize(1024);
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    public void setSize(final int size) { //重新设置缓存大小时，会重置keyMap字段
        //LinkedHashMap构造函数的第三个参数，true代表该LinkedHashMap记录顺序是access-order（即每一次访问都
        // 会访问当前元素的后一个元素）
        keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
            private static final long serialVersionUID = 4267176411845948333L;

            //当调用put方法时会调用该方法
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
                boolean tooBig = size() > size;
                if (tooBig) {//如果已经达到上限，则更新eldestKey字段，后面会删除该项
                    eldestKey = eldest.getKey();
                }
                return tooBig;
            }
        };
    }

    @Override
    public void putObject(Object key, Object value) {
        delegate.putObject(key, value); //添加缓存项
        cycleKeyList(key); //清除最久未使用的缓存项
    }

    @Override
    public Object getObject(Object key) {
        keyMap.get(key); //touch  修改LinkedHashMap中记录的顺序
        return delegate.getObject(key);
    }

    @Override
    public Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        keyMap.clear();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return null;
    }

    private void cycleKeyList(Object key) {
        keyMap.put(key, key);
        if (eldestKey != null) { //不为空  则证明已经达到上限
            delegate.removeObject(eldestKey);//清除最久未使用的缓存项
            eldestKey = null;
        }
    }

}
