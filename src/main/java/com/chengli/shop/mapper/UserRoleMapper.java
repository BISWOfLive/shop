package com.chengli.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengli.shop.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色联合键访问。本表没有单列主键，任何读写都必须同时给出 user_id 与 role_id。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
