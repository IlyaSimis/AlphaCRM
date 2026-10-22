import { describe, expect, it } from 'vitest'
import type { DocumentSummary } from '../../api/types'
import { activeDocument, isLocked, latestSigned, noteComplete, noteOf, upsertDocument, visitTimeLabel } from './visit'

const signed: DocumentSummary = { id: 'd1', state: 'SIGNED_LOCKED', templateVersion: 'v2.4', signedAt: '2026-10-20T10:06:00Z', amendsDocumentId: null }
const amendment: DocumentSummary = { id: 'd2', state: 'DRAFT', templateVersion: 'v2.4', signedAt: null, amendsDocumentId: 'd1' }

describe('приём пациента', () => {
  it('требует все четыре поля заметки', () => {
    expect(noteComplete({ complaints: 'а', mentalStatus: 'б', diagnosis: 'в', recommendations: 'г' })).toBe(true)
    expect(noteComplete({ complaints: 'а', mentalStatus: ' ', diagnosis: 'в', recommendations: 'г' })).toBe(false)
  })

  it('берёт текст заметки из приёма, а не из шаблона', () => {
    expect(noteOf(null)).toEqual({ complaints: '', mentalStatus: '', diagnosis: '', recommendations: '' })
    expect(noteOf({ encounterId: 'e', appointmentId: 'a', patientId: 'p', practitionerId: 'd', status: 'IN_PROGRESS', startedAt: '', diagnosis: 'Диагноз', documents: [] }).diagnosis).toBe('Диагноз')
  })

  it('подписанная версия блокирует форму, пока нет дополнения', () => {
    expect(isLocked([])).toBe(false)
    expect(isLocked([signed])).toBe(true)
    expect(isLocked([signed, amendment])).toBe(false)
    expect(activeDocument([signed, amendment])?.id).toBe('d2')
    expect(latestSigned([signed, amendment])?.id).toBe('d1')
  })

  it('обновляет версию на месте или добавляет новую', () => {
    expect(upsertDocument([amendment], { ...amendment, state: 'SIGNED_LOCKED' })).toHaveLength(1)
    expect(upsertDocument([signed], amendment).map((doc) => doc.id)).toEqual(['d1', 'd2'])
  })

  it('показывает время приёма по длительности записи', () => {
    expect(visitTimeLabel({ patientId: 'p', patientName: 'Анна', practitionerId: 'd', time: '09:00', minutes: 50 })).toBe('09:00–09:50')
    expect(visitTimeLabel({ patientId: 'p', patientName: 'Анна', practitionerId: 'd' })).toBe('')
  })
})
