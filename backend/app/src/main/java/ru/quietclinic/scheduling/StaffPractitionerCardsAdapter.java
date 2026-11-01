package ru.quietclinic.scheduling;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.quietclinic.staff.PractitionerCards;
import ru.quietclinic.staff.ShiftSpec;

/**
 * Карточка врача для «Сотрудников»: та же проверка смен и тот же аудит, что у POST /practitioners.
 * Транзакцию открывает вызывающий, поэтому ошибка учётной записи откатывает и карточку.
 */
@Component
class StaffPractitionerCardsAdapter implements PractitionerCards {
  private final PractitionerService practitioners;

  StaffPractitionerCardsAdapter(PractitionerService practitioners) {
    this.practitioners = practitioners;
  }

  @Override
  public UUID create(String fullName, String specialty, String room, List<ShiftSpec> shifts) {
    CreatePractitionerRequest request =
        new CreatePractitionerRequest(
            fullName,
            specialty,
            room,
            shifts.stream()
                .map(s -> new ShiftRequest(s.dayOfWeek(), s.startsAt(), s.endsAt()))
                .toList());
    return practitioners.create(request).id();
  }
}
