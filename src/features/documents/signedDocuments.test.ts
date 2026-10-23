import { describe, expect, it } from 'vitest'
import type { SignedDocumentItem } from '../../api/types'
import { documentKind, preview, signedMoment } from './signedDocuments'

const doc = (id: string, amendsDocumentId: string | null = null): SignedDocumentItem => ({
  id, encounterId: 'e1', appointmentId: 'a1', patientId: 'p1', practitionerId: 'd1', signedAt: '2026-10-20T09:40:00Z', templateVersion: 'v2.4', amendsDocumentId,
})

describe('signed documents archive', () => {
  it('labels an amendment with a link to the original document', () => {
    const original = doc('d1')
    const amendment = doc('d2', 'd1')
    expect(documentKind(original, [original, amendment])).toBe('Документ приёма')
    expect(documentKind(amendment, [original, amendment])).toBe(`Дополнение к документу от ${signedMoment(original.signedAt)}`)
    expect(documentKind(doc('d3', 'missing'), [])).toBe('Дополнение к документу')
  })

  it('truncates long text for a list row', () => {
    expect(preview(null)).toBe('—')
    expect(preview('коротко')).toBe('коротко')
    expect(preview('а'.repeat(100), 10)).toBe(`${'а'.repeat(9)}…`)
  })
})
