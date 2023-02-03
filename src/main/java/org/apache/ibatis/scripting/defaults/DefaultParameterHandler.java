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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * <p>
 * 在ParameterHandler接口中只定义了一个setParameters（）方法，该方法主要负责调用PreparedStatement.set*（）
 * 方法为SQL语句绑定实参。MyBatis只为ParameterHandler接口提供了唯一一个实现类
 */
public class DefaultParameterHandler implements ParameterHandler {

    //TypeHandlerRegistry对象，管理MyBatis中的全部TypeHandler对象
    private final TypeHandlerRegistry typeHandlerRegistry;

    //MappedStatement对象，其中记录SQL节点相应的配置信息
    private final MappedStatement mappedStatement;

    //用户传入的实参对象
    private final Object parameterObject;

    //对应的BoundSql对象，需要设置参数的PreparedStatement对象，就是根据该BoundSql中记录的SQL
    //语句创建的，BoundSql中也记录了对应参数的名称和相关属性
    private final BoundSql boundSql;
    private final Configuration configuration;

    public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        this.mappedStatement = mappedStatement;
        this.configuration = mappedStatement.getConfiguration();
        this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
        this.parameterObject = parameterObject;
        this.boundSql = boundSql;
    }

    @Override
    public Object getParameterObject() {
        return parameterObject;
    }

    //在DefaultParameterHandler.setParameters（）方法中会遍历
    //BoundSql.parameterMappings集合中记录的ParameterMapping对象，并根据其中记录的参数名称查找相应实参，然后与SQL语句绑定
    @Override
    public void setParameters(PreparedStatement ps) {
        ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
        //取出SQL中的参数映射列表
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings != null) {//检测parameterMappings集合是否为空（略）
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                //过滤掉存储过程中的输出参数
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;//记录绑定实参
                    String propertyName = parameterMapping.getProperty(); //获取参数名称

                    //获取对应的实参值
                    if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) { //整个参数为空
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        value = parameterObject; //实参可以直接通过TypeHandler转换为JdbcType
                    } else {
                        //获取对象中相应的属性值或查找Map对象中的值
                        MetaObject metaObject = configuration.newMetaObject(parameterObject);
                        value = metaObject.getValue(propertyName);
                    }
                    //获取ParameterMapping中设置的TypeHandler对象
                    TypeHandler typeHandler = parameterMapping.getTypeHandler();
                    JdbcType jdbcType = parameterMapping.getJdbcType();
                    if (value == null && jdbcType == null) {
                        jdbcType = configuration.getJdbcTypeForNull();
                    }
                    try {
                        //通过TypeHandler.setParameter()方法会调用PreparedStatement,set*()方法为SQL语句绑定相应的实参
                        typeHandler.setParameter(ps, i + 1, value, jdbcType);
                    } catch (TypeException e) {
                        throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
                    } catch (SQLException e) {
                        throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
                    }
                }
            }
        }
    }

}
