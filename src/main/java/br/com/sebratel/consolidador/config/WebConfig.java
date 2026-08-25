package br.com.sebratel.consolidador.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Libera os dev servers do frontend a chamar a API: Vite/React (porta 5173,
 * novo frontend) e Angular (porta 4200, ainda em paralelo durante a migracao
 * para React - ver spec/react-migration.md).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:4200")
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}
