package ru.quietclinic.communications;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Общие настройки напоминаний текущей клиники. */
@RestController
@RequestMapping("/api/v1/clinic/notification-settings")
public class NotificationSettingsController {
  private final NotificationSettingsService settings;

  NotificationSettingsController(NotificationSettingsService settings) {
    this.settings = settings;
  }

  @GetMapping
  NotificationSettings get() {
    return settings.get();
  }

  @PutMapping
  NotificationSettings update(@RequestBody NotificationSettings request) {
    return settings.update(request);
  }
}
