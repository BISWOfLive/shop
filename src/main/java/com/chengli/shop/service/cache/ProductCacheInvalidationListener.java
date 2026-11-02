package com.chengli.shop.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheInvalidationListener {
    private final ProductDetailCache cache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onProductChanged(ProductChangedEvent event) {
        try {
            cache.evict(event.productId());
        } catch (RuntimeException exception) {
            // 即使替换适配器，删除失败也不能改变已经提交的保存结果。
            log.warn("Product cache invalidation failed after commit: productId={}, errorType={}",
                    event.productId(), exception.getClass().getSimpleName());
        }
    }
}
