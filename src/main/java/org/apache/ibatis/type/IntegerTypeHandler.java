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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 以IntegerTypeHandler为例
 * 一般情况下，TypeHandler用于完成单个参数以及单个劣质的类型转换，如果存在多列值转换成一个Java对象的需求
 * 应该优先考虑使用在映射文件中定义合适的映射规则（<resultMap>）完成映射
 * @author Clinton Begin
 */
public class IntegerTypeHandler extends BaseTypeHandler<Integer> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Integer parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter);//调用了PreparedStatement.setInt()实现参数绑定
    }

    @Override
    public Integer getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        return rs.getInt(columnName);//调用了ResultSet.getInt()获取指定列
    }

    @Override
    public Integer getNullableResult(ResultSet rs, int columnIndex)
            throws SQLException {
        return rs.getInt(columnIndex);//调用了ResultSet.getInt()获取指定列
    }

    @Override
    public Integer getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        return cs.getInt(columnIndex);//调用了ResultSet.getInt()获取指定列
    }
}
