import { useEffect, useMemo, useState } from 'react'
import { AddRounded, ArchiveRounded, CheckCircleOutlineRounded, NotificationsActiveRounded, NoteAddRounded, RadioButtonUncheckedRounded, RestoreRounded, ViewKanbanRounded, ViewListRounded } from '@mui/icons-material'
import { Alert, Box, Button, Checkbox, Chip, IconButton, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import { archiveWorkspaceItem, createWorkspaceItem, loadWorkspaceAssignees, loadWorkspaceItems, updateWorkspaceItem } from '../../api/workspace'
import type { PatientItem, ScheduleItem, WorkspaceAssignee, WorkspaceFilter, WorkspaceItem, WorkspaceItemInput } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import type { WorkspaceNavigationIntent } from '../assistant/navigationCatalog'
import { WorkspaceItemDialog } from './WorkspaceItemDialog'
import { blankWorkspaceItem, emptyWorkspaceFilter, filterWorkspace, inputOf, isWorkspaceOverdue, isWorkspaceReminderDue, newWorkspaceId, offlineStore, syncOfflineItem } from './workspace'

type WorkspacePageProps = {
  apiOnline: boolean
  selfId: string
  staff?: WorkspaceAssignee[]
  patients: PatientItem[]
  appointments: ScheduleItem[]
  notify: (message: string) => void
  navigationIntent?: ({ key: number } & WorkspaceNavigationIntent) | null
}

function normalizeItem(item: WorkspaceItem, selfId: string): WorkspaceItem {
  const tags = Array.isArray(item.tags)
    ? [...new Set(item.tags.filter((tag): tag is string => typeof tag === 'string').map((tag) => tag.trim()).filter(Boolean))]
    : []
  return {
    ...item,
    creatorId: item.creatorId ?? selfId,
    assigneeId: item.assigneeId ?? selfId,
    tags,
    fields: Array.isArray(item.fields) ? item.fields : [],
    checklist: Array.isArray(item.checklist) ? item.checklist.filter((entry) => entry && typeof entry.text === 'string') : [],
  }
}

/** Личные записи и общие задачи: серверная версия для работы и локальная демо-песочница. */
export function WorkspacePage({ apiOnline, selfId, staff = [], patients, appointments, notify, navigationIntent }: WorkspacePageProps) {
  const initial = useMemo(() => apiOnline ? [] : offlineStore(selfId).items.map((item) => normalizeItem(item, selfId)), [apiOnline, selfId])
  const [items, setItems] = useState<WorkspaceItem[]>(initial)
  const [remoteStaff, setRemoteStaff] = useState<WorkspaceAssignee[]>([])
  const [filter, setFilter] = useState<WorkspaceFilter>(emptyWorkspaceFilter)
  const [archived, setArchived] = useState(false)
  const [editor, setEditor] = useState<{ item: WorkspaceItem | null; preset?: string } | null>(null)
  const [quickTitle, setQuickTitle] = useState('')
  const [loading, setLoading] = useState(apiOnline)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    setLoading(true)
    loadWorkspaceItems(archived)
      .then((nextItems) => { if (!cancelled) { setItems(nextItems.map((item) => normalizeItem(item, selfId))); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
      .finally(() => { if (!cancelled) setLoading(false) })
    loadWorkspaceAssignees()
      .then((people) => { if (!cancelled) setRemoteStaff(people) })
      .catch(() => { /* Оставим уже известных сотрудников и текущего пользователя. */ })
    return () => { cancelled = true }
  }, [apiOnline, archived, revision, selfId])

  const availableStaff = useMemo(() => {
    const people = new Map<string, WorkspaceAssignee>()
    people.set(selfId, { id: selfId, fullName: staff.find((person) => person.id === selfId)?.fullName ?? 'Вы', role: staff.find((person) => person.id === selfId)?.role ?? 'MANAGER' })
    for (const person of [...staff, ...remoteStaff]) if (person.active !== false) people.set(person.id, person)
    return [...people.values()].sort((left, right) => left.fullName.localeCompare(right.fullName, 'ru-RU'))
  }, [selfId, staff, remoteStaff])
  const selfName = availableStaff.find((person) => person.id === selfId)?.fullName ?? 'Вы'
  const staffName = (id: string | null | undefined) => availableStaff.find((person) => person.id === id)?.fullName ?? 'Сотрудник'

  useEffect(() => {
    if (apiOnline) return
    try { globalThis.localStorage?.setItem(`alphacrm.workspace.${selfId}`, JSON.stringify({ items, views: [] })) } catch { /* Нет места — остаётся текущая сессия. */ }
  }, [apiOnline, selfId, items])

  useEffect(() => {
    if (!navigationIntent) return
    setArchived(false)
    setFilter({
      ...emptyWorkspaceFilter,
      due: navigationIntent.due ?? 'all',
      type: navigationIntent.type ?? '',
    })
  }, [navigationIntent])

  const patchFilter = <K extends keyof WorkspaceFilter>(key: K, value: WorkspaceFilter[K]) => setFilter((current) => ({ ...current, [key]: value }))
  const visible = useMemo(() => filterWorkspace(apiOnline ? items : items.filter((item) => archived ? item.archivedAt : !item.archivedAt), filter), [apiOnline, items, archived, filter])
  const types = useMemo(() => [...new Set(items.map((item) => item.type))].sort((a, b) => a.localeCompare(b, 'ru-RU')), [items])
  const statuses = useMemo(() => [...new Set(visible.map((item) => item.status))].sort((a, b) => a.localeCompare(b, 'ru-RU')), [visible])
  const tags = useMemo(() => [...new Set(items.flatMap((item) => item.tags))].sort((a, b) => a.localeCompare(b, 'ru-RU')), [items])
  const patientName = (id: string | null) => id ? patients.find((patient) => patient.id === id)?.name : null

  const refresh = () => setRevision((value) => value + 1)
  const save = async (input: WorkspaceItemInput) => {
    const previous = editor?.item
    if (apiOnline) {
      const saved = normalizeItem(previous
        ? await updateWorkspaceItem(previous.id, input)
        : await createWorkspaceItem(input), selfId)
      // Ответ сервера показываем сразу: новые теги доступны ещё до завершения фонового обновления.
      setItems((current) => previous
        ? current.map((item) => item.id === saved.id ? saved : item)
        : [saved, ...current])
      refresh()
    } else {
      const now = new Date().toISOString()
      const saved = normalizeItem({
        ...previous,
        ...input,
        id: previous?.id ?? newWorkspaceId(),
        creatorId: previous?.creatorId ?? selfId,
        createdAt: previous?.createdAt ?? now,
        updatedAt: now,
        completedAt: input.completed ? previous?.completedAt ?? now : null,
        archivedAt: previous?.archivedAt ?? null,
      }, selfId)
      syncOfflineItem(saved, previous ?? undefined)
      setItems((current) => previous ? current.map((item) => item.id === saved.id ? saved : item) : [saved, ...current])
    }
    notify(previous ? 'Запись сохранена' : input.assigneeId && input.assigneeId !== selfId ? `Задача назначена: ${staffName(input.assigneeId)}` : 'Запись создана')
    setEditor(null)
  }

  const toggle = async (item: WorkspaceItem) => {
    try {
      const input = { ...inputOf(item), completed: !item.completed }
      if (apiOnline) {
        const saved = normalizeItem(await updateWorkspaceItem(item.id, input), selfId)
        setItems((current) => current.map((entry) => entry.id === saved.id ? saved : entry))
      } else {
        const now = new Date().toISOString()
        const saved = { ...item, completed: input.completed, completedAt: input.completed ? now : null, updatedAt: now }
        syncOfflineItem(saved)
        setItems((current) => current.map((entry) => entry.id === item.id ? saved : entry))
      }
    } catch (reason) { setError((reason as Error).message) }
  }

  const toggleChecklist = async (item: WorkspaceItem, index: number, done: boolean) => {
    try {
      const checklist = item.checklist.map((entry, at) => at === index ? { ...entry, done } : entry)
      const input = { ...inputOf(item), checklist }
      if (apiOnline) {
        const saved = normalizeItem(await updateWorkspaceItem(item.id, input), selfId)
        setItems((current) => current.map((entry) => entry.id === saved.id ? saved : entry))
      } else {
        const saved = { ...item, checklist, updatedAt: new Date().toISOString() }
        syncOfflineItem(saved)
        setItems((current) => current.map((entry) => entry.id === item.id ? saved : entry))
      }
    } catch (reason) { setError((reason as Error).message) }
  }

  const setArchive = async (item: WorkspaceItem, value: boolean) => {
    try {
      if (apiOnline) await archiveWorkspaceItem(item.id, value)
      else {
        const saved = { ...item, archivedAt: value ? new Date().toISOString() : null }
        syncOfflineItem(saved)
        setItems((current) => current.map((entry) => entry.id === item.id ? saved : entry))
      }
      notify(value ? 'Запись в архиве' : 'Запись восстановлена')
      if (apiOnline) refresh()
    } catch (reason) { setError((reason as Error).message) }
  }

  const quickCreate = async () => {
    const title = quickTitle.trim()
    if (!title) return
    try {
      const input = { ...blankWorkspaceItem('Задача', selfId), title }
      if (apiOnline) {
        const saved = normalizeItem(await createWorkspaceItem(input), selfId)
        setItems((current) => [saved, ...current])
        refresh()
      } else {
        const now = new Date().toISOString()
        setItems((current) => [{ ...input, id: newWorkspaceId(), creatorId: selfId, createdAt: now, updatedAt: now, completedAt: null, archivedAt: null }, ...current])
      }
      setQuickTitle('')
    } catch (reason) { setError((reason as Error).message) }
  }

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Дашборды</Typography><Typography sx={{ color: 'text.secondary' }}>Задачи, заметки и записи в вашей системе — без обязательных сценариев и полей.</Typography></Box>
        {!archived && <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}><Button variant="outlined" startIcon={<NoteAddRounded />} onClick={() => setEditor({ item: null, preset: 'Заметка' })}>Заметка</Button><Button variant="contained" startIcon={<AddRounded />} onClick={() => setEditor({ item: null })}>Создать</Button></Stack>}
      </Box>
      <Alert severity="info" sx={{ mb: 2 }}>Заметки остаются личными, а задачу можно назначить сотруднику. Это не медицинская документация: клинические записи хранятся в защищённой карточке пациента.</Alert>
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}

      {!archived && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 2 }}>
        <TextField fullWidth size="small" label="Быстрая задача" placeholder="Название — остальное добавите позже" value={quickTitle} onChange={(event) => setQuickTitle(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void quickCreate() } }} />
        <Button variant="outlined" startIcon={<AddRounded />} onClick={() => void quickCreate()} disabled={!quickTitle.trim()}>Добавить</Button>
      </Stack>}

      <Box className="panel workspace-filters" sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: '2fr repeat(5, minmax(130px, 1fr))' }, gap: 1.5 }}>
          <TextField size="small" label="Поиск по всему содержимому" value={filter.query} onChange={(event) => patchFilter('query', event.target.value)} />
          <TextField select size="small" label="Тип" value={filter.type} onChange={(event) => patchFilter('type', event.target.value)}><MenuItem value="">Все типы</MenuItem>{types.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}</TextField>
          <TextField select size="small" label="Тег" value={filter.tag} onChange={(event) => patchFilter('tag', event.target.value)}><MenuItem value="">Все теги</MenuItem>{tags.map((tag) => <MenuItem key={tag} value={tag}>{tag}</MenuItem>)}</TextField>
          <TextField select size="small" label="Исполнитель" value={filter.assigneeId ?? ''} onChange={(event) => patchFilter('assigneeId', event.target.value || null)}><MenuItem value="">Все записи</MenuItem><MenuItem value={selfId}>Назначенные мне</MenuItem></TextField>
          <TextField select size="small" label="Срок" value={filter.due} onChange={(event) => patchFilter('due', event.target.value as WorkspaceFilter['due'])}><MenuItem value="all">Любой</MenuItem><MenuItem value="today">Сегодня</MenuItem><MenuItem value="overdue">Просрочено</MenuItem><MenuItem value="week">Ближайшие 7 дней</MenuItem><MenuItem value="none">Без срока</MenuItem></TextField>
          <TextField select size="small" label="Сортировка" value={filter.sort} onChange={(event) => patchFilter('sort', event.target.value as WorkspaceFilter['sort'])}><MenuItem value="due">По сроку</MenuItem><MenuItem value="updated">По изменению</MenuItem><MenuItem value="created">По созданию</MenuItem><MenuItem value="title">По названию</MenuItem></TextField>
        </Box>
        <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, alignItems: 'center', flexWrap: 'wrap' }}>
          <ToggleButtonGroup size="small" exclusive value={filter.display} onChange={(_, value) => { if (value) patchFilter('display', value) }} aria-label="Вид списка"><ToggleButton value="list" aria-label="Список"><ViewListRounded /></ToggleButton><ToggleButton value="board" aria-label="Доска"><ViewKanbanRounded /></ToggleButton></ToggleButtonGroup>
          <Box sx={{ flex: 1 }} />
          <Button size="small" startIcon={archived ? <RestoreRounded /> : <ArchiveRounded />} onClick={() => { setArchived((value) => !value); setFilter(emptyWorkspaceFilter) }}>{archived ? 'К активным' : 'Архив'}</Button>
        </Stack>
      </Box>

      {loading ? <Typography sx={{ color: 'text.secondary' }}>Загрузка…</Typography> : visible.length === 0 ? <Box className="panel" sx={{ p: 4, textAlign: 'center' }}><Typography variant="h6">{archived ? 'Архив пуст' : 'Здесь пока ничего нет'}</Typography><Typography sx={{ color: 'text.secondary', mt: 1 }}>{archived ? 'Архивированные записи можно восстановить.' : 'Создайте запись или измените фильтры — структура полностью ваша.'}</Typography></Box> : filter.display === 'list' ? (
        <Stack component="section" aria-label="Список задач" spacing={1.2}>{visible.map((item) => <WorkspaceCard key={item.id} item={item} patientName={patientName(item.patientId)} assigneeName={staffName(item.assigneeId)} creatorName={staffName(item.creatorId)} selfId={selfId} archived={archived} onOpen={() => setEditor({ item })} onToggle={() => void toggle(item)} onToggleChecklist={(index, done) => void toggleChecklist(item, index, done)} onArchive={() => void setArchive(item, !archived)} />)}</Stack>
      ) : (
        <Box component="section" aria-label="Доска задач" className="workspace-board">{statuses.map((status) => <Box component="section" className="workspace-board-column" key={status}><Typography className="panel-label" sx={{ p: 1.5 }}>{status} · {visible.filter((item) => item.status === status).length}</Typography><Stack spacing={1}>{visible.filter((item) => item.status === status).map((item) => <WorkspaceCard key={item.id} item={item} patientName={patientName(item.patientId)} assigneeName={staffName(item.assigneeId)} creatorName={staffName(item.creatorId)} selfId={selfId} archived={archived} compact onOpen={() => setEditor({ item })} onToggle={() => void toggle(item)} onToggleChecklist={(index, done) => void toggleChecklist(item, index, done)} onArchive={() => void setArchive(item, !archived)} />)}</Stack></Box>)}</Box>
      )}

      {editor && <WorkspaceItemDialog item={editor.item} preset={editor.preset} selfId={selfId} selfName={selfName} staff={availableStaff} patients={patients} appointments={appointments} onClose={() => setEditor(null)} onSave={save} />}
    </>
  )
}

