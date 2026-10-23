package ru.quietclinic.scheduling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Недельный график врача целиком. Дня нет в списке — выходной. */
record WorkingHoursRequest(@NotNull List<@Valid ShiftRequest> shifts) {}
