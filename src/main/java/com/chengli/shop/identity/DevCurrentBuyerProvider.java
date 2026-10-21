package com.chengli.shop.identity;

import com.chengli.shop.config.DevBuyerProperties;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@EnableConfigurationProperties(DevBuyerProperties.class)
@RequiredArgsConstructor
public class DevCurrentBuyerProvider implements CurrentBuyerProvider {
    private final DevBuyerProperties properties;
    private final UserAccountMapper userAccountMapper;

    @Override
    public Long currentBuyerId() {
        Long id = properties.getBuyerId();
        if (id == null || id <= 0 || userAccountMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "请先配置存在的开发买家 shop.dev.buyer-id");
        }
        return id;
    }
}
