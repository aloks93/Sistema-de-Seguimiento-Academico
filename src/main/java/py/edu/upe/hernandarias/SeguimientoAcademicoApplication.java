package py.edu.upe.hernandarias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SeguimientoAcademicoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeguimientoAcademicoApplication.class, args);
    }
}
