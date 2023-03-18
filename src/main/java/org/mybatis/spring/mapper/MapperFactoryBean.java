/**
 *    Copyright 2010-2018 the original author or authors.
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

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;

/**
 * BeanFactory that enables injection of MyBatis mapper interfaces. It can be set up with a
 * SqlSessionFactory or a pre-configured SqlSessionTemplate.
 * <p>
 * Sample configuration:
 *
 * <pre class="code">
 * {@code
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * <p>
 * Note that this factory can only inject <em>interfaces</em>, not concrete classes.
 *
 * @author Eduardo Macarron
 *
 * @see SqlSessionTemplate
 */
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

  /**
   * Mapper 接口
   */
  private Class<T> mapperInterface;

  /**
   * 是否添加到 {@link Configuration} 中
   */
  private boolean addToConfig = true;

  public MapperFactoryBean() {
    //intentionally empty 
  }
  
  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * {@inheritDoc}
   * 在父类中 DaoSupport 中初始化方法 afterProperties ，
   * 初始化包括对 DAO 配置的验证以及对 DAO 的初始化工作，其中 initDao方法是模板方法，
   * 设计给子类进一步逻辑处理。
   * 而 checkDaoConfig() 才是我们分析的重点。
   */
  @Override
  protected void checkDaoConfig() {
    // 父类中对于sqlSession 不为空的验证。
    // <1> 校验 sqlSessionTemplate 非空
    super.checkDaoConfig();

    // <2> 校验 mapperInterface 非空
    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    // sqlSession 作为根据接口创建映射器代理的接触类一定不可以为空，而 sqlSession 的初始化工作是在设定其 sqlSessionFactory 属性完成的

    // <3> 添加 Mapper 接口到 configuration 中
    Configuration configuration = getSqlSession().getConfiguration();
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        /**
         * 在Mybatis实现过程中并没有手动调用 configuration.addMapper 方法，而是在映射问阿金读取过程中一旦
         * 解析到如 <mapper namespace="Mapper.UserMapper"> ，便会自动进行类型映射的注册。那么 Spring中
         * 为什么会把这个功能单独拿出来放在这里验证呢？是不是多此一举呢？
         *
         * 该方法其实是将 UserMapper注册到映射类型中，如果你可以保证这个接口一定存在对应的映射文件，
         * 那么其实这个验证并没有必要，但是由于这个是我们自行决定的配置，无法保证这里配置的接口一定存在对应的
         * 映射文件，所以这里非常有必要进行验证。
         *
         * 在执行此代码的时候，Mybatis会检查嵌入的映射接口是否存在对应的映射文件，如果没有会抛出异常，
         * Spring 正是在用这种方式来完成接口对应的映射文件存在性验证。
         */
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * 由于 MapperFactoryBean 实现了 FactoryBean 接口，所以当通过 getBean 方法获取
   * 对应实例的时候其实是获取该类的 getObject函数返回的实例
   */
  @Override
  public T getObject() throws Exception {
    // 这段代码正是我们在提供 Mybatis 独立使用功能的时候的一个代码调用。Spring 通过FactoryBean 进行了封装
    // 获得 Mapper 对象。注意，返回的是基于 Mapper 接口自动生成的代理对象。
    return getSqlSession().getMapper(this.mapperInterface);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<T> getObjectType() {
    return this.mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  //------------- mutators --------------

  /**
   * Sets the mapper interface of the MyBatis mapper
   *
   * @param mapperInterface class of the interface
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * Return the mapper interface of the MyBatis mapper
   *
   * @return class of the interface
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * If addToConfig is false the mapper will not be added to MyBatis. This means
   * it must have been included in mybatis-config.xml.
   * <p>
   * If it is true, the mapper will be added to MyBatis in the case it is not already
   * registered.
   * <p>
   * By default addToConfig is true.
   *
   * @param addToConfig a flag that whether add mapper to MyBatis or not
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * Return the flag for addition into MyBatis config.
   *
   * @return true if the mapper will be added to MyBatis in the case it is not already
   * registered.
   */
  public boolean isAddToConfig() {
    return addToConfig;
  }
}
