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
package org.apache.ibatis.reflection;

public interface ReflectorFactory {

    boolean isClassCacheEnabled();//检测该ReflectorFactory对象是否缓存Reflector对象

    void setClassCacheEnabled(boolean classCacheEnabled);//设置是否缓存Reflector对象

    Reflector findForClass(Class<?> type); //创建指定Class对应的Reflector对象
}