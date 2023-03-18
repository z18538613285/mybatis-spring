/**
 *    Copyright 2010-2016 the original author or authors.
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
package org.mybatis.spring.config;

import java.lang.annotation.Annotation;

import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * A {#code BeanDefinitionParser} that handles the element scan of the MyBatis.
 * namespace
 * 
 * @author Lishu Luo
 * @author Eduardo Macarron
 *
 * @since 1.2.0
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 *
 * @tips <mybatis:scan /> 的解析器
 */
public class MapperScannerBeanDefinitionParser implements BeanDefinitionParser {

  private static final String ATTRIBUTE_BASE_PACKAGE = "base-package";
  private static final String ATTRIBUTE_ANNOTATION = "annotation";
  private static final String ATTRIBUTE_MARKER_INTERFACE = "marker-interface";
  private static final String ATTRIBUTE_NAME_GENERATOR = "name-generator";
  private static final String ATTRIBUTE_TEMPLATE_REF = "template-ref";
  private static final String ATTRIBUTE_FACTORY_REF = "factory-ref";

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized BeanDefinition parse(Element element, ParserContext parserContext) {
    // 创建 ClassPathMapperScanner 对象
    ClassPathMapperScanner scanner = new ClassPathMapperScanner(parserContext.getRegistry());
    ClassLoader classLoader = scanner.getResourceLoader().getClassLoader();
    XmlReaderContext readerContext = parserContext.getReaderContext();
    // 设置 resourceLoader 属性
    scanner.setResourceLoader(readerContext.getResourceLoader());
    try {
      // 解析 annotation 属性
      String annotationClassName = element.getAttribute(ATTRIBUTE_ANNOTATION);
      if (StringUtils.hasText(annotationClassName)) {
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> markerInterface = (Class<? extends Annotation>) classLoader.loadClass(annotationClassName);
        scanner.setAnnotationClass(markerInterface);
      }
      // 解析 marker-interface 属性
      String markerInterfaceClassName = element.getAttribute(ATTRIBUTE_MARKER_INTERFACE);
      if (StringUtils.hasText(markerInterfaceClassName)) {
        Class<?> markerInterface = classLoader.loadClass(markerInterfaceClassName);
        scanner.setMarkerInterface(markerInterface);
      }
      // 解析 name-generator 属性
      String nameGeneratorClassName = element.getAttribute(ATTRIBUTE_NAME_GENERATOR);
      if (StringUtils.hasText(nameGeneratorClassName)) {
        Class<?> nameGeneratorClass = classLoader.loadClass(nameGeneratorClassName);
        BeanNameGenerator nameGenerator = BeanUtils.instantiateClass(nameGeneratorClass, BeanNameGenerator.class);
        scanner.setBeanNameGenerator(nameGenerator);
      }
    } catch (Exception ex) {
      readerContext.error(ex.getMessage(), readerContext.extractSource(element), ex.getCause());
    }
    // 解析 template-ref 属性
    String sqlSessionTemplateBeanName = element.getAttribute(ATTRIBUTE_TEMPLATE_REF);
    scanner.setSqlSessionTemplateBeanName(sqlSessionTemplateBeanName);
    // 解析 factory-ref 属性
    String sqlSessionFactoryBeanName = element.getAttribute(ATTRIBUTE_FACTORY_REF);
    scanner.setSqlSessionFactoryBeanName(sqlSessionFactoryBeanName);
    // 注册 scanner 的过滤器
    scanner.registerFilters();
    // 获得要扫描的包
    String basePackage = element.getAttribute(ATTRIBUTE_BASE_PACKAGE);
    // 执行扫描
    scanner.scan(StringUtils.tokenizeToStringArray(basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
    return null;
  }

}
