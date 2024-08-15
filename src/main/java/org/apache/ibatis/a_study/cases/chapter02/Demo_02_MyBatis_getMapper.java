package org.apache.ibatis.a_study.cases.chapter02;

import org.apache.ibatis.a_study.entity.User;
import org.apache.ibatis.a_study.mapper.UserMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.InputStream;
import java.util.List;

/**
 * @author zhishun.cai
 * @create 2024/7/1
 * @note
 */
public class Demo_02_MyBatis_getMapper {

  public static void main(String[] args) throws Exception {

    // 1.获取配置文件
    InputStream in = Resources.getResourceAsStream("mybatis-config.xml");
    // 2.加载配置文件。获取SqlSessionFactory对象
    SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(in);
    // 3.根据SqlSessionFactory对象获取SqlSession对象
    SqlSession sqlSession = factory.openSession();
    // 4.通过SqlSession中提供的 API方法来操作数据库
    UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
    List<User> users = userMapper.selectList();
    System.out.println(users);
  }
}
