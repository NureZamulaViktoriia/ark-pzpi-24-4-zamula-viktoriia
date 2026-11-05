package com.rackguard.rackguardserver.config;


import com.rackguard.rackguardserver.model.User;
import com.rackguard.rackguardserver.repository.UserRepository;
import com.rackguard.rackguardserver.util.Const;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class AppConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasenames("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        return ms;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver r = new AcceptHeaderLocaleResolver();
        r.setDefaultLocale(Locale.ENGLISH);
        r.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("uk")));
        return r;
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("RackGuard API").version("1.0")
                        .description("IoT endpoints (/api/iot/**) need headers X-Device-Id, X-Timestamp (epoch seconds), "
                                + "X-Signature = HMAC-SHA256 hex of deviceId\\ntimestamp\\nMETHOD\\npath\\nbody."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public ApplicationRunner bootstrapAdmin(UserRepository users, PasswordEncoder encoder,
                                            @Value("${app.admin.email}") String email, @Value("${app.admin.password}") String password) {
        return args -> {
            if (users.count() == 0 && !password.isBlank()) {
                users.insert(new User(null, "Administrator", email, encoder.encode(password), Const.ADMIN, null, true));
            }
        };
    }
}