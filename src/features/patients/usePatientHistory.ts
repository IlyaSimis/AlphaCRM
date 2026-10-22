import { useEffect, useState } from 'react'
import { loadAppointments } from '../../api/schedule'
import type { PatientItem, ScheduleItem } from '../../api/types'
import { localToday } from '../../shared/dates'
import { isUuid } from '../../shared/text'
import { historyRange, localHistory } from './history'

/** Все записи пациента: с сервера, а в демо-режиме — из загруженного расписания. */
export function usePatientHistory(patient: PatientItem, apiOnline: boolean, localItems: ScheduleItem[], revision: number) {
  const remote = apiOnline && isUuid(patient.id)
  const [items, setItems] = useState<ScheduleItem[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!remote) return
    let cancelled = false
    loadAppointments({ ...historyRange(localToday()), patientId: patient.id })
      .then((rows) => { if (!cancelled) { setItems(rows); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [remote, patient.id, revision])

  return { items: remote ? items ?? [] : localHistory(localItems, patient), loading: remote && items === null && !error, error }
}
