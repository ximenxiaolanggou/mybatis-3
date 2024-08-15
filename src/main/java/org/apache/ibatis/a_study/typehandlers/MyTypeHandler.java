package org.apache.ibatis.a_study.typehandlers;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author zhishun.cai
 * @create 2024/7/1
 * @note
 */
public class MyTypeHandler extends BaseTypeHandler<String> {

  /**
   * 插入数据的时候回调的方法
   * @param ps
   * @param i
   * @param parameter
   * @param jdbcType
   * @throws SQLException
   */
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
    System.out.println("---------------setNonNullParameter1："+parameter);
    ps.setString(i, parameter);
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
    String name = rs.getString(columnName);
    if("超管".equals(name)){
      return name+"666";
    }
    return name;
  }

  @Override
  public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String name = rs.getString(columnIndex);
    if("超管".equals(name)){
      return name+"666";
    }
    return name;
  }

  @Override
  public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    String name = cs.getString(columnIndex);
    if("超管".equals(name)){
      return name+"666";
    }
    return name;
  }
}
