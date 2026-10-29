import { useEffect, useMemo, useState } from 'react'
import { AddRounded, ArchiveRounded, CheckCircleOutlineRounded, DeleteOutlineRounded, NotificationsActiveRounded, NoteAddRounded, RadioButtonUncheckedRounded, RestoreRounded, SaveRounded, ViewKanbanRounded, ViewListRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, IconButton, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import { archiveWorkspaceItem, createWorkspaceItem, createWorkspaceView, deleteWorkspaceView, loadWorkspaceItems, loadWorkspaceViews, updateWorkspaceItem } from '../../api/workspace'
import type { PatientItem, ScheduleItem, WorkspaceFilter, WorkspaceItem, WorkspaceItemInput, WorkspaceView } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { NameDialog } from '../drive/NameDialog'
import type { WorkspaceNavigationIntent } from '../assistant/navigationCatalog'
import { WorkspaceItemDialog } from './WorkspaceItemDialog'
import { blankWorkspaceItem, emptyWorkspaceFilter, filterWorkspace, inputOf, isWorkspaceOverdue, isWorkspaceReminderDue, newWorkspaceId } from './workspace'

type WorkspacePageProps = {
  apiOnline: boolean
  selfId: string
  patients: PatientItem[]
  appointments: ScheduleItem[]
  notify: (message: string) => void
  navigationIntent?: ({ key: number } & WorkspaceNavigationIntent) | null
}

type OfflineStore = { items: WorkspaceItem[]; views: WorkspaceView[] }
const storageKey = (selfId: string) => `alphacrm.workspace.${selfId}`

function offlineStore(selfId: string): OfflineStore {
  try {
    const stored = globalThis.localStorage?.getItem(storageKey(selfId))
    if (stored) return JSON.parse(stored) as OfflineStore
  } catch { /* Песочница продолжит работу в памяти. */ }
  const now = new Date().toISOString()
  return {
    items: [
      { ...blankWorkspaceItem(), id: newWorkspaceId(), title: 'Собрать план на неделю', body: 'Свободная задача: добавьте свои поля, теги и чек-лист.', tags: ['планирование'], checklist: [{ text: 'Выбрать приоритеты', done: false }], createdAt: now, updatedAt: now, completedAt: null, archivedAt: null },
      { ...blankWorkspaceItem('Заметка'), id: newWorkspaceId(), title: 'Идеи для клиники', body: 'Здесь может быть любой текст — обязательной структуры нет.', createdAt: now, updatedAt: now, completedAt: null, archivedAt: null },
    ],
    views: [],
  }
}

/** Личное пространство: серверная версия для работы и локально сохраняемая демо-песочница. */
export function WorkspacePage({ apiOnline, selfId, patients, appointments, notify, navigationIntent }: WorkspacePageProps) {
  const initial = useMemo(() => apiOnline ? { items: [], views: [] } : offlineStore(selfId), [apiOnline, selfId])
  const [items, setItems] = useState<WorkspaceItem[]>(initial.items)
  const [views, setViews] = useState<WorkspaceView[]>(initial.views)
  const [filter, setFilter] = useState<WorkspaceFilter>(emptyWorkspaceFilter)
  const [archived, setArchived] = useState(false)
  const [editor, setEditor] = useState<{ item: WorkspaceItem | null; preset?: string } | null>(null)
  const [quickTitle, setQuickTitle] = useState('')
  const [savingView, setSavingView] = useState(false)
  const [loading, setLoading] = useState(apiOnline)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    setLoading(true)
    Promise.all([loadWorkspaceItems(archived), loadWorkspaceViews()])
      .then(([nextItems, nextViews]) => { if (!cancelled) { setItems(nextItems); setViews(nextViews); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [apiOnline, archived, revision])

  useEffect(() => {
    if (apiOnline) return
    try { globalThis.localStorage?.setItem(storageKey(selfId), JSON.stringify({ items, views })) } catch { /* Нет места — остаётся текущая сессия. */ }
  }, [apiOnline, selfId, items, views])

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
    if (apiOnline) {
      if (editor?.item) await updateWorkspaceItem(editor.item.id, input)
      else await createWorkspaceItem(input)
      refresh()
    } else {
      const now = new Date().toISOString()
      if (editor?.item) {
        const completedAt = input.completed ? editor.item.completedAt ?? now : null
        setItems((current) => current.map((item) => item.id === editor.item?.id ? { ...item, ...input, completedAt, updatedAt: now } : item))
      } else {
        setItems((current) => [{ ...input, id: newWorkspaceId(), createdAt: now, updatedAt: now, completedAt: input.completed ? now : null, archivedAt: null }, ...current])
      }
    }
    notify(editor?.item ? 'Запись сохранена' : 'Запись создана')
    setEditor(null)
  }

  const toggle = async (item: WorkspaceItem) => {
    try {
      const input = { ...inputOf(item), completed: !item.completed }
      if (apiOnline) await updateWorkspaceItem(item.id, input)
      else {
        const now = new Date().toISOString()
        setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, completed: input.completed, completedAt: input.completed ? now : null, updatedAt: now } : entry))
      }
      if (apiOnline) refresh()
    } catch (reason) { setError((reason as Error).message) }
  }

  const setArchive = async (item: WorkspaceItem, value: boolean) => {
    try {
      if (apiOnline) await archiveWorkspaceItem(item.id, value)
      else setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, archivedAt: value ? new Date().toISOString() : null } : entry))
      notify(value ? 'Запись в архиве' : 'Запись восстановлена')
      if (apiOnline) refresh()
    } catch (reason) { setError((reason as Error).message) }
  }

  const quickCreate = async () => {
    const title = quickTitle.trim()
    if (!title) return
    try {
      const input = { ...blankWorkspaceItem(), title }
      if (apiOnline) { await createWorkspaceItem(input); refresh() }
      else {
        const now = new Date().toISOString()
        setItems((current) => [{ ...input, id: newWorkspaceId(), createdAt: now, updatedAt: now, completedAt: null, archivedAt: null }, ...current])
      }
      setQuickTitle('')
    } catch (reason) { setError((reason as Error).message) }
  }

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Моё пространство</Typography><Typography sx={{ color: 'text.secondary' }}>Задачи, заметки и записи в вашей системе — без обязательных сценариев и полей.</Typography></Box>
        {!archived && <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}><Button variant="outlined" startIcon={<NoteAddRounded />} onClick={() => setEditor({ item: null, preset: 'Заметка' })}>Заметка</Button><Button variant="contained" startIcon={<AddRounded />} onClick={() => setEditor({ item: null })}>Создать</Button></Stack>}
      </Box>
      <Alert severity="info" sx={{ mb: 2 }}>Это личные рабочие записи, а не медицинская документация. Клинические заметки и документы остаются в защищённой карточке пациента.</Alert>
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}

      {!archived && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 2 }}>
        <TextField fullWidth size="small" label="Быстрая задача" placeholder="Название — остальное добавите позже" value={quickTitle} onChange={(event) => setQuickTitle(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void quickCreate() } }} />
        <Button variant="outlined" startIcon={<AddRounded />} onClick={() => void quickCreate()} disabled={!quickTitle.trim()}>Добавить</Button>
      </Stack>}

      <Box className="panel workspace-filters" sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: '2fr repeat(4, minmax(130px, 1fr))' }, gap: 1.5 }}>
          <TextField size="small" label="Поиск по всему содержимому" value={filter.query} onChange={(event) => patchFilter('query', event.target.value)} />
          <TextField select size="small" label="Тип" value={filter.type} onChange={(event) => patchFilter('type', event.target.value)}><MenuItem value="">Все типы</MenuItem>{types.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}</TextField>
          <TextField select size="small" label="Тег" value={filter.tag} onChange={(event) => patchFilter('tag', event.target.value)}><MenuItem value="">Все теги</MenuItem>{tags.map((tag) => <MenuItem key={tag} value={tag}>{tag}</MenuItem>)}</TextField>
          <TextField select size="small" label="Срок" value={filter.due} onChange={(event) => patchFilter('due', event.target.value as WorkspaceFilter['due'])}><MenuItem value="all">Любой</MenuItem><MenuItem value="today">Сегодня</MenuItem><MenuItem value="overdue">Просрочено</MenuItem><MenuItem value="week">Ближайшие 7 дней</MenuItem><MenuItem value="none">Без срока</MenuItem></TextField>
          <TextField select size="small" label="Сортировка" value={filter.sort} onChange={(event) => patchFilter('sort', event.target.value as WorkspaceFilter['sort'])}><MenuItem value="due">По сроку</MenuItem><MenuItem value="updated">По изменению</MenuItem><MenuItem value="created">По созданию</MenuItem><MenuItem value="title">По названию</MenuItem></TextField>
        </Box>
        <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, alignItems: 'center', flexWrap: 'wrap' }}>
          <ToggleButtonGroup size="small" exclusive value={filter.display} onChange={(_, value) => { if (value) patchFilter('display', value) }} aria-label="Вид списка"><ToggleButton value="list" aria-label="Список"><ViewListRounded /></ToggleButton><ToggleButton value="board" aria-label="Доска"><ViewKanbanRounded /></ToggleButton></ToggleButtonGroup>
          <Button size="small" startIcon={<SaveRounded />} onClick={() => setSavingView(true)}>Сохранить вид</Button>
          <Button size="small" onClick={() => setFilter(emptyWorkspaceFilter)}>Сбросить</Button>
          <Box sx={{ flex: 1 }} />
          <Button size="small" startIcon={archived ? <RestoreRounded /> : <ArchiveRounded />} onClick={() => { setArchived((value) => !value); setFilter(emptyWorkspaceFilter) }}>{archived ? 'К активным' : 'Архив'}</Button>
        </Stack>
        {views.length > 0 && <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, flexWrap: 'wrap' }}>{views.map((view) => <Chip key={view.id} label={view.name} onClick={() => { setFilter({ ...emptyWorkspaceFilter, ...view.filter }); setArchived(false) }} onDelete={() => {
          if (apiOnline) void deleteWorkspaceView(view.id).then(refresh).catch((reason: Error) => setError(reason.message))
          else setViews((current) => current.filter((entry) => entry.id !== view.id))
        }} deleteIcon={<Tooltip title="Удалить сохранённый вид"><DeleteOutlineRounded /></Tooltip>} />)}</Stack>}
      </Box>

      {loading ? <Typography sx={{ color: 'text.secondary' }}>Загрузка…</Typography> : visible.length === 0 ? <Box className="panel" sx={{ p: 4, textAlign: 'center' }}><Typography variant="h6">{archived ? 'Архив пуст' : 'Здесь пока ничего нет'}</Typography><Typography sx={{ color: 'text.secondary', mt: 1 }}>{archived ? 'Архивированные записи можно восстановить.' : 'Создайте запись или измените фильтры — структура полностью ваша.'}</Typography></Box> : filter.display === 'list' ? (
        <Stack spacing={1.2}>{visible.map((item) => <WorkspaceCard key={item.id} item={item} patientName={patientName(item.patientId)} archived={archived} onOpen={() => setEditor({ item })} onToggle={() => void toggle(item)} onArchive={() => void setArchive(item, !archived)} />)}</Stack>
      ) : (
        <Box className="workspace-board">{statuses.map((status) => <Box component="section" className="workspace-board-column" key={status}><Typography className="panel-label" sx={{ p: 1.5 }}>{status} · {visible.filter((item) => item.status === status).length}</Typography><Stack spacing={1}>{visible.filter((item) => item.status === status).map((item) => <WorkspaceCard key={item.id} item={item} patientName={patientName(item.patientId)} archived={archived} compact onOpen={() => setEditor({ item })} onToggle={() => void toggle(item)} onArchive={() => void setArchive(item, !archived)} />)}</Stack></Box>)}</Box>
      )}

      {editor && <WorkspaceItemDialog item={editor.item} preset={editor.preset} patients={patients} appointments={appointments} onClose={() => setEditor(null)} onSave={save} />}
      {savingView && <NameDialog title="Сохранить текущий вид" initial="" onClose={() => setSavingView(false)} onSave={async (name) => {
        try {
          if (apiOnline) { await createWorkspaceView(name, filter); refresh() }
          else setViews((current) => [...current, { id: newWorkspaceId(), name, filter, createdAt: new Date().toISOString() }])
          setSavingView(false)
          notify('Представление сохранено')
          return null
        } catch (reason) { return (reason as Error).message }
      }} />}
    </>
  )
}

