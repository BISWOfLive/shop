package com.chengli.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.ToString;

/**
 * 用户账号；角色关系单独存储，不在实体中维护关联对象。
 */
@Data
@TableName("user_account")
public class UserAccount {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    /** 仅存编码后的口令，不作为响应字段。 */
    @JsonIgnore
    @ToString.Exclude
    @TableField("password_hash")
    private String passwordHash;

    private String nickname = "";

    /** 0：禁用，1：启用。 */
    private Integer status = 0;

    @TableField(value = "created_at", fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
