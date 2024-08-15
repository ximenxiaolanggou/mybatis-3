package org.apache.ibatis.a_study.mapper;

import org.apache.ibatis.a_study.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    List<User> selectList();
}
