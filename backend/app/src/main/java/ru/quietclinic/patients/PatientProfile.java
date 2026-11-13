package ru.quietclinic.patients;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Расширенная административная карточка. Хранится одним JSONB-документом: набор реквизитов можно
 * расширять без новой цепочки столбцов. Медицинские записи сюда не входят.
 */
record PatientProfile(
    @Size(max = 255) String lastName,
    @Size(max = 255) String firstName,
    @Size(max = 255) String middleName,
    @Pattern(regexp = "MALE|FEMALE|UNSPECIFIED") String gender,
    @Size(max = 120) String addressType,
    @Size(max = 255) String region,
    @Size(max = 255) String locality,
    @Size(max = 255) String street,
    @Size(max = 80) String house,
    @Size(max = 80) String apartment,
    @Size(max = 255) String employer,
    @Size(max = 255) String professionCompany,
    @Size(max = 255) String professionNsi,
    LocalDate workStartDate,
    @Size(max = 120) String bonusCard,
    @Size(max = 120) String documentType,
    @Size(max = 120) String documentSeriesNumber,
    LocalDate documentIssueDate,
    @Size(max = 40) String documentDepartmentCode,
    @Size(max = 500) String documentIssuedBy,
    @Size(max = 120) String contractNumber,
    LocalDate contractFrom,
    LocalDate contractTo,
    @Size(max = 20) List<@Valid PatientRepresentative> representatives,
    @Size(max = 40) String snils,
    @Size(max = 40) String inn,
    @Size(max = 255) String referralAgent,
    @Size(max = 50) List<@Size(max = 80) String> tags,
    @Size(max = 120) String ambulatoryNumber,
    @Size(max = 5000) String ambulatoryNotes,
    Boolean emailNotifications,
    Boolean smsNotifications,
    Boolean messengerNotifications,
    Boolean pushNotifications) {}
