package ru.quietclinic;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@Modulith
@SpringBootApplication
@OpenAPIDefinition(
    info =
        @Info(
            title = "Quiet Clinic API",
            version = "v1",
            description =
                "MVP API. Не является заявлением о соответствии регуляторным требованиям."))
public class MedicalApplication {
  public static void main(String[] args) {
    SpringApplication.run(MedicalApplication.class, args);
  }
}
