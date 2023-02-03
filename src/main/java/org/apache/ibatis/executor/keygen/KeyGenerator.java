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
package org.apache.ibatis.executor.keygen;

import java.sql.Statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * @author Clinton Begin
 *
 * 默认情况下，insert语句并不会返回自动生成的主键，而是返回插入记录的条数。
 * 如果业务逻辑需要获取插入记录时产生的自增主键，则可以使用Mybatis提供的KeyGenerator接口
 *
 * 不同的数据库产品对应的主键生成策略不一样，
 * 例如，Oracle、DB2等数据库产品是通过sequence实现自增id的，在执行insert语句之前必须明确指定主键的值；
 * 而MySQL、Postgresql等数据库在执行insert语句时，可以不指定主键，在插入过程中由数据库自动生成自增主键。
 * KeyGenerator接口针对这些不同的数据库产品提供了对应的处理方法
 */
public interface KeyGenerator {

    //在执行insert之前执行，设置属性order="BEFORE"
    void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

    //在执行insert之后执行，设置属性order="AFTER"
    void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

}
