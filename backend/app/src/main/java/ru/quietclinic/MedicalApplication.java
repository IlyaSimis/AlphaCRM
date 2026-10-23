package ru.quietclinic;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@Modulith
@SpringBootApplication
@OpenAPIDefinition(
    info =
        @Info(
            title = "AlphaCRM API",
            version = "v1",
            description = "MVP API. Not a statement of regulatory compliance."))
public class MedicalApplication {
  public static void main(String[] args) {
    // Всё время в приложении — UTC (смены врачей, записи). Без этого Hibernate с
    // hibernate.jdbc.time_zone=UTC сдвигает TIME смены на пояс JVM (в Москве — на +3 ч).
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(MedicalApplication.class, args);
  }
}
