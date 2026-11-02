package com.chengli.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/**
 * 账号口令编码。v1 参数固定：PBKDF2-HMAC-SHA256、16 字节随机盐、256 bit 派生结果、600000 次迭代，
 * 存储前缀固定 {pbkdf2-v1}。旧前缀的含义不随实现调整，升级口令须新增版本前缀。
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {

    public static final String PREFIX = "{pbkdf2-v1}";

    private static final int SALT_LENGTH = 16;
    private static final int ITERATIONS = 600000;
    private static final int HASH_WIDTH = 256;

    @Bean
    public PasswordEncoder passwordEncoder() {
        Pbkdf2PasswordEncoder delegate = new Pbkdf2PasswordEncoder("", SALT_LENGTH, ITERATIONS,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        delegate.setEncodeHashAsBase64(true);
        return new VersionedPbkdf2PasswordEncoder(delegate);
    }

    /** 在 Spring Security PBKDF2 结果前追加固定版本前缀，并拒绝非本版本或损坏的口令值。 */
    static final class VersionedPbkdf2PasswordEncoder implements PasswordEncoder {

        private final Pbkdf2PasswordEncoder delegate;

        VersionedPbkdf2PasswordEncoder(Pbkdf2PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return PREFIX + this.delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (rawPassword == null || encodedPassword == null || !encodedPassword.startsWith(PREFIX)) {
                return false;
            }
            String encoded = encodedPassword.substring(PREFIX.length());
            if (encoded.isBlank()) {
                return false;
            }
            try {
                return this.delegate.matches(rawPassword, encoded);
            } catch (RuntimeException invalidEncoding) {
                return false;
            }
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return false;
        }
    }
}
