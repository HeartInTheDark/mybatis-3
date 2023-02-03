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
package org.apache.ibatis.executor.loader.cglib;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.apache.ibatis.executor.loader.AbstractEnhancedDeserializationProxy;
import org.apache.ibatis.executor.loader.AbstractSerialStateHolder;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.loader.WriteReplaceInterface;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class CglibProxyFactory implements ProxyFactory {

    private static final Log log = LogFactory.getLog(CglibProxyFactory.class);
    private static final String FINALIZE_METHOD = "finalize";
    private static final String WRITE_REPLACE_METHOD = "writeReplace";

    public CglibProxyFactory() {
        try {
            Resources.classForName("net.sf.cglib.proxy.Enhancer");
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot enable lazy loading because CGLIB is not available. Add CGLIB to your classpath.", e);
        }
    }

    @Override
    public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        return EnhancedResultObjectProxyImpl.createProxy(target, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
    }

    public Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        return EnhancedDeserializationProxyImpl.createProxy(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }

    @Override
    public void setProperties(Properties properties) {
        // Not Implemented
    }

    static Object crateProxy(Class<?> type, Callback callback, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        Enhancer enhancer = new Enhancer();
        enhancer.setCallback(callback);
        enhancer.setSuperclass(type);

        //查找名为"writeReplace"的方法，查找不到则添加WriteReplaceInterface接口，该接口
        //中定义了"writeReplace"方法
        try {
            type.getDeclaredMethod(WRITE_REPLACE_METHOD);
            // ObjectOutputStream will call writeReplace of objects returned by writeReplace
            if (log.isDebugEnabled()) {
                log.debug(WRITE_REPLACE_METHOD + " method was found on bean " + type + ", make sure it returns this");
            }
        } catch (NoSuchMethodException e) {
            enhancer.setInterfaces(new Class[]{WriteReplaceInterface.class});
        } catch (SecurityException e) {
            // nothing to do here
        }

        //根据构造方法的参数列表，调用相应的enhancer.create()方法，创建代理对象
        Object enhanced;
        if (constructorArgTypes.isEmpty()) {
            enhanced = enhancer.create();
        } else {
            Class<?>[] typesArray = constructorArgTypes.toArray(new Class[constructorArgTypes.size()]);
            Object[] valuesArray = constructorArgs.toArray(new Object[constructorArgs.size()]);
            enhanced = enhancer.create(typesArray, valuesArray);
        }
        return enhanced;
    }

    private static class EnhancedResultObjectProxyImpl implements MethodInterceptor {

        private final Class<?> type;//需要创建代理的目标类

        //ResultLoaderMap对象，其中记录了延时加载的属性名称与对应ResultLoader对象之间的关系
        private final ResultLoaderMap lazyLoader;

        //在mybatis-config.xml中，aggressiveLazyLoading配置项的值
        private final boolean aggressive;

        //触发延时加载的方法名列表，如果调用了该列表的方法，则对全部的延时加载属性进行加载操作
        private final Set<String> lazyLoadTriggerMethods;
        private final ObjectFactory objectFactory;

        //创建代理对象时，使用的构造方法的参数类型和参数值
        private final List<Class<?>> constructorArgTypes;
        private final List<Object> constructorArgs;

        private EnhancedResultObjectProxyImpl(Class<?> type, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            this.type = type;
            this.lazyLoader = lazyLoader;
            this.aggressive = configuration.isAggressiveLazyLoading();
            this.lazyLoadTriggerMethods = configuration.getLazyLoadTriggerMethods();
            this.objectFactory = objectFactory;
            this.constructorArgTypes = constructorArgTypes;
            this.constructorArgs = constructorArgs;
        }

        public static Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            final Class<?> type = target.getClass();
            //EnhancedResultObjectProxyImpl本身就是Callback接口的实现
            EnhancedResultObjectProxyImpl callback = new EnhancedResultObjectProxyImpl(type, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
            //创建代理对象
            Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
            //将target对象中的属性复制到代理对象中
            PropertyCopier.copyBeanProperties(type, target, enhanced);
            return enhanced;
        }

        @Override
        public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            final String methodName = method.getName();
            try {
                synchronized (lazyLoader) {
                    if (WRITE_REPLACE_METHOD.equals(methodName)) {
                        Object original;
                        if (constructorArgTypes.isEmpty()) {
                            original = objectFactory.create(type);
                        } else {
                            original = objectFactory.create(type, constructorArgTypes, constructorArgs);
                        }
                        PropertyCopier.copyBeanProperties(type, enhanced, original);
                        if (lazyLoader.size() > 0) {
                            return new CglibSerialStateHolder(original, lazyLoader.getProperties(), objectFactory, constructorArgTypes, constructorArgs);
                        } else {
                            return original;
                        }
                    } else {
                        //检测是否存在延时加载的属性，以及调用方法名是否为"finalize"
                        if (lazyLoader.size() > 0 && !FINALIZE_METHOD.equals(methodName)) {
                            //如果aggressiveLazyLoading配置项为true，或是调用方法名存在于lazyLoadTriggerMethods
                            //列表中，则将全部的属性都加载完成
                            if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                                lazyLoader.loadAll();
                            } else if (PropertyNamer.isSetter(methodName)) {
                                //如果调用了某属性的setter方法，先获取该属性的名称
                                final String property = PropertyNamer.methodToProperty(methodName);
                                //从延迟列表中移除
                                lazyLoader.remove(property);
                            } else if (PropertyNamer.isGetter(methodName)) {
                                //如果调用了某属性的setter方法，先获取该属性的名称
                                final String property = PropertyNamer.methodToProperty(methodName);
                                if (lazyLoader.hasLoader(property)) {//检测是否为延时加载属性
                                    lazyLoader.load(property);//触发该属性的加载操作
                                }
                            }
                        }
                    }
                }
                return methodProxy.invokeSuper(enhanced, args);//调用目标对象的方法
            } catch (Throwable t) {
                throw ExceptionUtil.unwrapThrowable(t);
            }
        }
    }

    private static class EnhancedDeserializationProxyImpl extends AbstractEnhancedDeserializationProxy implements MethodInterceptor {

        private EnhancedDeserializationProxyImpl(Class<?> type, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                                 List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            super(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
        }

        public static Object createProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                         List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            final Class<?> type = target.getClass();
            //EnhancedDeserializationProxyImpl本身就是Callback接口的实现
            EnhancedDeserializationProxyImpl callback = new EnhancedDeserializationProxyImpl(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
            //调用crateProxy()方法创建代理对象
            Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
            //将target对象中的属性值复制到代理对象的对象属性中
            PropertyCopier.copyBeanProperties(type, target, enhanced);
            return enhanced;
        }

        @Override
        public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            final Object o = super.invoke(enhanced, method, args);
            return o instanceof AbstractSerialStateHolder ? o : methodProxy.invokeSuper(o, args);
        }

        @Override
        protected AbstractSerialStateHolder newSerialStateHolder(Object userBean, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                                                 List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            return new CglibSerialStateHolder(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
        }
    }
}
