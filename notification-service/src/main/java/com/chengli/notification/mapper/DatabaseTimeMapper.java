package com.chengli.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface DatabaseTimeMapper {
    /** 通知落库时间与已读时间都取通知库时钟，不依赖应用主机时间。 */
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    LocalDateTime currentTime();
}
