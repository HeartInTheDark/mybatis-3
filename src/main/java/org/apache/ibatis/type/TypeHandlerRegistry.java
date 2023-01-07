/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.type;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.JapaneseDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.reflection.Jdk;

/**
 * 在MyBatis初始化过程中会为已知的TypeHandler创建对象，并实现注册到TypeHandlerRegister中，由TypeHandlerRegistry管理
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public final class TypeHandlerRegistry {

  //记录JdbcType与TypeHandler之间的对应关系，其中JdbcType是一个枚举类型，它又定义对应Jdbc类型
  //该集合主要用于从结果集读取数据时，将数据从Jdbc类型转换成Java类型
  //拓展：EnumMap为创建一个key为枚举类型的map，构造方法指定key类型
  private final Map<JdbcType, TypeHandler<?>> JDBC_TYPE_HANDLER_MAP = new EnumMap<JdbcType, TypeHandler<?>>(JdbcType.class);

  //记录了Java类型向指定Jdbc类型转换时，需要使用的TypeHandler对象。例如：Java类型中的String可能
  //转换成数据库的char、varchar等多种类型，所以存在一对多的关系
  private final Map<Type, Map<JdbcType, TypeHandler<?>>> TYPE_HANDLER_MAP = new ConcurrentHashMap<Type, Map<JdbcType, TypeHandler<?>>>();

  private final TypeHandler<Object> UNKNOWN_TYPE_HANDLER = new UnknownTypeHandler(this);

  //记录了全部TypeHandler的类型以及该类型相应的TypeHandler对象
  private final Map<Class<?>, TypeHandler<?>> ALL_TYPE_HANDLERS_MAP = new HashMap<Class<?>, TypeHandler<?>>();

  //空TypeHandler集合的标识
  private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();

  private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

  public TypeHandlerRegistry() {

    //重点看下面的String 其他的类似

    register(Boolean.class, new BooleanTypeHandler());
    register(boolean.class, new BooleanTypeHandler());
    register(JdbcType.BOOLEAN, new BooleanTypeHandler());
    register(JdbcType.BIT, new BooleanTypeHandler());

    register(Byte.class, new ByteTypeHandler());
    register(byte.class, new ByteTypeHandler());
    register(JdbcType.TINYINT, new ByteTypeHandler());

    register(Short.class, new ShortTypeHandler());
    register(short.class, new ShortTypeHandler());
    register(JdbcType.SMALLINT, new ShortTypeHandler());

    register(Integer.class, new IntegerTypeHandler());
    register(int.class, new IntegerTypeHandler());
    register(JdbcType.INTEGER, new IntegerTypeHandler());

    register(Long.class, new LongTypeHandler());
    register(long.class, new LongTypeHandler());

    register(Float.class, new FloatTypeHandler());
    register(float.class, new FloatTypeHandler());
    register(JdbcType.FLOAT, new FloatTypeHandler());

    register(Double.class, new DoubleTypeHandler());
    register(double.class, new DoubleTypeHandler());
    register(JdbcType.DOUBLE, new DoubleTypeHandler());

    register(Reader.class, new ClobReaderTypeHandler());

    //StringTypeHandler 能够将数据从String类型转换为null（JdbcType），所以向TYPE_HANDLER_MAP集合注册该对象
    //并向ALL_TYPE_HANDLERS_MAP注册StringTypeHandler
    register(String.class, new StringTypeHandler());
    //StringTypeHandler 能够将数据从String类型转换为 CHAR（JdbcType），所以向TYPE_HANDLER_MAP集合注册该对象
    //并向ALL_TYPE_HANDLERS_MAP注册StringTypeHandler
    register(String.class, JdbcType.CHAR, new StringTypeHandler());
    //....类似
    register(String.class, JdbcType.CLOB, new ClobTypeHandler());
    register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.LONGVARCHAR, new ClobTypeHandler());
    register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
    register(JdbcType.CHAR, new StringTypeHandler());
    register(JdbcType.VARCHAR, new StringTypeHandler());
    register(JdbcType.CLOB, new ClobTypeHandler());
    register(JdbcType.LONGVARCHAR, new ClobTypeHandler());
    register(JdbcType.NVARCHAR, new NStringTypeHandler());
    register(JdbcType.NCHAR, new NStringTypeHandler());
    register(JdbcType.NCLOB, new NClobTypeHandler());

    register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
    register(JdbcType.ARRAY, new ArrayTypeHandler());

    register(BigInteger.class, new BigIntegerTypeHandler());
    register(JdbcType.BIGINT, new LongTypeHandler());

    register(BigDecimal.class, new BigDecimalTypeHandler());
    register(JdbcType.REAL, new BigDecimalTypeHandler());
    register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
    register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

    register(InputStream.class, new BlobInputStreamTypeHandler());
    register(Byte[].class, new ByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
    register(byte[].class, new ByteArrayTypeHandler());
    register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
    register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.BLOB, new BlobTypeHandler());

    register(Object.class, UNKNOWN_TYPE_HANDLER);
    register(Object.class, JdbcType.OTHER, UNKNOWN_TYPE_HANDLER);
    register(JdbcType.OTHER, UNKNOWN_TYPE_HANDLER);

    register(Date.class, new DateTypeHandler());
    register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
    register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
    register(JdbcType.TIMESTAMP, new DateTypeHandler());
    register(JdbcType.DATE, new DateOnlyTypeHandler());
    register(JdbcType.TIME, new TimeOnlyTypeHandler());

    register(java.sql.Date.class, new SqlDateTypeHandler());
    register(java.sql.Time.class, new SqlTimeTypeHandler());
    register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

    // mybatis-typehandlers-jsr310
    if (Jdk.dateAndTimeApiExists) {
      this.register(Instant.class, InstantTypeHandler.class);
      this.register(LocalDateTime.class, LocalDateTimeTypeHandler.class);
      this.register(LocalDate.class, LocalDateTypeHandler.class);
      this.register(LocalTime.class, LocalTimeTypeHandler.class);
      this.register(OffsetDateTime.class, OffsetDateTimeTypeHandler.class);
      this.register(OffsetTime.class, OffsetTimeTypeHandler.class);
      this.register(ZonedDateTime.class, ZonedDateTimeTypeHandler.class);
      this.register(Month.class, MonthTypeHandler.class);
      this.register(Year.class, YearTypeHandler.class);
      this.register(YearMonth.class, YearMonthTypeHandler.class);
      this.register(JapaneseDate.class, JapaneseDateTypeHandler.class);
    }

    // issue #273
    register(Character.class, new CharacterTypeHandler());
    register(char.class, new CharacterTypeHandler());
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    this.defaultEnumTypeHandler = typeHandler;
  }

  public boolean hasTypeHandler(Class<?> javaType) {
    return hasTypeHandler(javaType, null);
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
    return hasTypeHandler(javaTypeReference, null);
  }

  public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
    return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
    return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
  }

  public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
    return ALL_TYPE_HANDLERS_MAP.get(handlerType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    return getTypeHandler((Type) type, null);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
    return getTypeHandler(javaTypeReference, null);
  }

  public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
    return JDBC_TYPE_HANDLER_MAP.get(jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
    return getTypeHandler((Type) type, jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
    return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
  }

  @SuppressWarnings("unchecked")
  private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
    if (ParamMap.class.equals(type)) {
      return null;
    }
    //查找（或初始化）Java类型对应的TypeHandler集合
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
    TypeHandler<?> handler = null;
    if (jdbcHandlerMap != null) {
      // 根据JDBC类型查找TypeHandler对象
      handler = jdbcHandlerMap.get(jdbcType);
      if (handler == null) {
        handler = jdbcHandlerMap.get(null);
      }
      if (handler == null) {
        // #591
        //如果jdbcHandlerMap只注册了一个TypeHandler，则使用此Typehandler对象
        handler = pickSoleHandler(jdbcHandlerMap);
      }
    }
    // type drives generics here
    return (TypeHandler<T>) handler;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
    //查找Java类型对应的TypeHandler集合
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = TYPE_HANDLER_MAP.get(type);
    if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) { //检测是否为空集合标识
      return null;
    }

    //初始化指定Java类型的TypeHandler集合
    if (jdbcHandlerMap == null && type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      if (clazz.isEnum()) {//枚举类型的处理
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(clazz, clazz);
        if (jdbcHandlerMap == null) {
          register(clazz, getInstance(clazz, defaultEnumTypeHandler));
          return TYPE_HANDLER_MAP.get(clazz);
        }
      } else {
        //查找父类对应的TypeHandler集合，并作为初始化集合
        jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
      }
    }
    TYPE_HANDLER_MAP.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
    return jdbcHandlerMap;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
    for (Class<?> iface : clazz.getInterfaces()) {
      Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = TYPE_HANDLER_MAP.get(iface);
      if (jdbcHandlerMap == null) {
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
      }
      if (jdbcHandlerMap != null) {
        // Found a type handler regsiterd to a super interface
        HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<JdbcType, TypeHandler<?>>();
        for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
          // Create a type handler instance with enum type as a constructor arg
          newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
        }
        return newMap;
      }
    }
    return null;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
    Class<?> superclass =  clazz.getSuperclass();
    if (superclass == null || Object.class.equals(superclass)) {
      return null; //父类为Object或null  查找结束
    }
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = TYPE_HANDLER_MAP.get(superclass);
    if (jdbcHandlerMap != null) {
      return jdbcHandlerMap;
    } else {
      //递归查找父类对应的TypeHandler集合
      return getJdbcHandlerMapForSuperclass(superclass);
    }
  }

  private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
    TypeHandler<?> soleHandler = null;
    for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
      if (soleHandler == null) {
        soleHandler = handler;
      } else if (!handler.getClass().equals(soleHandler.getClass())) {
        // More than one type handlers registered.
        return null;
      }
    }
    return soleHandler;
  }

  public TypeHandler<Object> getUnknownTypeHandler() {
    return UNKNOWN_TYPE_HANDLER;
  }

  //重载5
  public void register(JdbcType jdbcType, TypeHandler<?> handler) {
    //注册JDBC类型对应的TypeHandler
    JDBC_TYPE_HANDLER_MAP.put(jdbcType, handler);
  }

  //
  // REGISTER INSTANCE
  //

  // Only handler

  //重载2
  @SuppressWarnings("unchecked")
  public <T> void register(TypeHandler<T> typeHandler) {
    boolean mappedTypeFound = false;
    //获取@MappedTypes注解，并根据@MappedTypes 注解指定的Java类型进行注册
    MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      for (Class<?> handledType : mappedTypes.value()) {
        register(handledType, typeHandler);//交由重载3解析处理
        mappedTypeFound = true;
      }
    }
    // @since 3.1.0 - try to auto-discover the mapped type
    //从3.1.0版本开始，可以根据TypeHandler类型自动查找对应的Java类型，这需要我们的TypeHandler实现类同时
    //继承TypeReference这个抽象类
    if (!mappedTypeFound && typeHandler instanceof TypeReference) {
      try {
        TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
        register(typeReference.getRawType(), typeHandler); //交由重载3处理
        mappedTypeFound = true;
      } catch (Throwable t) {
        // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
      }
    }
    if (!mappedTypeFound) {
      register((Class<T>) null, typeHandler);// 类型转换后，交由重载3处理
    }
  }

  // java type + handler
  //重载3
  public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
    register((Type) javaType, typeHandler);
  }
  private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
    //获取@MappedJdbcTypes注解
    MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
    if (mappedJdbcTypes != null) {
      //根据@MappedJdbcTypes注解指定的JDBC类型进行注册
      for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
        register(javaType, handledJdbcType, typeHandler);//交由重载4完成注册
      }
      if (mappedJdbcTypes.includeNullJdbcType()) {
        register(javaType, null, typeHandler);//交由重载4完成注册
      }
    } else {
      register(javaType, null, typeHandler);//交由重载4完成注册
    }
  }

  public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
    register(javaTypeReference.getRawType(), handler);
  }

  // java type + jdbc type + handler

  public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
    register((Type) type, jdbcType, handler);
  }

  //重载4
  private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
    if (javaType != null) { //检测是否明确指定了TypeHandler能够处理的Java类型
      //获取指定Java类型在TYPE_HANDLER_MAP集合中对应的TypeHandler集合
      Map<JdbcType, TypeHandler<?>> map = TYPE_HANDLER_MAP.get(javaType);
      if (map == null || map == NULL_TYPE_HANDLER_MAP) {//创建新的TypeHandler集合，并添加到TYPE_HANDLER_MAP中
        map = new HashMap<JdbcType, TypeHandler<?>>();
        TYPE_HANDLER_MAP.put(javaType, map);
      }
      map.put(jdbcType, handler); //将TypeHandler对象注册到TYPE_HANDLER_MAP集合
    }
    //向ALL_TYPE_HANDLERS_MAP集合注册TypeHandler类型和对应的TypeHandler对象
    ALL_TYPE_HANDLERS_MAP.put(handler.getClass(), handler);
  }

  //
  // REGISTER CLASS
  //

  // Only handler type

  //重载1
  public void register(Class<?> typeHandlerClass) {
    boolean mappedTypeFound = false;
    //获取@MappedTypes注解
    MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      //根据@MappedTypes注解中指定的Java类型进行注册
      for (Class<?> javaTypeClass : mappedTypes.value()) {
        //经过强制类型转换以及使用反射创建 TypeHandler 对象之后，交由重载3继续处理
        register(javaTypeClass, typeHandlerClass);
        mappedTypeFound = true;
      }
    }
    if (!mappedTypeFound) {
      //为指定@MappedTypes 注解交由重载2继续处理
      register(getInstance(null, typeHandlerClass));
    }
  }

  // java type + handler type

  public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
    register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
  }

  public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
  }

  // java type + jdbc type + handler type

  public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
    register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
  }

  // Construct a handler (used also from Builders)

  @SuppressWarnings("unchecked")
  public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    if (javaTypeClass != null) {
      try {
        Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
        return (TypeHandler<T>) c.newInstance(javaTypeClass);
      } catch (NoSuchMethodException ignored) {
        // ignored
      } catch (Exception e) {
        throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
      }
    }
    try {
      Constructor<?> c = typeHandlerClass.getConstructor();
      return (TypeHandler<T>) c.newInstance();
    } catch (Exception e) {
      throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
    }
  }

  // scan
  //重载6
  //主要用来自动扫描指定包下的TypeHandler实现并完成注册
  public void register(String packageName) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
    //查找指定包下的TypeHandler接口实现类
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
    for (Class<?> type : handlerSet) {
      //Ignore inner classes and interfaces (including package-info.java) and abstract classes
      //过滤掉内部类，接口以及抽象类
      if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
        register(type);//交由重载1继续注册
      }
    }
  }
  
  // get information
  
  /**
   * @since 3.2.2
   */
  public Collection<TypeHandler<?>> getTypeHandlers() {
    return Collections.unmodifiableCollection(ALL_TYPE_HANDLERS_MAP.values());
  }
  
}
