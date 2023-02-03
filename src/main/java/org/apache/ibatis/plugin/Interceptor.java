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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * @author Clinton Begin
 *
 * Interceptor接口是MyBatis插件模块的核心
 */
public interface Interceptor {

    Object intercept(Invocation invocation) throws Throwable;//执行拦截逻辑的方法

    Object plugin(Object target);//决定是否触发intercept()方法

    void setProperties(Properties properties);//根据配置初始化Interceptor对象

}
