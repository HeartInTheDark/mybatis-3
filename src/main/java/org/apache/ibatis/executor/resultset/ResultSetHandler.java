/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.executor.resultset;

import org.apache.ibatis.cursor.Cursor;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * @author Clinton Begin
 */
public interface ResultSetHandler {

  //处理结果集，生成相应的结果对象集合
  <E> List<E> handleResultSets(Statement stmt) throws SQLException;

  //处理结果集，返回相应的游标对象
  <E> Cursor<E> handleCursorResultSets(Statement stmt) throws SQLException;

  //处理存储过程输出的参数
  void handleOutputParameters(CallableStatement cs) throws SQLException;

}
