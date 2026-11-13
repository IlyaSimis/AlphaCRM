import type { ConsentState } from '../../api/types'

/** Надпись о согласии пациента на экране по его состоянию. */
export const consentLabels: Record<ConsentState, string> = { ACTIVE: 'Действует', NEEDS_UPDATE: 'Требует обновления' }

export const consentBases = [
  { value: 'SIGNED_FORM', label: 'Подписанная форма' },
  { value: 'ELECTRONIC_SIGNATURE', label: 'Электронная подпись' },
]
