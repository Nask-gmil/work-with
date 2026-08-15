package jp.workwith.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVCへ共通のAPIログインチェックを登録します。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 今後追加するrooms、seats、chatなどを含むAPI全体が対象です。
                .addPathPatterns("/api/**")
                // 登録とログインは、未ログイン状態で利用する必要があります。
                .excludePathPatterns(
                        "/api/users/register",
                        "/api/users/login",
                        "/api/public-config");
    }
}
