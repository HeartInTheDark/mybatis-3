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
package org.apache.ibatis.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Clinton Begin
 *
 * @Signature 注解中都标识了该插件需要拦截的方法信息，
 * 其中@Signature注解的type属性指定需要拦截的类型，
 * method属性指定需要拦截的方法，
 * args属性指定了被拦截方法的参数列表。
 * 通过这三个属性值，@Signature注解就可以表示一个方法签名，唯一确定一个方法
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Signature {
    Class<?> type();//指定需要拦截的类型

    String method();//指定需要拦截的方法

    Class<?>[] args();//指定了被拦截方法的参数列表
}