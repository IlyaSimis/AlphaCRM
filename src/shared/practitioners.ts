import type { Practitioner } from '../api/types'

/** «Психиатр · кабинет 204»; без кабинета — только специальность. */
export function specialtyAndRoom(practitioner: Pick<Practitioner, 'specialty' | 'room'>) {
  return practitioner.room ? `${practitioner.specialty} · кабинет ${practitioner.room}` : practitioner.specialty
}
