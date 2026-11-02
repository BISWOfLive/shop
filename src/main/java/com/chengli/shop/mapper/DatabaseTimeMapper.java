package com.chengli.shop.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;

@Mapper
public interface DatabaseTimeMapper {
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    LocalDateTime currentTime();
}
