import type { PatientFileItem } from '../../api/types'
import type { Role } from '../../shared/roles'

export const fileStateLabels: Record<PatientFileItem['state'], string> = { DRAFT: 'Черновик', SIGNED_LOCKED: 'Подписан' }

/** Текст перед подписью — что именно фиксирует подпись. */
export const SIGN_NOTICE = 'Подпись фиксирует, кто и когда подтвердил файл. Содержимое после этого не меняется.'

/**
 * Права на PDF пациента — как на сервере. Кассир блока не видит. Врачу сервер дополнительно
 * проверяет связь с лечением; менеджеру отдаются только подписанные файлы.
 */
export function fileRights(role: Role) {
  const editor = role === 'ADMINISTRATOR' || role === 'DOCTOR'
  return {
    visible: role !== 'CASHIER',
    upload: editor,
    sign: (file: PatientFileItem) => editor && file.state === 'DRAFT',
    download: editor || role === 'MANAGER',
  }
}
