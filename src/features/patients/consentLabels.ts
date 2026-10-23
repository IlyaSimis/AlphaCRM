import type { ConsentState } from '../../api/types'

/** Надпись о согласии пациента на экране по его состоянию. */
export const consentLabels: Record<ConsentState, string> = { ACTIVE: 'Действует', NEEDS_UPDATE: 'Требует обновления' }
