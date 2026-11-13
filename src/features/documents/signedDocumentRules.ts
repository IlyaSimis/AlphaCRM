import type { SignedDocumentItem } from '../../api/types'
import { clinicTimeZone } from '../../shared/clinicTime'

/** Поля подписанного снимка в порядке заметки приёма. */
export const snapshotFields: Array<{ key: 'complaints' | 'mentalStatus' | 'diagnosis' | 'recommendations'; label: string }> = [
  { key: 'complaints', label: 'Жалобы и анамнез' },
  { key: 'mentalStatus', label: 'Психический статус' },
  { key: 'diagnosis', label: 'Диагноз' },
  { key: 'recommendations', label: 'Назначения и рекомендации' },
]

export function signedMoment(iso: string) {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: clinicTimeZone() })
}

/** «Дополнение к документу от 20.10.2026 09:40» или «Документ приёма». */
export function documentKind(doc: SignedDocumentItem, all: SignedDocumentItem[]) {
  if (!doc.amendsDocumentId) return 'Документ приёма'
  const original = all.find((item) => item.id === doc.amendsDocumentId)
  return original ? `Дополнение к документу от ${signedMoment(original.signedAt)}` : 'Дополнение к документу'
}

/** Короткая строка для списка: длинный текст обрезается, полный — в карточке. */
export function preview(text: string | null | undefined, limit = 80) {
  if (!text) return '—'
  return text.length > limit ? `${text.slice(0, limit - 1)}…` : text
}
