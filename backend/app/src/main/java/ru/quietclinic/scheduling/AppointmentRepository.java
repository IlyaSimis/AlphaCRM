package ru.quietclinic.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
  @Query(
      """
      select count(a) from Appointment a
      where a.practitionerId = :practitionerId
        and a.status in ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS','COMPLETED')
        and a.startsAt < :endsAt and a.endsAt > :startsAt
      """)
  long countOverlaps(
      @Param("practitionerId") UUID practitionerId,
      @Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt);

  /** То же пересечение, но без самой переносимой записи. */
  @Query(
      """
      select count(a) from Appointment a
      where a.practitionerId = :practitionerId
        and a.id <> :excludedId
        and a.status in ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS','COMPLETED')
        and a.startsAt < :endsAt and a.endsAt > :startsAt
      """)
  long countOverlapsExcluding(
      @Param("practitionerId") UUID practitionerId,
      @Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt,
      @Param("excludedId") UUID excludedId);

  /** Занятое время врача в окне: те же статусы, что считает проверка пересечения. */
  @Query(
      """
      select a from Appointment a
      where a.practitionerId = :practitionerId
        and a.status in ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS','COMPLETED')
        and a.startsAt < :to and a.endsAt > :from
      order by a.startsAt
      """)
  List<Appointment> findBusy(
      @Param("practitionerId") UUID practitionerId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  List<Appointment> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
      Instant from, Instant to);

  List<Appointment> findByPatientIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
      UUID patientId, Instant from, Instant to);
}
