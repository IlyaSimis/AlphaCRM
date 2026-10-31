import type { DashboardWidget } from '../../api/types'
import type { Page } from '../../app/pages'
import { formatMoney } from '../schedule/booking'

/** Байты для подписи квоты: 1,5 ГБ, 230 МБ. */
export function formatBytes(bytes: number) {
  const units = ['Б', 'КБ', 'МБ', 'ГБ', 'ТБ']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  const digits = value >= 10 || unit === 0 ? 0 : 1
  return `${value.toFixed(digits).replace('.', ',')} ${units[unit]}`
}

/**
 * Подпись виджета по коду. Сервер отдаёт только код и числа — текст на экране выбирает интерфейс.
 * Неизвестный код (более новый сервер) не показывается.
 */
export function widgetText(widget: DashboardWidget): { label: string; note: string; accent: boolean } | null {
  const own = widget.scope === 'OWN'
  const second = widget.secondary ?? 0
  switch (widget.code) {
    case 'appointments.month':
      return { label: own ? 'мои приёмы за месяц' : 'приёмы за месяц', note: `завершено ${second}`, accent: false }
    case 'salary.month':
      return { label: 'зарплата за месяц', note: 'по действующим условиям', accent: false }
    case 'appointments.month.noShow':
      return { label: 'неявок за месяц', note: `отмен ${second}`, accent: widget.value > 0 }
    case 'appointments.today':
      return { label: own ? 'моих приёмов сегодня' : 'приёмов сегодня', note: 'активные записи', accent: false }
    case 'documents.unsigned':
      return { label: 'неподписанных документов', note: 'черновики приёмов и PDF', accent: widget.value > 0 }
    case 'charges.unpaid':
      return { label: 'счетов к оплате', note: `на ${formatMoney(second)}`, accent: widget.value > 0 }
    case 'charges.awaitingAmount':
      return { label: 'счетов ждут суммы', note: 'свободная цена', accent: widget.value > 0 }
    case 'clinical.break-glass':
      return { label: own ? 'экстренных доступов к моим пациентам' : 'экстренных доступов к медданным', note: 'за 7 дней', accent: widget.value > 0 }
    case 'revenue.month':
      return { label: 'выручка за месяц', note: `оплат ${second}, за вычетом возвратов`, accent: false }
    case 'tasks.due':
      return { label: own ? 'моих задач на сегодня' : 'задач на сегодня', note: 'открытые, срок наступил', accent: widget.value > 0 }
    case 'workspace.due':
      return { label: 'личных записей на сегодня', note: `просрочено ${second}`, accent: second > 0 }
    case 'stock.shortage':
      return { label: 'позиций в минусе', note: 'нехватка на складе', accent: widget.value > 0 }
    case 'stock.expiring':
      return { label: 'партий истекает', note: 'в ближайшие 30 дней', accent: widget.value > 0 }
    case 'marking.queue':
      return { label: 'операций маркировки ждут', note: `ошибок ${second}`, accent: second > 0 }
    case 'delivery.failed':
      return { label: 'ошибок доставки', note: `в очереди ${second}`, accent: widget.value > 0 }
    case 'events.backlog':
      return { label: 'событий с повтором', note: `не доставлено ${second}`, accent: second > 0 }
    case 'drive.quota':
      return { label: 'занято на диске', note: widget.secondary == null ? 'квота не задана' : `из ${formatBytes(widget.secondary)}`, accent: false }
    default:
      return null
  }
}

/** Раздел, в котором пользователь может увидеть данные за числом на плашке. */
export function dashboardDestination(code: string): Page | null {
  switch (code) {
    case 'appointments.month':
    case 'appointments.month.noShow':
    case 'appointments.today': return 'schedule'
    case 'salary.month': return 'hr-me'
    case 'documents.unsigned': return 'documents'
    case 'charges.unpaid':
    case 'charges.awaitingAmount': return 'cashier'
    case 'clinical.break-glass': return 'break-glass'
    case 'revenue.month': return 'sales'
    case 'tasks.due': return 'registry'
    case 'workspace.due': return 'workspace'
    case 'stock.shortage':
    case 'stock.expiring': return 'stock-balances'
    case 'marking.queue': return 'stock-marking'
    case 'delivery.failed':
    case 'events.backlog': return 'outbox'
    case 'drive.quota': return 'drive'
    default: return null
  }
}
