import type { ItemKind, MarkingSystem, StockBalance, StockDocument, StockDocumentType } from '../../api/types'

export const kindLabels: Record<ItemKind, string> = {
  DRUG: 'Лекарства',
  MEDICAL_DEVICE: 'Медизделия',
  CONSUMABLE: 'Расходники',
  GOODS: 'Товары',
}

export const markingLabels: Record<MarkingSystem, string> = {
  NONE: 'Не маркируется',
  MDLP: 'МДЛП (лекарства)',
  GIS_MT: 'ГИС МТ (товары)',
}

export const documentTypeLabels: Record<StockDocumentType, string> = {
  RECEIPT: 'Приход',
  CONSUMPTION: 'Расход',
  WRITE_OFF: 'Списание',
  TRANSFER: 'Перемещение',
  RETURN_TO_SUPPLIER: 'Возврат поставщику',
}

export const documentStatusLabels: Record<string, string> = {
  DRAFT: 'Черновик',
  POSTED: 'Проведена',
  CANCELLED: 'Отменена',
}

export const codeStatusLabels: Record<string, string> = {
  PENDING_ACCEPTANCE: 'Ждёт приёмки',
  IN_STOCK: 'В наличии',
  RESERVED: 'В резерве',
  WITHDRAWAL_PENDING: 'Выбывает',
  WITHDRAWN: 'Выведена из оборота',
  WRITE_OFF_PENDING: 'Списывается',
  WRITTEN_OFF: 'Списана',
  RETURN_PENDING: 'Возвращается',
  RETURNED: 'Возвращена',
  ERROR: 'Ошибка',
}

export const operationLabels: Record<string, string> = {
  ACCEPTANCE: 'Приёмка',
  WITHDRAW_MEDICAL_USE: 'Вывод для медицинского применения',
  WRITE_OFF: 'Списание',
  RETURN_TO_SUPPLIER: 'Возврат поставщику',
}

export const operationStatusLabels: Record<string, string> = { PENDING: 'В очереди', DONE: 'Принята', ERROR: 'Ошибка' }

export const operationErrorLabels: Record<string, string> = {
  MARKING_NOT_CONFIGURED: 'Интеграция с Честным знаком не настроена',
  UNKNOWN_OPERATION: 'Неизвестная операция',
}

export const adapterLabels: Record<string, string> = {
  NONE: 'Не настроена — операции ждут в очереди',
  MOCK: 'Заглушка (ничего не отправляется)',
  MDLP: 'МДЛП',
  GIS_MT: 'ГИС МТ',
}

/** Какие склады нужны документу: приход — куда, расход и списание — откуда, перемещение — оба. */
export function warehousesOf(type: StockDocumentType) {
  return { from: type !== 'RECEIPT', to: type === 'RECEIPT' || type === 'TRANSFER' }
}

/** Количество из поля ввода: положительное, до трёх знаков после запятой. */
export function parseQuantity(value: string): number | null {
  const normalized = value.trim().replace(',', '.')
  if (!/^\d+(\.\d{1,3})?$/.test(normalized)) return null
  const number = Number(normalized)
  return number > 0 ? number : null
}

/** Количество для экрана: 2, 0,5, 1,25. */
export function formatQuantity(value: number) {
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(3))).replace('.', ',')
}

/** Остаток требует внимания: в минусе или срок годности в ближайшие 30 дней. */
export function balanceAlert(row: StockBalance, today: string): 'negative' | 'expiring' | null {
  if (row.quantity < 0) return 'negative'
  if (row.expiresOn && row.quantity > 0) {
    const limit = new Date(`${today}T00:00:00Z`)
    limit.setUTCDate(limit.getUTCDate() + 30)
    if (row.expiresOn <= limit.toISOString().slice(0, 10)) return 'expiring'
  }
  return null
}

/** Сообщение о нехватке после проведения расхода сверх остатка; без нехватки — null. */
export function shortageText(document: Pick<StockDocument, 'shortages'>) {
  if (!document.shortages || document.shortages.length === 0) return null
  return `Проведено с нехваткой: ${document.shortages.map((item) => `${item.itemName} — ${formatQuantity(item.missing)}`).join(', ')}. Регистратура получит задачу.`
}
