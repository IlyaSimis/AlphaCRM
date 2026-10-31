import { describe, expect, it } from 'vitest'
import { supportedFilePreview } from '../files/filePreview'
import { allows, expiryIn, tokenFrom } from './drive'

describe('drive rules', () => {
  it('ranks permissions: manage includes edit and view', () => {
    expect(allows({ permission: 'MANAGE' }, 'EDIT')).toBe(true)
    expect(allows({ permission: 'VIEW' }, 'EDIT')).toBe(false)
    expect(allows({ permission: null }, 'VIEW')).toBe(false)
  })

  it('reads the token from a pasted link or takes the token as is', () => {
    expect(tokenFrom('https://crm.example/?drive-link=abc_123')).toBe('abc_123')
    expect(tokenFrom(' abc_123 ')).toBe('abc_123')
  })

  it('sets link expiry to the end of the day N days ahead', () => {
    expect(expiryIn(7, new Date('2026-10-28T10:00:00Z'))).toBe('2026-11-04T23:59:00.000Z')
  })

  it('allows PDF, common images, DOCX and XLSX but not active content', () => {
    expect(supportedFilePreview('заключение.PDF')).toBe('pdf')
    expect(supportedFilePreview('снимок.jpg')).toBe('image')
    expect(supportedFilePreview('договор.docx')).toBe('office')
    expect(supportedFilePreview('отчёт.xlsx')).toBe('office')
    expect(supportedFilePreview('страница.html')).toBeNull()
    expect(supportedFilePreview('рисунок.svg')).toBeNull()
    expect(supportedFilePreview('текст.txt')).toBeNull()
  })
})
