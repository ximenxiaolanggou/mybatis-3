package org.apache.ibatis.a_study.reflector;

import org.apache.ibatis.a_study.entity.User;
import org.apache.ibatis.reflection.Reflector;

import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author zhishun.cai
 * @create 2024/7/2
 * @note
 */
public class TestReflector {


  public static void main(String[] args) {

    Reflector useReflector = new Reflector(User.class);

  }
}
