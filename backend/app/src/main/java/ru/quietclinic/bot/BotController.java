package ru.quietclinic.bot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API Telegram-бота клиники. Вход — заголовок X-Bot-Token, см. {@link BotSecurityConfig}. */
@RestController
@RequestMapping("/api/v1/bot")
public class BotController {
  private final BotService bot;

  BotController(BotService bot) {
    this.bot = bot;
  }

  @GetMapping("/clinic")
  BotClinicView clinic() {
    return bot.clinic();
  }

  @GetMapping("/practitioners")
  List<BotPractitionerView> practitioners() {
    return bot.practitioners();
  }

  @GetMapping("/practitioners/{id}")
  BotPractitionerView practitioner(@PathVariable UUID id) {
    return bot.practitioner(id);
  }

  @GetMapping("/practitioners/{id}/slots")
  List<BotSlotView> slots(@PathVariable UUID id) {
    return bot.slots(id);
  }

  @GetMapping("/patients/by-telegram/{chatId}")
  BotPatientView patient(@PathVariable @Pattern(regexp = BotChatRequest.CHAT_ID) String chatId) {
    return bot.patient(chatId);
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  BotAppointmentView book(@Valid @RequestBody BotBookingRequest request) {
    return bot.book(request);
  }

  @GetMapping("/appointments/{id}")
  BotAppointmentView appointment(
      @PathVariable UUID id,
      @RequestParam @Pattern(regexp = BotChatRequest.CHAT_ID) String telegramChatId) {
    return bot.appointment(id, telegramChatId);
  }

  @PostMapping("/appointments/{id}/confirm")
  BotAppointmentView confirm(@PathVariable UUID id, @Valid @RequestBody BotChatRequest request) {
    return bot.confirm(id, request.telegramChatId());
  }

  @PostMapping("/appointments/{id}/cancel")
  BotAppointmentView cancel(@PathVariable UUID id, @Valid @RequestBody BotChatRequest request) {
    return bot.cancel(id, request.telegramChatId());
  }

  @PostMapping("/operator-requests")
  @ResponseStatus(HttpStatus.CREATED)
  BotTicketView operatorRequest(@Valid @RequestBody BotOperatorRequest request) {
    return bot.operatorRequest(request);
  }
}