type CardProps = { item: WorkspaceItem; patientName?: string | null; assigneeName: string; creatorName: string; selfId: string; archived: boolean; compact?: boolean; onOpen: () => void; onToggle: () => void; onToggleChecklist: (index: number, done: boolean) => void; onArchive: () => void }
function WorkspaceCard({ item, patientName, assigneeName, creatorName, selfId, archived, compact, onOpen, onToggle, onToggleChecklist, onArchive }: CardProps) {
  const checklistDone = item.checklist.filter((entry) => entry.done).length
  return (
    <Box className={`panel workspace-card workspace-card--${item.color} ${item.completed ? 'completed' : ''}`} sx={{ p: compact ? 1.2 : 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
        {!archived && <Tooltip title={item.completed ? 'Вернуть в работу' : 'Отметить выполненным'}><IconButton size="small" aria-label={item.completed ? `Вернуть: ${item.title}` : `Выполнить: ${item.title}`} onClick={onToggle}>{item.completed ? <CheckCircleOutlineRounded color="primary" /> : <RadioButtonUncheckedRounded />}</IconButton></Tooltip>}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Button variant="text" onClick={onOpen} sx={{ p: 0, minWidth: 0, textAlign: 'left', textTransform: 'none', color: 'text.primary', fontWeight: 700, textDecoration: item.completed ? 'line-through' : 'none' }}>{item.title}</Button>
          {item.body && !compact && <Typography sx={{ color: 'text.secondary', fontSize: 14, mt: 0.5, whiteSpace: 'pre-wrap', overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>{item.body}</Typography>}
          {item.checklist.length > 0 && <Stack component="ul" aria-label={`Пункты: ${item.title}`} spacing={0.35} sx={{ mt: 1, mb: 0, pl: 0, listStyle: 'none' }}>
            {item.checklist.map((entry, index) => <Stack component="li" direction="row" spacing={0.6} key={`${entry.text}-${index}`} sx={{ alignItems: 'center' }}>
              <Checkbox size="small" checked={entry.done} disabled={archived} onChange={(event) => onToggleChecklist(index, event.target.checked)} slotProps={{ input: { 'aria-label': `Подпункт: ${entry.text || `Пункт ${index + 1}`}` } }} sx={{ p: 0.25 }} />
              <Typography sx={{ fontSize: 13, color: entry.done ? 'text.secondary' : 'text.primary', textDecoration: entry.done ? 'line-through' : 'none' }}>{entry.text || `Пункт ${index + 1}`}</Typography>
            </Stack>)}
          </Stack>}
          <Stack direction="row" spacing={0.7} useFlexGap sx={{ mt: 1, flexWrap: 'wrap', alignItems: 'center' }}><Chip size="small" variant="outlined" label={item.type} /><Chip size="small" label={item.status} />{(item.assigneeId !== selfId || item.creatorId !== selfId) && <Chip size="small" color="primary" variant="outlined" label={`Исполнитель: ${assigneeName}`} />}{item.creatorId && item.creatorId !== selfId && <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Автор: {creatorName}</Typography>}{item.dueAt && <Chip size="small" color={isWorkspaceOverdue(item) ? 'error' : 'default'} label={`Срок: ${formatClinicMoment(item.dueAt)}`} />}{isWorkspaceReminderDue(item) && <Tooltip title="Время напоминания наступило"><NotificationsActiveRounded color="warning" fontSize="small" /></Tooltip>}{item.checklist.length > 0 && <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>☑ {checklistDone}/{item.checklist.length}</Typography>}{patientName && <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Пациент: {patientName}</Typography>}{item.tags.map((tag) => <Chip key={tag} size="small" variant="outlined" label={`#${tag}`} />)}</Stack>
        </Box>
        <Tooltip title={archived ? 'Восстановить' : 'В архив'}><IconButton size="small" aria-label={`${archived ? 'Восстановить' : 'Архивировать'}: ${item.title}`} onClick={onArchive}>{archived ? <RestoreRounded /> : <ArchiveRounded />}</IconButton></Tooltip>
      </Stack>
    </Box>
  )
}
