package org.apache.ibatis.a_study.cases.chapter01;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * @author zhishun.cai
 * @create 2024/7/1
 * @note JDBC 测试
 */
public class Demo_01_JDBC {

  private static String url = "jdbc:mysql://150.158.78.149:3306/mybatis?useUnicode=true&characterEncoding=UTF-8&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT%2b8";
  private static String username = "root";
  private static String pwd = "zhishun.cai";

  public static void main(String[] args) throws Exception {
    // 注册驱动
    Class.forName("com.mysql.cj.jdbc.Driver");

    // 打开连接
    Connection connection = DriverManager.getConnection(url, username, pwd);

    // 执行sql
    Statement statement = connection.createStatement();
    ResultSet resultSet = statement.executeQuery("select * from user");

    // 遍历输出
    while (resultSet.next()) {
      int id = resultSet.getInt("id");
      String username = resultSet.getString("username");
      String age = resultSet.getString("age");
      System.out.println(id + " " + username + " " + age);
    }
  }
}
