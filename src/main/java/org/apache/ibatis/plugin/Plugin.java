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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * @author Clinton Begin
 *
 * MyBatis提供的Plugin工具类实现，它实现了InvocationHandler接口，并提供了一个wrap（）静态方法用于创建代理对象
 */
public class Plugin implements InvocationHandler {

    private final Object target; //目标对象
    private final Interceptor interceptor;//Interceptor对象
    private final Map<Class<?>, Set<Method>> signatureMap;//记录了@Signature注解中的信息

    private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
        this.target = target;
        this.interceptor = interceptor;
        this.signatureMap = signatureMap;
    }

    public static Object wrap(Object target, Interceptor interceptor) {
        //获取用户自定义Interceptor中@Signature注解的信息，getSignatureMap()方法负责
        //处理@Signature注解，代码并不复杂，不再赘述
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
        Class<?> type = target.getClass();//获取目标类型

        //获取目标类型实现的接口，正如前文所述，拦截器可以拦截的4类对象都实现了相应的接口，这也是能
        //使用JDK动态代理的方式创建代理对象的基础
        Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
        if (interfaces.length > 0) {
            //使用JDK动态代理的方式创建代理对象
            return Proxy.newProxyInstance(
                    type.getClassLoader(),
                    interfaces,
                    //这里使用InvocationHandler对象就是Plugin对象
                    new Plugin(target, interceptor, signatureMap));
        }
        return target;
    }

    //Plugin.invoke（）方法中，会将当前调用的方法与signatureMap集合中记录的方法信息进行比较，
    // 如果当前调用的方法是需要被拦截的方法，则调用其intercept（）方法进行处理，如果不能被拦截则
    // 直接调用target的相应方法
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            //获取当前方法所在类或接口中，可被当前Interceptor拦截的方法
            Set<Method> methods = signatureMap.get(method.getDeclaringClass());
            //如果当前调用的方法需要被拦截，则调用interceptor.intercept()方法进行拦截处理
            if (methods != null && methods.contains(method)) {
                return interceptor.intercept(new Invocation(target, method, args));
            }
            //如果当前调用的方法不能被拦截，则调用target对象的相应方法
            return method.invoke(target, args);
        } catch (Exception e) {
            throw ExceptionUtil.unwrapThrowable(e);
        }
    }

    private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
        Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
        // issue #251
        if (interceptsAnnotation == null) {
            throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
        }
        Signature[] sigs = interceptsAnnotation.value();
        Map<Class<?>, Set<Method>> signatureMap = new HashMap<Class<?>, Set<Method>>();
        for (Signature sig : sigs) {
            Set<Method> methods = signatureMap.get(sig.type());
            if (methods == null) {
                methods = new HashSet<Method>();
                signatureMap.put(sig.type(), methods);
            }
            try {
                Method method = sig.type().getMethod(sig.method(), sig.args());
                methods.add(method);
            } catch (NoSuchMethodException e) {
                throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
            }
        }
        return signatureMap;
    }

    private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
        Set<Class<?>> interfaces = new HashSet<Class<?>>();
        while (type != null) {
            for (Class<?> c : type.getInterfaces()) {
                if (signatureMap.containsKey(c)) {
                    interfaces.add(c);
                }
            }
            type = type.getSuperclass();
        }
        return interfaces.toArray(new Class<?>[interfaces.size()]);
    }

}
