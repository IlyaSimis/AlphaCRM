import type { DocumentSummary, EncounterView, NoteFields } from '../../api/types'

/** Что открыто на экране приёма: строка расписания или приём из истории пациента. */
export type VisitTarget = {
  patientId: string
  patientName: string
  practitionerId: string
  appointmentId?: string
  date?: string
  time?: string
  minutes?: number
  serviceName?: string
  encounter?: EncounterView
}

export const noteSections: Array<{ key: keyof NoteFields; number: string; title: string; rows: number }> = [
  { key: 'complaints', number: '01', title: 'Жалобы и анамнез', rows: 3 },
  { key: 'mentalStatus', number: '02', title: 'Психический статус', rows: 3 },
  { key: 'diagnosis', number: '03', title: 'Диагноз', rows: 2 },
  { key: 'recommendations', number: '04', title: 'Назначения и рекомендации', rows: 3 },
]

export const emptyNote: NoteFields = { complaints: '', mentalStatus: '', diagnosis: '', recommendations: '' }

export function noteOf(encounter?: EncounterView | null): NoteFields {
  return {
    complaints: encounter?.complaints ?? '',
    mentalStatus: encounter?.mentalStatus ?? '',
    diagnosis: encounter?.diagnosis ?? '',
    recommendations: encounter?.recommendations ?? '',
  }
}

/** Сервер принимает заметку только со всеми четырьмя заполненными полями. */
export function noteComplete(note: NoteFields) {
  return noteSections.every((section) => note[section.key].trim().length > 0)
}

/** Текущая версия: открытый черновик, если он есть, иначе последняя версия. */
export function activeDocument(documents: DocumentSummary[]) {
  return documents.findLast((doc) => doc.state === 'DRAFT') ?? documents.at(-1)
}

export function latestSigned(documents: DocumentSummary[]) {
  return documents.findLast((doc) => doc.state === 'SIGNED_LOCKED')
}

/** Форма заблокирована, когда последняя версия подписана и нового черновика нет. */
export function isLocked(documents: DocumentSummary[]) {
  return activeDocument(documents)?.state === 'SIGNED_LOCKED'
}

export function upsertDocument(documents: DocumentSummary[], doc: DocumentSummary) {
  return documents.some((item) => item.id === doc.id) ? documents.map((item) => (item.id === doc.id ? doc : item)) : [...documents, doc]
}

export function visitTimeLabel(target: VisitTarget) {
  if (!target.time) return ''
  if (!target.minutes) return target.time
  const [hours, minutes] = target.time.split(':').map(Number)
  const end = hours * 60 + minutes + target.minutes
  return `${target.time}–${String(Math.floor(end / 60)).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`
}
