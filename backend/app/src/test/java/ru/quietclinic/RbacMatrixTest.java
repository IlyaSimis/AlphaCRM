package ru.quietclinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Матрица RBAC из docs/target-architecture.md (раздел C) против кода: каждая строка {@code
 * rbac-matrix.csv} — роль, метод, путь, ожидаемый статус. {@code 403} — сервер отказал по
 * разрешению роли (ACCESS_DENIED или ROLE_NOT_ASSIGNABLE); {@code ok} — разрешение есть, ответ не
 * 401 и не 403 (дальше решают данные: 404 для несуществующего id — тоже «ok»). Тест падает, если
 * матрица и код разошлись.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RbacMatrixTest {
  static final String ANNA = "00000000-0000-0000-0000-000000000101";

  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  static Stream<Arguments> matrix() throws Exception {
    var rows = new ArrayList<Arguments>();
    try (var input = RbacMatrixTest.class.getResourceAsStream("/rbac-matrix.csv");
        var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.startsWith("#") || line.startsWith("role;")) continue;
        var cells = line.split(";", 5);
        rows.add(Arguments.of(cells[0], cells[1], cells[2], cells[3], cells[4]));
      }
    }
    return rows.stream();
  }

  @ParameterizedTest(name = "{0} {1} {2} → {3}")
  @MethodSource("matrix")
  void roleMatchesMatrix(String role, String method, String path, String expected, String body)
      throws Exception {
    var call =
        request(HttpMethod.valueOf(method), substitute(path))
            .with(staff.exactly(role))
            .contentType(MediaType.APPLICATION_JSON);
    if (!body.isBlank()) call.content(substitute(body));
    var response = mvc.perform(call).andReturn().getResponse();
    var status = response.getStatus();
    if ("403".equals(expected)) {
      assertThat(status).as("%s %s %s", role, method, path).isEqualTo(403);
      String code = JsonPath.read(response.getContentAsString(), "$.code");
      assertThat(code).isIn(List.of("ACCESS_DENIED", "ROLE_NOT_ASSIGNABLE"));
    } else {
      assertThat(status)
          .as("%s %s %s: %s", role, method, path, response.getContentAsString())
          .isNotIn(401, 403);
    }
  }

  private static String substitute(String text) {
    return text.replace("{random}", UUID.randomUUID().toString())
        .replace("{patient}", ANNA)
        .replace("{doctor}", StaffSessions.PETROV)
        .replace("{unique}", UUID.randomUUID().toString().substring(0, 10));
  }
}
