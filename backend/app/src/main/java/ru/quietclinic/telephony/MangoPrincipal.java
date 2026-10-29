package ru.quietclinic.telephony;

import java.util.UUID;
import ru.quietclinic.platform.ClinicMember;

/** Сервисный вызов Mango действует только внутри одной клиники и не имеет прав сотрудника. */
record MangoPrincipal(UUID clinicId) implements ClinicMember {}