type CardProps = { item: WorkspaceItem; patientName?: string | null; archived: boolean; compact?: boolean; onOpen: () => void; onToggle: () => void; onArchive: () => void }
function WorkspaceCard({ item, patientName, archived, compact, onOpen, onToggle, onArchive }: CardProps) {
  const checklistDone = item.checklist.filter((entry) => entry.done).length
  return (
    <Box className={`panel workspace-card workspace-card--${item.color} ${item.completed ? 'completed' : ''}`} sx={{ p: compact ? 1.2 : 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
        {!archived && <Tooltip title={item.completed ? 'Вернуть в работу' : 'Отметить выполненным'}><IconButton size="small" aria-label={item.completed ? `Вернуть: ${item.title}` : `Выполнить: ${item.title}`} onClick={onToggle}>{item.completed ? <CheckCircleOutlineRounded color="primary" /> : <RadioButtonUncheckedRounded />}</IconButton></Tooltip>}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Button variant="text" onClick={onOpen} sx={{ p: 0, minWidth: 0, textAlign: 'left', textTransform: 'none', color: 'text.primary', fontWeight: 700, textDecoration: item.completed ? 'line-through' : 'none' }}>{item.title}</Button>
          {item.body && !compact && <Typography sx={{ color: 'text.secondary', fontSize: 14, mt: 0.5, whiteSpace: 'pre-wrap', overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>{item.body}</Typography>}
          <Stack direction="row" spacing={0.7} useFlexGap sx={{ mt: 1, flexWrap: 'wrap', alignItems: 'center' }}><Chip size="small" variant="outlined" label={item.type} /><Chip size="small" label={item.status} />{item.dueAt && <Chip size="small" color={isWorkspaceOverdue(item) ? 'error' : 'default'} label={`Срок: ${formatClinicMoment(item.dueAt)}`} />}{isWorkspaceReminderDue(item) && <Tooltip title="Время напоминания наступило"><NotificationsActiveRounded color="warning" fontSize="small" /></Tooltip>}{item.checklist.length > 0 && <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>☑ {checklistDone}/{item.checklist.length}</Typography>}{patientName && <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Пациент: {patientName}</Typography>}{item.tags.map((tag) => <Chip key={tag} size="small" variant="outlined" label={`#${tag}`} />)}</Stack>
        </Box>
        <Tooltip title={archived ? 'Восстановить' : 'В архив'}><IconButton size="small" aria-label={`${archived ? 'Восстановить' : 'Архивировать'}: ${item.title}`} onClick={onArchive}>{archived ? <RestoreRounded /> : <ArchiveRounded />}</IconButton></Tooltip>
      </Stack>
    </Box>
  )
}
