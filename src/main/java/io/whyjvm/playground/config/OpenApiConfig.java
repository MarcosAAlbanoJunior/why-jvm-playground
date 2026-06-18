package io.whyjvm.playground.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados da pagina Swagger (OpenAPI) exposta em {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI playgroundOpenApi() {
        return new OpenAPI().info(new Info()
                .title("why-jvm playground")
                .version("0.0.1")
                .description("""
                        Aplicacao-alvo para testar o agente why-jvm. Cada grupo de endpoints
                        provoca um cenario real: erro, lentidao, N+1 de JDBC e exaustao de pool.
                        As rotas rapidas sao aquecidas na subida para o baseline de latencia."""));
    }
}
