import type { PatientFileItem } from '../../api/types'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'

export const fileStateLabels: Record<PatientFileItem['state'], string> = { DRAFT: 'Черновик', SIGNED_LOCKED: 'Подписан' }

/** Текст перед подписью — что именно фиксирует подпись. */
export const SIGN_NOTICE = 'Подпись фиксирует, кто и когда подтвердил файл. Содержимое после этого не меняется.'

/**
 * Права на PDF пациента — как на сервере (clinical.patient-file.*). Кассир, медперсонал и
 * техподдержка блока не видят. Врачу сервер дополнительно проверяет связь с лечением; менеджеру
 * отдаются только подписанные файлы.
 */
export function fileRights(role: Role) {
  const editor = can(role, 'clinical.patient-file.write', 'clinical.patient-file.write-care')
  const reader = can(role, 'clinical.patient-file.read', 'clinical.patient-file.read-signed')
  return {
    visible: editor || reader,
    upload: editor,
    sign: (file: PatientFileItem) => editor && file.state === 'DRAFT',
    download: editor || reader,
    /** Только подписанные (менеджер). */
    signedOnly: !editor && can(role, 'clinical.patient-file.read-signed') && !can(role, 'clinical.patient-file.read'),
  }
}
