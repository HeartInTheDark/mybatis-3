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
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 *
 * 封装了Mapper接口中对应方法的信息，以及对应SQL语句的名称
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperMethod {

    private final SqlCommand command;//记录了SQL语句的名称和类型
    private final MethodSignature method;//Mapper接口中对应方法的相关信息

    public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
        this.command = new SqlCommand(config, mapperInterface, method);
        this.method = new MethodSignature(config, mapperInterface, method);
    }

    public Object execute(SqlSession sqlSession, Object[] args) {
        Object result;
        switch (command.getType()) {//根据SQL语句的类型调用SqlSession对应的方法
            case INSERT: {
                //使用paramNameResolver处理args[]数组（用户传入的实参列表），将用户传入的实参与指定的参
                // 数名称关联起来
                Object param = method.convertArgsToSqlCommandParam(args);
                //调用SqlSession.insert()方法，rowCountResult()方法会根据method字段中记录的方法的
                //返回值类型对结果进行转换
                result = rowCountResult(sqlSession.insert(command.getName(), param));
                break;
            }
            case UPDATE: {
                //与INSERT一致....
                Object param = method.convertArgsToSqlCommandParam(args);
                result = rowCountResult(sqlSession.update(command.getName(), param));
                break;
            }
            case DELETE: {
                Object param = method.convertArgsToSqlCommandParam(args);
                result = rowCountResult(sqlSession.delete(command.getName(), param));
                break;
            }
            case SELECT:
                //处理返回值为void且ResultSet通过ResultHandler处理的方法
                if (method.returnsVoid() && method.hasResultHandler()) {
                    executeWithResultHandler(sqlSession, args);
                    result = null;
                } else if (method.returnsMany()) { //处理返回值为集合或者数组的方法
                    result = executeForMany(sqlSession, args);
                } else if (method.returnsMap()) {//处理返回值为map的方法
                    result = executeForMap(sqlSession, args);
                } else if (method.returnsCursor()) {//处理返回值为Cursor的方法
                    result = executeForCursor(sqlSession, args);
                } else {//处理单一对象的方法
                    Object param = method.convertArgsToSqlCommandParam(args);
                    result = sqlSession.selectOne(command.getName(), param);
                }
                break;
            case FLUSH:
                result = sqlSession.flushStatements();
                break;
            default:
                throw new BindingException("Unknown execution method for: " + command.getName());
        }
        if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
            throw new BindingException("Mapper method '" + command.getName()
                    + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
        }
        return result;
    }

    private Object rowCountResult(int rowCount) {
        final Object result;
        if (method.returnsVoid()) {
            result = null;//Mapper接口中对应方法的返回值为void
        } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
            result = rowCount;//Mapper接口中对应方法的返回值为int 或者Integer
        } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
            result = (long) rowCount;//Mapper接口中对应方法的返回值为 long 或者 Long
        } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
            result = rowCount > 0;//Mapper接口中对应方法的返回值为 boolean 或者 Boolean
        } else {//以上都不满足 抛出异常
            throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
        }
        return result;
    }

    private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
        //获取SQL语句对应的MappedStatement对象，MappedStatement中记录了SQL的相关信息
        MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());

        //当使用ResultHandler处理结果集时，必须指定ResultMap或者ResultType
        if (!StatementType.CALLABLE.equals(ms.getStatementType())
                && void.class.equals(ms.getResultMaps().get(0).getType())) {
            throw new BindingException("method " + command.getName()
                    + " needs either a @ResultMap annotation, a @ResultType annotation,"
                    + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
        }
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) { //检测参数中是否有RowBounds类型的参数
            //获取RowBounds对象，根据rowBoundsIndex字段指定位置，从args中查找
            RowBounds rowBounds = method.extractRowBounds(args);
            //调用SqlSession.select()方法，执行查询，并由指定的ResultHandler处理结果对象
            sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
        } else {
            sqlSession.select(command.getName(), param, method.extractResultHandler(args));
        }
    }

    private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
        List<E> result;
        Object param = method.convertArgsToSqlCommandParam(args);//参数列表转换
        if (method.hasRowBounds()) {//检测参数中是否有RowBounds类型的参数
            RowBounds rowBounds = method.extractRowBounds(args);
            //调用SqlSession.select()方法，完成查询
            result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.<E>selectList(command.getName(), param);
        }
        // issue #510 Collections & arrays support
        //将结果转换为Collection集合
        if (!method.getReturnType().isAssignableFrom(result.getClass())) {
            if (method.getReturnType().isArray()) {
                return convertToArray(result);
            } else {
                return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
            }
        }
        return result;
    }

    private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
        Cursor<T> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.<T>selectCursor(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.<T>selectCursor(command.getName(), param);
        }
        return result;
    }

    private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
        //使用了ObjectFactory，通过反射创建集合对象
        Object collection = config.getObjectFactory().create(method.getReturnType());
        //构建MetaObject对象
        MetaObject metaObject = config.newMetaObject(collection);
        //实际上就是调用Collection.addAll()方法
        metaObject.addAll(list);
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <E> Object convertToArray(List<E> list) {
        //获取数组的元素类型
        Class<?> arrayComponentType = method.getReturnType().getComponentType();
        //创建数组对象
        Object array = Array.newInstance(arrayComponentType, list.size());
        if (arrayComponentType.isPrimitive()) { //将每一项都添加到数组中
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        } else {
            return list.toArray((E[]) array);
        }
    }

    private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
        Map<K, V> result;
        Object param = method.convertArgsToSqlCommandParam(args);//转换实参列表
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            //调用SqlSession.select()方法，完成查询
            result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
        } else {
            result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
        }
        return result;
    }

    public static class ParamMap<V> extends HashMap<String, V> {

        private static final long serialVersionUID = -2212268410512043556L;

        @Override
        public V get(Object key) {
            if (!super.containsKey(key)) {
                throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
            }
            return super.get(key);
        }

    }

    public static class SqlCommand {

        private final String name;  //记录SQL语句的名称
        private final SqlCommandType type;//记录SQL语句的类型

        public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {

            final String methodName = method.getName();
            final Class<?> declaringClass = method.getDeclaringClass();
            //SQL语句的名称是由Mapper接口的名称与对应方法名称组成的
            MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
                    configuration);
            if (ms == null) {
                if (method.getAnnotation(Flush.class) != null) { //处理@Flush注解
                    name = null;
                    type = SqlCommandType.FLUSH;
                } else {
                    throw new BindingException("Invalid bound statement (not found): "
                            + mapperInterface.getName() + "." + methodName);
                }
            } else {
                //初始化name和type
                name = ms.getId();
                type = ms.getSqlCommandType();
                if (type == SqlCommandType.UNKNOWN) {
                    throw new BindingException("Unknown execution method for: " + name);
                }
            }
        }

        public String getName() {
            return name;
        }

        public SqlCommandType getType() {
            return type;
        }

        private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
                                                       Class<?> declaringClass, Configuration configuration) {
            String statementId = mapperInterface.getName() + "." + methodName;
            if (configuration.hasStatement(statementId)) {//检测是否有该名称的SQL语句
                //从Configuration.mappedStatements中查找对应的MappedStatement对象
                //MappedStatement对象中封装了SQL语句相关的信息，在MyBatis初始化时创建
                return configuration.getMappedStatement(statementId);
            } else if (mapperInterface.equals(declaringClass)) {
                return null;
            }
            for (Class<?> superInterface : mapperInterface.getInterfaces()) {
                if (declaringClass.isAssignableFrom(superInterface)) {
                    MappedStatement ms = resolveMappedStatement(superInterface, methodName,
                            declaringClass, configuration);
                    if (ms != null) {
                        return ms;
                    }
                }
            }
            return null;
        }
    }

    //封装了Mapper接口中定义方法的相关信息
    public static class MethodSignature {

        private final boolean returnsMany;//返回值类型是否为Collection类型或是数组类型
        private final boolean returnsMap;//返回值是否为Map类型
        private final boolean returnsVoid;//返回值是否为void
        private final boolean returnsCursor;//返回值是否为Cursor类型
        private final Class<?> returnType;//返回值类型
        private final String mapKey;//返回值类型是map，则该字段记录了作为key的列名
        private final Integer resultHandlerIndex;//用来标记该方法参数列表中resultHandler位置
        private final Integer rowBoundsIndex;//用来标记该方法参数列表中 rowBounds位置
        private final ParamNameResolver paramNameResolver;//该方法对应的ParamNameResolver对象

        public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
            //解析方法的返回值类型
            Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
            if (resolvedReturnType instanceof Class<?>) {
                this.returnType = (Class<?>) resolvedReturnType;
            } else if (resolvedReturnType instanceof ParameterizedType) {
                this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
            } else {
                this.returnType = method.getReturnType();
            }
            //初始化以下字段
            this.returnsVoid = void.class.equals(this.returnType);
            this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
            this.returnsCursor = Cursor.class.equals(this.returnType);
            this.mapKey = getMapKey(method);
            this.returnsMap = this.mapKey != null;
            this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
            this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
            this.paramNameResolver = new ParamNameResolver(configuration, method);
        }

        //负责将args[]数组转换成SQL语句对应的参数列表
        public Object convertArgsToSqlCommandParam(Object[] args) {
            return paramNameResolver.getNamedParams(args);
        }

        public boolean hasRowBounds() {
            return rowBoundsIndex != null;
        }

        public RowBounds extractRowBounds(Object[] args) {
            return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
        }

        public boolean hasResultHandler() {
            return resultHandlerIndex != null;
        }

        public ResultHandler extractResultHandler(Object[] args) {
            return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
        }

        public String getMapKey() {
            return mapKey;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public boolean returnsMany() {
            return returnsMany;
        }

        public boolean returnsMap() {
            return returnsMap;
        }

        public boolean returnsVoid() {
            return returnsVoid;
        }

        public boolean returnsCursor() {
            return returnsCursor;
        }

        //查找指定类型的参数在参数列列表中的位置
        private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
            Integer index = null;
            final Class<?>[] argTypes = method.getParameterTypes();
            for (int i = 0; i < argTypes.length; i++) {//遍历MethodSignature对应方法的参数列表
                if (paramType.isAssignableFrom(argTypes[i])) {
                    if (index == null) {//记录paramType类型的参数在参数列表中的位置
                        index = i;
                    } else {// rowBounds和resultHandler类型的参数只能有一个
                        throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
                    }
                }
            }
            return index;
        }

        private String getMapKey(Method method) {
            String mapKey = null;
            if (Map.class.isAssignableFrom(method.getReturnType())) {
                final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
                if (mapKeyAnnotation != null) {
                    mapKey = mapKeyAnnotation.value();
                }
            }
            return mapKey;
        }
    }

}
