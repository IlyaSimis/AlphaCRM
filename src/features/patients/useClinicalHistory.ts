import { useEffect, useState } from 'react'
import { loadEncounters } from '../../api/clinical'
import type { EncounterView } from '../../api/types'

/**
 * Клиническая история для врача. Если связи с лечением нет, сервер отвечает отказом —
 * его текст и показываем, ничего не подставляя взамен.
 */
export function useClinicalHistory(patientId: string, enabled: boolean, doctorId: string, revision: number) {
  const [encounters, setEncounters] = useState<EncounterView[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    setEncounters(null)
    setError('')
    loadEncounters(patientId)
      .then((rows) => { if (!cancelled) setEncounters(rows) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [enabled, patientId, doctorId, revision])

  return { encounters: encounters ?? [], loading: enabled && encounters === null && !error, error }
}
