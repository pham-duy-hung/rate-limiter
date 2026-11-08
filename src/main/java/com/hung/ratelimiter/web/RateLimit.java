package com.hung.ratelimiter.web;


import com.hung.ratelimiter.core.Algorithm;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    Algorithm algorithm() default Algorithm.TOKEN_BUCKET;

    // Số request tối đa mỗi cửa sổ. Giá trị <= 0 nghĩa là dùng mặc định trong yml.
    int limit() default -1;

   //thời gian của ửa sổ tính bằng giâu. value <=0 nghĩa là dùng mặc định trong yml
    long windowSeconds() default -1;

    KeyBy key() default KeyBy.IP;
}
