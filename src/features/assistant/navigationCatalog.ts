import { navigationFor, pageTitles, type Page } from '../../app/pages'
import type { WorkspaceDueFilter } from '../../api/types'
import type { Role } from '../../shared/roles'

export type WorkspaceNavigationIntent = { due?: WorkspaceDueFilter; type?: string }
export type NavigationDestination = {
  id: string
  page: Page
  title: string
  description: string
  keywords: string[]
  workspaceIntent?: WorkspaceNavigationIntent
}

const details: Record<Page, { description: string; keywords: string[] }> = {
  today: { description: 'Рабочий день, персональные показатели и ближайшие дела', keywords: ['главная', 'день', 'дашборд'] },
  workspace: { description: 'Личные задачи, заметки, произвольные записи и сохранённые представления', keywords: ['мои задачи', 'заметки', 'дела', 'пространство', 'создать задачу'] },
  analytics: { description: 'Аналитика посещаемости и показатели работы клиники', keywords: ['статистика', 'отчёты', 'показатели'] },
  'hr-me': { description: 'Свои условия работы, должность и ставка', keywords: ['моя ставка', 'мой договор', 'условия'] },
  staff: { description: 'Учётные записи сотрудников, роли и доступ', keywords: ['пользователи', 'добавить сотрудника', 'права'] },
  hr: { description: 'Ставки, должности и условия работы сотрудников', keywords: ['договоры', 'зарплата', 'ставки'] },
  audit: { description: 'Журнал действий и событий безопасности', keywords: ['история действий', 'лог', 'аудит'] },
  'break-glass': { description: 'Журнал экстренного доступа к клиническим записям', keywords: ['экстренный доступ', 'break glass'] },
  outbox: { description: 'Очередь внешних сообщений, чеков и ошибок доставки', keywords: ['доставка', 'сообщения', 'очередь'] },
  integrations: { description: 'Настройка Mango, Telegram и MAX для клиники', keywords: ['боты', 'телефония', 'токены', 'webhook', 'манго', 'телеграм'] },
  schedule: { description: 'Расписание врачей, записи пациентов и свободные интервалы', keywords: ['календарь', 'записи', 'приёмы', 'записать'] },
  patients: { description: 'Поиск и список пациентов клиники', keywords: ['картотека', 'база пациентов', 'найти пациента'] },
  patient: { description: 'Карточка выбранного пациента', keywords: ['карта пациента'] },
  visit: { description: 'Текущий клинический приём', keywords: ['ведение приёма'] },
  registry: { description: 'Административные задачи регистратуры и лист ожидания', keywords: ['администратор', 'ожидание', 'регистратура'] },
  documents: { description: 'Архив подписанных медицинских документов', keywords: ['заключения', 'подписи', 'архив'] },
  'contract-templates': { description: 'DOCX-шаблоны договоров, их версии и переменные для подстановки', keywords: ['договоры', 'шаблоны', 'загрузить договор', 'конструктор договоров'] },
  hours: { description: 'Рабочие графики и смены врачей', keywords: ['смены', 'график работы', 'часы'] },
  'stock-balances': { description: 'Остатки товаров, лекарств и расходников на складах', keywords: ['склад', 'остатки', 'партии'] },
  'stock-items': { description: 'Номенклатура и карточки складских позиций', keywords: ['товары', 'лекарства', 'расходники'] },
  'stock-documents': { description: 'Приход, расход, перемещение и списание со склада', keywords: ['накладные', 'приход', 'списание'] },
  'stock-marking': { description: 'Коды маркировки и обмен с маркировочными системами', keywords: ['честный знак', 'марки', 'коды'] },
  'stock-norms': { description: 'Нормы расхода материалов на услуги', keywords: ['расход материалов', 'нормы'] },
  cashier: { description: 'Счета пациентов и приём оплаты за услуги', keywords: ['оплата пациентом', 'счета', 'деньги', 'чек'] },
  sales: { description: 'Выручка, продажи и возвраты по периодам', keywords: ['доход', 'финансовый отчёт', 'выручка'] },
  prices: { description: 'Справочник услуг, длительность и цены', keywords: ['прайс', 'стоимость', 'услуги'] },
  drive: { description: 'Личные файлы и папки на корпоративном диске', keywords: ['файлы', 'документы', 'мой диск'] },
  'drive-shared': { description: 'Файлы и папки, к которым открыт доступ', keywords: ['общие файлы', 'доступные'] },
  'drive-trash': { description: 'Удалённые файлы и восстановление', keywords: ['удалённые', 'восстановить файл'] },
}

/** Каталог содержит только страницы роли; динамические карточки и приёмы сюда не попадают. */
export function navigationCatalog(role: Role): NavigationDestination[] {
  const base: NavigationDestination[] = navigationFor(role).map((page) => ({
    id: page,
    page,
    title: pageTitles[page],
    description: details[page].description,
    keywords: details[page].keywords,
  }))
  if (!base.some((item) => item.page === 'workspace')) return base
  const index = base.findIndex((item) => item.page === 'workspace') + 1
  base.splice(index, 0,
    { id: 'workspace-overdue', page: 'workspace', title: 'Просроченные задачи', description: 'Дашборды с фильтром просроченных записей', keywords: ['просроченные', 'долги', 'срок прошёл', 'задачи'], workspaceIntent: { due: 'overdue' } },
    { id: 'workspace-today', page: 'workspace', title: 'Задачи на сегодня', description: 'Личные задачи и записи со сроком сегодня', keywords: ['на сегодня', 'сегодняшние задачи', 'план дня'], workspaceIntent: { due: 'today' } },
    { id: 'workspace-notes', page: 'workspace', title: 'Мои заметки', description: 'Личные записи типа «Заметка»', keywords: ['заметки', 'мои записи', 'тексты'], workspaceIntent: { type: 'Заметка' } },
  )
  return base
}

const normalize = (value: string) => value.toLocaleLowerCase('ru-RU').replaceAll('ё', 'е').replace(/[^a-zа-я0-9]+/gi, ' ').trim()

/** Дешёвый локальный роутинг: точные совпадения не расходуют токены внешней модели. */
export function searchNavigation(query: string, role: Role): Array<NavigationDestination & { score: number }> {
  const text = normalize(query)
  if (!text) return []
  const tokens = text.split(' ').filter((token) => token.length > 1)
  return navigationCatalog(role).map((destination) => {
    const title = normalize(destination.title)
    const keywords = destination.keywords.map(normalize)
    const description = normalize(destination.description)
    let score = title === text ? 120 : title.includes(text) || text.includes(title) ? 85 : 0
    let matched = 0
    for (const token of tokens) {
      const tokenScore = title.split(' ').some((part) => part.startsWith(token)) ? 35
        : title.includes(token) ? 25
        : keywords.some((keyword) => keyword.includes(token)) ? 20
        : description.includes(token) ? 10 : 0
      if (tokenScore) matched += 1
      score += tokenScore
    }
    score += tokens.length ? Math.round(40 * matched / tokens.length) : 0
    return { ...destination, score }
  }).filter((destination) => destination.score >= 30).sort((left, right) => right.score - left.score || left.title.localeCompare(right.title, 'ru-RU')).slice(0, 6)
}
