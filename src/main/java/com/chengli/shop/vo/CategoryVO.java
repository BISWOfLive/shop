package com.chengli.shop.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class CategoryVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String name;
    private Integer sortOrder;
}
