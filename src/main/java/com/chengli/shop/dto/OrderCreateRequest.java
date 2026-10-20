package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 下单意图；买家身份、订单号、金额、状态和请求摘要由服务端生成。 */
@Data
public class OrderCreateRequest {
    @NotBlank(message = "下单请求号不能为空")
    @Size(max = 64, message = "下单请求号不能超过 64 个字符")
    @Pattern(regexp = "[\\x21-\\x7E]+", message = "下单请求号仅支持无空格的可见 ASCII 字符")
    private String idempotencyKey;

    @NotEmpty(message = "订单至少需要一件商品")
    @Valid
    private List<@NotNull OrderItemCreateRequest> items;
}
