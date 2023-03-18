/**
 *    Copyright 2010-2017 the original author or authors.
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
package org.mybatis.spring.mapper;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

/**
 * A {@link ClassPathBeanDefinitionScanner} that registers Mappers by
 * {@code basePackage}, {@code annotationClass}, or {@code markerInterface}. If
 * an {@code annotationClass} and/or {@code markerInterface} is specified, only
 * the specified types will be searched (searching for all interfaces will be
 * disabled).
 * <p>
 * This functionality was previously a private class of
 * {@link MapperScannerConfigurer}, but was broken out in version 1.2.0.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 * 
 * @see MapperFactoryBean
 * @since 1.2.0
 *
 * @tips 负责执行扫描，将扫描到的 Mapper 接口，注册成 beanClass 为
 * MapperFactoryBean 的 BeanDefinition 对象，从而实现创建 Mapper 对象。
 */
public class ClassPathMapperScanner extends ClassPathBeanDefinitionScanner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClassPathMapperScanner.class);

  /**
   * 是否添加到 {@link org.apache.ibatis.session.Configuration} 中
   */
  private boolean addToConfig = true;

  private SqlSessionFactory sqlSessionFactory;

  private SqlSessionTemplate sqlSessionTemplate;

  /**
   * {@link #sqlSessionTemplate} 的 bean 名字
   */
  private String sqlSessionTemplateBeanName;

  /**
   * {@link #sqlSessionFactory} 的 bean 名字
   */
  private String sqlSessionFactoryBeanName;

  /**
   * 指定注解
   */
  private Class<? extends Annotation> annotationClass;

  /**
   * 指定接口
   */
  private Class<?> markerInterface;

  /**
   * MapperFactoryBean 对象
   */
  private MapperFactoryBean<?> mapperFactoryBean = new MapperFactoryBean<>();

  public ClassPathMapperScanner(BeanDefinitionRegistry registry) {
    super(registry, false);
  }

  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass;
  }

  public void setMarkerInterface(Class<?> markerInterface) {
    this.markerInterface = markerInterface;
  }

  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  public void setSqlSessionTemplateBeanName(String sqlSessionTemplateBeanName) {
    this.sqlSessionTemplateBeanName = sqlSessionTemplateBeanName;
  }

  public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
    this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
  }

  public void setMapperFactoryBean(MapperFactoryBean<?> mapperFactoryBean) {
    this.mapperFactoryBean = mapperFactoryBean != null ? mapperFactoryBean : new MapperFactoryBean<>();
  }


  /**
   * Configures parent scanner to search for the right interfaces. It can search
   * for all interfaces or just for those that extends a markerInterface or/and
   * those annotated with the annotationClass
   *
   * @tips 注册过滤器
   */
  public void registerFilters() {
    // 是否接受所有接口
    boolean acceptAllInterfaces = true;

    // if specified, use the given annotation and / or marker interface
    // 对于 annotation 属性的处理
    // 如果指定了注解，则添加 INCLUDE 过滤器 AnnotationTypeFilter 对象
    if (this.annotationClass != null) {
      /**
       * 根据此属性生成过滤器以保证达到用户想要的效果，而封装此属性的过滤器就是 AnnotationTypeFilter。
       * AnnotationTypeFilter 保证在扫描对应 Java 文件时只接受标记有注解为 annotationClass 的接口。
       */
      addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
      acceptAllInterfaces = false; // 标记不是接受所有接口
    }

    // override AssignableTypeFilter to ignore matches on the actual marker interface
    // 对 markerInterface 属性的处理
    // 如果指定了接口，则添加 INCLUDE 过滤器 AssignableTypeFilter 对象
    if (this.markerInterface != null) {
      /**
       * 根据此属性生成过滤器以保证用户想要的效果，而封装此属性的过滤器就是实现 AssignableTypeFilter接口的局部类。
       * 表示扫描过程中石油实现 markerInterface 接口的类才会被接受
       */
      addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
        @Override
        protected boolean matchClassName(String className) {
          return false;
        }
      });
      acceptAllInterfaces = false; // 标记不是接受所有接口
    }

    // 如果接受所有接口，则添加自定义 INCLUDE 过滤器 TypeFilter ，全部返回 true
    if (acceptAllInterfaces) {
      // default include filter that accepts all classes
      /**
       * 在上面两个属性中如果存在其中任何属性，acceptAllInterfaces 的值将会改变，但是如果
       * 用户没有设定以上两个属性，那么，Spring会为我们增加一个默认的过滤器实现 TypeFilter接口的
       * 局部类，旨在接受所有接口文件
       */
      addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
    }

    // exclude package-info.java
    // 不扫描 package-info.java
    // 添加 INCLUDE 过滤器，排除 package-info.java
    addExcludeFilter((metadataReader, metadataReaderFactory) -> {
      /**
       * 对于命名为 package-info 的Java文件，默认不作为逻辑实现接口，将其排除掉，使用
       * TypeFilter 接口的局部类实现 match 方法。
       */
      String className = metadataReader.getClassMetadata().getClassName();
      return className.endsWith("package-info");
    });
  }

  /**
   * Calls the parent search that will search and register all the candidates.
   * Then the registered objects are post processed to set them as
   * MapperFactoryBeans
   *
   * @tips 执行扫描，将扫描到的 Mapper 接口，注册成 beanClass 为 MapperFactoryBean 的 BeanDefinition 对象。
   */
  @Override
  public Set<BeanDefinitionHolder> doScan(String... basePackages) {
    // <1> 执行扫描，获得包下符合的类们，并分装成 BeanDefinitionHolder 对象的集合
    Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

    // 如果没有扫描到任何文件发出警告
    if (beanDefinitions.isEmpty()) {
      LOGGER.warn(() -> "No MyBatis mapper was found in '" + Arrays.toString(basePackages) + "' package. Please check your configuration.");
    } else {
      // 处理 BeanDefinitionHolder 对象的集合
      processBeanDefinitions(beanDefinitions);
    }

    return beanDefinitions;
  }

  private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
    GenericBeanDefinition definition;
    // <1> 遍历 BeanDefinitionHolder 数组，逐一设置属性
    for (BeanDefinitionHolder holder : beanDefinitions) {
      definition = (GenericBeanDefinition) holder.getBeanDefinition();
      String beanClassName = definition.getBeanClassName();
      LOGGER.debug(() -> "Creating MapperFactoryBean with name '" + holder.getBeanName()
          + "' and '" + beanClassName + "' mapperInterface");

      // the mapper interface is the original class of the bean
      // but, the actual class of the bean is MapperFactoryBean
      // <2> 此处 definition 的 beanClass 为 Mapper 接口，需要修改成 MapperFactoryBean 类，从而创建 Mapper 代理对象
      definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName); // issue #59
      definition.setBeanClass(this.mapperFactoryBean.getClass());

      // 开始构造 MapperFactoryBean 类型的 bean
      // <3> 设置 `MapperFactoryBean.addToConfig` 属性
      definition.getPropertyValues().add("addToConfig", this.addToConfig);

      // <4.1>是否已经显式设置了 sqlSessionFactory 或 sqlSessionFactory 属性
      boolean explicitFactoryUsed = false;
      // <4.2> 如果 sqlSessionFactoryBeanName 或 sqlSessionFactory 非空，设置到 `MapperFactoryBean.sqlSessionFactory` 属性
      if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
        definition.getPropertyValues().add("sqlSessionFactory", new RuntimeBeanReference(this.sqlSessionFactoryBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionFactory != null) {
        definition.getPropertyValues().add("sqlSessionFactory", this.sqlSessionFactory);
        explicitFactoryUsed = true;
      }

      // <4.3> 如果 sqlSessionTemplateBeanName 或 sqlSessionTemplate 非空，设置到 `MapperFactoryBean.sqlSessionTemplate` 属性
      if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
        if (explicitFactoryUsed) {
          LOGGER.warn(() -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate", new RuntimeBeanReference(this.sqlSessionTemplateBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionTemplate != null) {
        if (explicitFactoryUsed) {
          LOGGER.warn(() -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate", this.sqlSessionTemplate);
        explicitFactoryUsed = true;
      }

      // <4.4> 如果未显式设置，则设置根据类型自动注入
      if (!explicitFactoryUsed) {
        LOGGER.debug(() -> "Enabling autowire by type for MapperFactoryBean with name '" + holder.getBeanName() + "'.");
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
    return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
    if (super.checkCandidate(beanName, beanDefinition)) {
      return true;
    } else {
      LOGGER.warn(() -> "Skipping MapperFactoryBean with name '" + beanName
          + "' and '" + beanDefinition.getBeanClassName() + "' mapperInterface"
          + ". Bean already defined with the same name!");
      return false;
    }
  }

}
