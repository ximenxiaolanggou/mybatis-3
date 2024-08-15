/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(Configuration.class, reader, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, Reader reader, String environment,
      Properties props) {
    this(configClass, new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(Configuration.class, inputStream, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, InputStream inputStream, String environment,
      Properties props) {
    // XMLMapperEntityResolver这里的Entity指定就是配置文件
    // XMLMapperEntityResolver对象创建使用的是无参构造（之创建对象，没有做其余初始化东西）
    // XMLMapperEntityResolver中定义了dtd文件位置（xml规则文件），并且本地提供了相关文件，避免网上下载不下来

    // XPathParser构造方法中主要两件事
    // 1. 将构造函数中的参数比如props、XMLMapperEntityResolver添加到XPathParser成员变量中
    // 2. 将配置文件输入流转成document对象（html和xml文件基本都是一样都是属于document对象）
    this(configClass, new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(Class<? extends Configuration> configClass, XPathParser parser, String environment,
      Properties props) {
    // 1. 创建Configuration对象
    // 2. 调用父类构造器
    super(newConfig(configClass));
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // 设置变量
    this.configuration.setVariables(props);
    // 解析标志位
    this.parsed = false;
    // 设置环境
    this.environment = environment;
    // 设置解析器
    this.parser = parser;
  }

  public Configuration parse() {
    // 配置文件只能解析一次，第二次抛出异常
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 解析标志为true
    parsed = true;

    // parser.evalNode("/configuration")：获取到configuration节点
    // parseConfiguration：解析configuration节点
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      // 解析properties标签
      propertiesElement(root.evalNode("properties"));
      // 解析settings标签
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 设置vfsImpl字段
      loadCustomVfsImpl(settings);
      loadCustomLogImpl(settings);
      // 类型别名解析（类型别名有两种写法）：
      // 1. <typeAlias alias="Post" type="domain.blog.Post"/> 具体到某个别名
      // 2. <package name="domain.blog"/> 指定到某个包下
      typeAliasesElement(root.evalNode("typeAliases"));
      // 插件标签解析
      pluginsElement(root.evalNode("plugins"));
      // 对象工厂解析
      objectFactoryElement(root.evalNode("objectFactory"));
      // 对象包装工厂解析
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 反射工厂解析
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 上面完成了settings标签解析，这里是将解析的属性设置到Configuration对象中
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 环境标签解析
      environmentsElement(root.evalNode("environments"));
      // databaseIdProvider标签解析
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 类型处理器标签解析：提供了两种方式定义自定义类型处理器
      // 1、<package name="org.mybatis.example"/>：指定包名
      // 2、<typeHandler handler="org.mybatis.example.ExampleTypeHandler"/> 指定具体的处理器
      typeHandlersElement(root.evalNode("typeHandlers"));
      // mappers 标签解析
      /**
       * 1、使用相对于类路径的资源引用
       * <mappers>
       *   <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
       *   <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
       *   <mapper resource="org/mybatis/builder/PostMapper.xml"/>
       * </mappers>
       *
       * 2、使用完全限定资源定位符（URL）
       * <mappers>
       *   <mapper url="file:///var/mappers/AuthorMapper.xml"/>
       *   <mapper url="file:///var/mappers/BlogMapper.xml"/>
       *   <mapper url="file:///var/mappers/PostMapper.xml"/>
       * </mappers>
       *
       * 3、使用映射器接口实现类的完全限定类名
       * <mappers>
       *   <mapper class="org.mybatis.builder.AuthorMapper"/>
       *   <mapper class="org.mybatis.builder.BlogMapper"/>
       *   <mapper class="org.mybatis.builder.PostMapper"/>
       * </mappers>
       *
       * 4、将包内的映射器接口实现全部注册为映射器
       * <mappers>
       *   <package name="org.mybatis.builder"/>
       * </mappers>
       */
      mappersElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // 将settings所有子标签setting中的name和value属性转成Properties对象
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 循环settings中配置属性，判断是否满足mybatis规范（setting中的name编写是有规范的，不能够任意写）
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException(
            "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfsImpl(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value == null) {
      return;
    }
    String[] clazzes = value.split(",");
    for (String clazz : clazzes) {
      if (!clazz.isEmpty()) {
        @SuppressWarnings("unchecked")
        Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
        configuration.setVfsImpl(vfsImpl);
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  private void typeAliasesElement(XNode context) {
    if (context == null) {
      return;
    }
    for (XNode child : context.getChildren()) {
      // 子标签名为package：1、获取name属性，2、调用别名注册器完成注册（解析包下所有类，并添加到注册器中）
      if ("package".equals(child.getName())) {
        String typeAliasPackage = child.getStringAttribute("name");
        configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
      } else {
        // 这里就是： <typeAlias alias="Blog" type="domain.blog.Blog"/>，解析
        String alias = child.getStringAttribute("alias");
        String type = child.getStringAttribute("type");
        try {
          Class<?> clazz = Resources.classForName(type);
          if (alias == null) {
            typeAliasRegistry.registerAlias(clazz);
          } else {
            typeAliasRegistry.registerAlias(alias, clazz);
          }
        } catch (ClassNotFoundException e) {
          throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
        }
      }
    }
  }

  private void pluginsElement(XNode context) throws Exception {
    if (context != null) {
      for (XNode child : context.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor()
            .newInstance();
        interceptorInstance.setProperties(properties);
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    // 将propetties中的属性转成Properties对象
    Properties defaults = context.getChildrenAsProperties();
    // 获取resource属性
    String resource = context.getStringAttribute("resource");
    // 获取url属性
    String url = context.getStringAttribute("url");
    // resource和url不能同时配置
    if (resource != null && url != null) {
      throw new BuilderException(
          "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
    }
    if (resource != null) {
      // 将配置文件中属性读取出来放置到defaults中
      defaults.putAll(Resources.getResourceAsProperties(resource));
    } else if (url != null) {
      // 将url指定的资源中属性读取出来放置到defaults中
      defaults.putAll(Resources.getUrlAsProperties(url));
    }
    // 获取Configuration对象中的变量属性
    Properties vars = configuration.getVariables();
    // 不为空就添加
    if (vars != null) {
      defaults.putAll(vars);
    }
    // 解析器中设置解析属性
    parser.setVariables(defaults);
    // Configuration对象中设置解析属性
    configuration.setVariables(defaults);
  }

  private void settingsElement(Properties props) {
    configuration
        .setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(
        AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(
        stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
    configuration.setArgNameBasedConstructorAutoMapping(
        booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
    configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
    configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    // environment为空使用默认环境
    if (environment == null) {
      environment = context.getStringAttribute("default");
    }
    for (XNode child : context.getChildren()) {
      String id = child.getStringAttribute("id");
      // 判断default配置的环境和每个环境id是否相同
      if (isSpecifiedEnvironment(id)) {
        // 解析transactionManager属性，并创建事务工厂
        TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
        // 解析dataSource属性，并创建DataSourceFactory
        DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
        DataSource dataSource = dsFactory.getDataSource();
        // 创建环境对象
        Environment.Builder environmentBuilder = new Environment.Builder(id).transactionFactory(txFactory)
            .dataSource(dataSource);
        // 将环境对象设置到Configuration中
        configuration.setEnvironment(environmentBuilder.build());
        break;
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    String type = context.getStringAttribute("type");
    // awful patch to keep backward compatibility
    if ("VENDOR".equals(type)) {
      type = "DB_VENDOR";
    }
    Properties properties = context.getChildrenAsProperties();
    DatabaseIdProvider databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor()
        .newInstance();
    databaseIdProvider.setProperties(properties);
    Environment environment = configuration.getEnvironment();
    if (environment != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  // 类型处理器标签解析
  private void typeHandlersElement(XNode context) {
    if (context == null) {
      return;
    }
    for (XNode child : context.getChildren()) {
      // 包名指定方式
      if ("package".equals(child.getName())) {
        // 获取name属性（包名）
        String typeHandlerPackage = child.getStringAttribute("name");
        // 类型处理注册器，注册方法
        typeHandlerRegistry.register(typeHandlerPackage);
      } else {
        // 具体指定自定义类型处理器
        // 获取javaType类型
        String javaTypeName = child.getStringAttribute("javaType");
        // 获取jdbcType类型
        String jdbcTypeName = child.getStringAttribute("jdbcType");
        // 获取处理器
        String handlerTypeName = child.getStringAttribute("handler");
        // 获取处理器class
        Class<?> javaTypeClass = resolveClass(javaTypeName);
        JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
        Class<?> typeHandlerClass = resolveClass(handlerTypeName);
        // 因为标签中javaType、jdbcType是可写可不写的，所有分为以下三种情况
        if (javaTypeClass != null) {
          if (jdbcType == null) {
            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
          } else {
            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
          }
        } else {
          typeHandlerRegistry.register(typeHandlerClass);
        }
      }
    }
  }

  private void mappersElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    for (XNode child : context.getChildren()) {
      // 将包内的映射器接口实现全部注册为映射器：<package name="org.mybatis.builder"/>
      if ("package".equals(child.getName())) {
        String mapperPackage = child.getStringAttribute("name");
        configuration.addMappers(mapperPackage);
      } else {
        String resource = child.getStringAttribute("resource");
        String url = child.getStringAttribute("url");
        String mapperClass = child.getStringAttribute("class");
        // 使用相对于类路径的资源引用: <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
        if (resource != null && url == null && mapperClass == null) {
          ErrorContext.instance().resource(resource);
          // 读取xml文件
          try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            // 创建XMLMapper解析器对象，并将输入流、configuration等信息装配其中
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                configuration.getSqlFragments());
            // mapper解析工作
            mapperParser.parse();
          }
        } else if (resource == null && url != null && mapperClass == null) {
          // 使用完全限定资源定位符（URL）：<mapper url="file:///var/mappers/AuthorMapper.xml"/>
          ErrorContext.instance().resource(url);
          try (InputStream inputStream = Resources.getUrlAsStream(url)) {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                configuration.getSqlFragments());
            mapperParser.parse();
          }
        } else if (resource == null && url == null && mapperClass != null) {
          // 使用映射器接口实现类的完全限定类名：<mapper class="org.mybatis.builder.AuthorMapper"/>
          Class<?> mapperInterface = Resources.classForName(mapperClass);
          configuration.addMapper(mapperInterface);
        } else {
          throw new BuilderException(
              "A mapper element may only specify a url, resource or class, but not more than one.");
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    }
    if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    }
    return environment.equals(id);
  }

  private static Configuration newConfig(Class<? extends Configuration> configClass) {
    try {
      return configClass.getDeclaredConstructor().newInstance();
    } catch (Exception ex) {
      throw new BuilderException("Failed to create a new Configuration instance.", ex);
    }
  }

}
