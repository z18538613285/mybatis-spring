/**
 *    Copyright 2010-2019 the original author or authors.
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
package org.mybatis.spring.transaction;

import static org.springframework.util.Assert.notNull;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.ibatis.transaction.Transaction;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code SpringManagedTransaction} handles the lifecycle of a JDBC connection.
 * It retrieves a connection from Spring's transaction manager and returns it back to it
 * when it is no longer needed.
 * <p>
 * If Spring's transaction handling is active it will no-op all commit/rollback/close calls
 * assuming that the Spring transaction manager will do the job.
 * <p>
 * If it is not it will behave like {@code JdbcTransaction}.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @tips Spring 托管事务的 Transaction 实现类。
 */
public class SpringManagedTransaction implements Transaction {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpringManagedTransaction.class);
  /**
   * DataSource 对象
   */
  private final DataSource dataSource;
  /**
   * Connection 对象
   */
  private Connection connection;
  /**
   * 当前连接是否处于事务中
   *
   * @see DataSourceUtils#isConnectionTransactional(Connection, DataSource)
   */
  private boolean isConnectionTransactional;
  /**
   * 是否自动提交
   */
  private boolean autoCommit;

  public SpringManagedTransaction(DataSource dataSource) {
    notNull(dataSource, "No DataSource specified");
    this.dataSource = dataSource;
  }

  /**
   * {@inheritDoc}
   * 获得连接。
   */
  @Override
  public Connection getConnection() throws SQLException {
    if (this.connection == null) {
      // 如果连接不存在，获得连接
      openConnection();
    }
    return this.connection;
  }

  /**
   * Gets a connection from Spring transaction manager and discovers if this
   * {@code Transaction} should manage connection or let it to Spring.
   * <p>
   * It also reads autocommit setting because when using Spring Transaction MyBatis
   * thinks that autocommit is always false and will always call commit/rollback
   * so we need to no-op that calls.
   *
   * @tips 比较有趣的是，此处获取连接，不是通过 DataSource#getConnection() 方法，而是
   * 通过 org.springframework.jdbc.datasource.DataSourceUtils#getConnection(DataSource dataSource) 方法，
   * 获得 Connection 对象。而实际上，基于 Spring Transaction 体系，如果此处正在事务中时，
   * 已经有和当前线程绑定的 Connection 对象，就是存储在 ThreadLocal 中。
   */
  private void openConnection() throws SQLException {
    // 获得连接
    this.connection = DataSourceUtils.getConnection(this.dataSource);
    this.autoCommit = this.connection.getAutoCommit();
    this.isConnectionTransactional = DataSourceUtils.isConnectionTransactional(this.connection, this.dataSource);

    LOGGER.debug(() ->
        "JDBC Connection ["
            + this.connection
            + "] will"
            + (this.isConnectionTransactional ? " " : " not ")
            + "be managed by Spring");
  }

  /**
   * {@inheritDoc}
   * 提交事务
   */
  @Override
  public void commit() throws SQLException {
    if (this.connection != null && !this.isConnectionTransactional && !this.autoCommit) {
      LOGGER.debug(() -> "Committing JDBC Connection [" + this.connection + "]");
      this.connection.commit();
    }
  }

  /**
   * {@inheritDoc}
   * 回滚事务
   */
  @Override
  public void rollback() throws SQLException {
    if (this.connection != null && !this.isConnectionTransactional && !this.autoCommit) {
      LOGGER.debug(() -> "Rolling back JDBC Connection [" + this.connection + "]");
      this.connection.rollback();
    }
  }

  /**
   * {@inheritDoc}
   * 释放连接。
   *
   * 比较有趣的是，此处释放连接，不是通过 Connection#close() 方法，而是通过
   * org.springframework.jdbc.datasource.DataSourceUtils#releaseConnection(Connection connection,DataSource dataSource)
   * 方法，“释放”连接。但是，具体会不会关闭连接，根据当前线程绑定的 Connection 对象，是不是传入的 connection 参数。
   */
  @Override
  public void close() {
    DataSourceUtils.releaseConnection(this.connection, this.dataSource);
  }
    
  /**
   * {@inheritDoc}
   */
  @Override
  public Integer getTimeout() {
    ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
    if (holder != null && holder.hasTimeout()) {
      return holder.getTimeToLiveInSeconds();
    } 
    return null;
  }

}
