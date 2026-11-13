import { describe, expect, it } from 'vitest'
import type { DocumentSummary } from '../../api/types'
import { activeDocument, isLocked, latestSigned, noteComplete, noteOf, upsertDocument, visitTimeLabel } from './visitRules'

const signed: DocumentSummary = { id: 'd1', state: 'SIGNED_LOCKED', templateVersion: 'v2.4', signedAt: '2026-10-20T10:06:00Z', amendsDocumentId: null }
const amendment: DocumentSummary = { id: 'd2', state: 'DRAFT', templateVersion: 'v2.4', signedAt: null, amendsDocumentId: 'd1' }

describe('patient visit', () => {
  it('requires all four note fields', () => {
    expect(noteComplete({ complaints: 'а', mentalStatus: 'б', diagnosis: 'в', recommendations: 'г' })).toBe(true)
    expect(noteComplete({ complaints: 'а', mentalStatus: ' ', diagnosis: 'в', recommendations: 'г' })).toBe(false)
  })

  it('takes the note text from the visit, not from the template', () => {
    expect(noteOf(null)).toEqual({ complaints: '', mentalStatus: '', diagnosis: '', recommendations: '' })
    expect(noteOf({ encounterId: 'e', appointmentId: 'a', patientId: 'p', practitionerId: 'd', status: 'IN_PROGRESS', startedAt: '', diagnosis: 'Диагноз', documents: [] }).diagnosis).toBe('Диагноз')
  })

  it('a signed version locks the form until there is an amendment', () => {
    expect(isLocked([])).toBe(false)
    expect(isLocked([signed])).toBe(true)
    expect(isLocked([signed, amendment])).toBe(false)
    expect(activeDocument([signed, amendment])?.id).toBe('d2')
    expect(latestSigned([signed, amendment])?.id).toBe('d1')
  })

  it('updates a version in place or adds a new one', () => {
    expect(upsertDocument([amendment], { ...amendment, state: 'SIGNED_LOCKED' })).toHaveLength(1)
    expect(upsertDocument([signed], amendment).map((doc) => doc.id)).toEqual(['d1', 'd2'])
  })

  it('shows visit time by appointment duration', () => {
    expect(visitTimeLabel({ patientId: 'p', patientName: 'Анна', practitionerId: 'd', time: '09:00', minutes: 50 })).toBe('09:00–09:50')
    expect(visitTimeLabel({ patientId: 'p', patientName: 'Анна', practitionerId: 'd' })).toBe('')
  })
})
