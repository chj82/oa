package com.oa.boot.config;

import com.oa.boot.security.FrontendOrigin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web 跨域配置。 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
  private final String frontendOrigin;

  public WebConfiguration(
      @Value("${app.security.frontend-origin:http://localhost:3000}") String frontendOrigin) {
    this.frontendOrigin = FrontendOrigin.normalize(frontendOrigin);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(frontendOrigin)
        .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type")
        .allowCredentials(true);
  }
}
