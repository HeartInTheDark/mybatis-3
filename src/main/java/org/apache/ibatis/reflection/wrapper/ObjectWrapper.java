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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 * @DES 对对象的包装，抽象了对象的属性信息，定义了一系列查询对象的属性信息的方法，以及更新属性的方法
 */
public interface ObjectWrapper {

    //如果ObjectWrapper中封装的是普通Bean对象，则调用相应属性的相应getter方法
    //如果封装是集合类，则获取指定的key或下标对应的value值
    Object get(PropertyTokenizer prop);

    //如果ObjectWrapper中封装的是普通Bean对象，则调用相应属性的相应setter方法
    //如果封装是集合类，则设置指定的key或下标对应的value值
    void set(PropertyTokenizer prop, Object value);

    //查找属性表达式指定的属性，第二个参数表示是否忽略属性表达式中的下划线
    String findProperty(String name, boolean useCamelCaseMapping);

    String[] getGetterNames();//查找可写属性的名称集合

    String[] getSetterNames();//查找可读属性的名称集合

    //解析属性表达式指定的setter方法的参数类型
    Class<?> getSetterType(String name);

    //解析属性表达式指定的getter方法的参数类型
    Class<?> getGetterType(String name);

    //判断属性表达式是否有getter/setter方法
    boolean hasSetter(String name);

    boolean hasGetter(String name);

    //为属性表达式指定的属性创建相应的MetaObject对象
    MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

    boolean isCollection();//封装对象是否为Collection类型

    void add(Object element);//调用Collection对象的add方法

    <E> void addAll(List<E> element);//调用Collection对象的addAll方法

}
