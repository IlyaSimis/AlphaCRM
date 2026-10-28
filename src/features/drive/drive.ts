import type { DriveNode, DrivePermission } from '../../api/types'

export const permissionLabels: Record<DrivePermission, string> = { VIEW: 'Просмотр', EDIT: 'Редактирование', MANAGE: 'Управление' }

export const principalLabels: Record<string, string> = { USER: 'Сотрудник', ROLE: 'Роль', CLINIC: 'Вся клиника', LINK: 'Ссылка' }

const RANK: DrivePermission[] = ['VIEW', 'EDIT', 'MANAGE']

/** Право не ниже нужного: EDIT включает VIEW, MANAGE — всё. */
export function allows(node: Pick<DriveNode, 'permission'>, needed: DrivePermission) {
  return node.permission != null && RANK.indexOf(node.permission) >= RANK.indexOf(needed)
}

/** Токен из вставленной ссылки (…?drive-link=…) или сам токен. */
export function tokenFrom(input: string) {
  const text = input.trim()
  const match = text.match(/[?&]drive-link=([^&#]+)/)
  return match ? decodeURIComponent(match[1]) : text
}

/** Дата окончания ссылки: через days дней, конец дня по UTC. */
export function expiryIn(days: number, now = new Date()) {
  const end = new Date(now.getTime() + days * 86_400_000)
  end.setUTCHours(23, 59, 0, 0)
  return end.toISOString()
}
