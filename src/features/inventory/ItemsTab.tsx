import { useEffect, useState } from 'react'
import { AddRounded, EditRounded } from '@mui/icons-material'
import { Alert, Box, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { createCategory, loadCategories, loadItems, saveItem, type ItemInput } from '../../api/inventory'
import type { InventoryCategory, InventoryItem, ItemKind, MarkingSystem } from '../../api/types'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'
import { formatQuantity, kindLabels, markingLabels } from './inventory'

type ItemsTabProps = { role: Role; notify: (message: string) => void }

const sorts: Array<{ value: string; label: string }> = [
  { value: 'name', label: 'По названию (А–Я)' },
  { value: '-name', label: 'По названию (Я–А)' },
  { value: 'category', label: 'По категории' },
  { value: '-stock', label: 'Сначала больший остаток' },
  { value: 'stock', label: 'Сначала меньший остаток' },
]

/** Номенклатура с фильтрами по категории, виду и маркировке и сортировкой. */
export function ItemsTab({ role, notify }: ItemsTabProps) {
  const editable = can(role, 'inventory.item.write')
  const [items, setItems] = useState<InventoryItem[] | null>(null)
  const [categories, setCategories] = useState<InventoryCategory[]>([])
  const [category, setCategory] = useState('')
  const [kind, setKind] = useState('')
  const [marked, setMarked] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState('name')
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [editing, setEditing] = useState<InventoryItem | 'new' | null>(null)
  const [newCategory, setNewCategory] = useState(false)

  useEffect(() => {
    loadCategories().then(setCategories).catch((reason: Error) => setError(reason.message))
  }, [revision])

  useEffect(() => {
    let cancelled = false
    loadItems({ category, kind, marked: marked === '' ? null : marked === 'true', q: query.trim(), sort })
      .then((rows) => { if (!cancelled) { setItems(rows); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [category, kind, marked, query, sort, revision])

  return (
    <Box className="panel">
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2, flexWrap: 'wrap' }} useFlexGap>
        <TextField size="small" select label="Категория" value={category} onChange={(event) => setCategory(event.target.value)} sx={{ minWidth: 180 }}>
          <MenuItem value="">Все категории</MenuItem>
          {categories.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
        </TextField>
        <TextField size="small" select label="Вид" value={kind} onChange={(event) => setKind(event.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">Все виды</MenuItem>
          {(Object.keys(kindLabels) as ItemKind[]).map((value) => <MenuItem key={value} value={value}>{kindLabels[value]}</MenuItem>)}
        </TextField>
        <TextField size="small" select label="Маркировка" value={marked} onChange={(event) => setMarked(event.target.value)} sx={{ minWidth: 170 }}>
          <MenuItem value="">Все позиции</MenuItem>
          <MenuItem value="true">Маркируемые</MenuItem>
          <MenuItem value="false">Без маркировки</MenuItem>
        </TextField>
        <TextField size="small" label="Название или GTIN" value={query} onChange={(event) => setQuery(event.target.value)} />
        <TextField size="small" select label="Сортировка" value={sort} onChange={(event) => setSort(event.target.value)} sx={{ minWidth: 220 }}>
          {sorts.map((item) => <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>)}
        </TextField>
        {editable && <Button variant="outlined" onClick={() => setNewCategory(true)}>Новая категория</Button>}
        {editable && <Button variant="contained" startIcon={<AddRounded />} onClick={() => setEditing('new')}>Новая позиция</Button>}
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {items === null && !error
        ? <Box className="empty" sx={{ minHeight: 120 }}><CircularProgress aria-label="Загрузка номенклатуры" /></Box>
        : items && items.length === 0
          ? <Typography sx={{ color: 'text.secondary' }}>{query || category || kind || marked ? 'Ничего не нашлось.' : 'Позиций пока нет. Заведите позицию кнопкой «Новая позиция» или сразу оформите «Поступление» — новые позиции из него попадут сюда сами.'}</Typography>
          : <table className="audit-table">
            <thead><tr><th>Название</th><th>Категория</th><th>Вид</th><th>Ед.</th><th>GTIN</th><th>Маркировка</th><th>Остаток</th>{editable && <th />}</tr></thead>
            <tbody>
              {(items ?? []).map((item) => (
                <tr key={item.id} style={{ opacity: item.active ? 1 : 0.6 }}>
                  <td>{item.name}{item.rxOnly && <Chip size="small" variant="outlined" label="рецептурный" sx={{ ml: 1 }} />}{!item.active && <Chip size="small" variant="outlined" label="выключена" sx={{ ml: 1 }} />}</td>
                  <td>{item.categoryName}</td>
                  <td>{item.kind ? kindLabels[item.kind] : '—'}</td>
                  <td>{item.unit}</td>
                  <td className="nowrap">{item.gtin ?? '—'}</td>
                  <td>{markingLabels[item.markingSystem]}</td>
                  <td className="nowrap">{formatQuantity(item.stock)}</td>
                  {editable && <td><IconButton size="small" aria-label={`Изменить ${item.name}`} onClick={() => setEditing(item)}><EditRounded fontSize="small" /></IconButton></td>}
                </tr>
              ))}
            </tbody>
          </table>}
      {editing && <ItemDialog item={editing === 'new' ? null : editing} categories={categories} onClose={() => setEditing(null)} onSaved={(saved) => { setEditing(null); setRevision((value) => value + 1); notify(`Позиция «${saved.name}» сохранена`) }} />}
      {newCategory && <CategoryDialog categories={categories} onClose={() => setNewCategory(false)} onSaved={(saved) => { setNewCategory(false); setRevision((value) => value + 1); notify(`Категория «${saved.name}» создана`) }} />}
    </Box>
  )
}

function ItemDialog({ item, categories, onClose, onSaved }: { item: InventoryItem | null; categories: InventoryCategory[]; onClose: () => void; onSaved: (item: InventoryItem) => void }) {
  const [draft, setDraft] = useState<ItemInput>(() => item
    ? { categoryId: item.categoryId, name: item.name, unit: item.unit, gtin: item.gtin, marked: item.marked, markingSystem: item.markingSystem, rxOnly: item.rxOnly, active: item.active }
    : { categoryId: null, kind: 'CONSUMABLE', name: '', unit: 'шт', gtin: null, marked: false, markingSystem: 'NONE', rxOnly: false })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (patch: Partial<ItemInput>) => setDraft((current) => ({ ...current, ...patch }))
  const kind = categories.find((category) => category.id === draft.categoryId)?.kind ?? draft.kind

  const save = async () => {
    if (!draft.name.trim() || !draft.unit.trim()) {
      setError('Укажите название и единицу измерения')
      return
    }
    if (draft.gtin && !/^\d{14}$/.test(draft.gtin)) {
      setError('GTIN — 14 цифр')
      return
    }
    if (draft.marked && (!draft.gtin || draft.markingSystem === 'NONE')) {
      setError('Маркируемой позиции нужны GTIN и система маркировки')
      return
    }
    setBusy(true)
    try {
      onSaved(await saveItem(item?.id ?? null, { ...draft, name: draft.name.trim(), unit: draft.unit.trim(), gtin: draft.gtin || null, markingSystem: draft.marked ? draft.markingSystem : 'NONE' }))
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{item ? 'Позиция номенклатуры' : 'Новая позиция'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Название" value={draft.name} onChange={(event) => set({ name: event.target.value })} autoFocus />
          <Stack direction="row" spacing={2}>
            <TextField select label="Категория" value={draft.categoryId ?? ''} onChange={(event) => set({ categoryId: event.target.value || null })} sx={{ flex: 1 }} helperText={draft.categoryId ? undefined : 'Позиция попадёт в основную категорию вида'}>
              <MenuItem value="">Основная категория вида</MenuItem>
              {categories.map((category) => <MenuItem key={category.id} value={category.id}>{category.name} · {kindLabels[category.kind]}</MenuItem>)}
            </TextField>
            {!draft.categoryId && <TextField select label="Вид" value={draft.kind ?? 'CONSUMABLE'} onChange={(event) => set({ kind: event.target.value as ItemKind })} sx={{ width: 180 }}>
              {(Object.keys(kindLabels) as ItemKind[]).map((value) => <MenuItem key={value} value={value}>{kindLabels[value]}</MenuItem>)}
            </TextField>}
          </Stack>
          <Stack direction="row" spacing={2}>
            <TextField label="Единица" value={draft.unit} onChange={(event) => set({ unit: event.target.value })} sx={{ width: 140 }} />
            <TextField label="GTIN (14 цифр)" value={draft.gtin ?? ''} onChange={(event) => set({ gtin: event.target.value.trim() || null })} fullWidth />
          </Stack>
          <FormControlLabel control={<Checkbox checked={draft.marked} onChange={(event) => set({ marked: event.target.checked, markingSystem: event.target.checked ? (kind === 'DRUG' ? 'MDLP' : 'GIS_MT') : 'NONE' })} />} label="Маркируется (Честный знак): при движении сканируются марки упаковок" />
          {draft.marked && <TextField select label="Система маркировки" value={draft.markingSystem} onChange={(event) => set({ markingSystem: event.target.value as MarkingSystem })} helperText="Лекарства — МДЛП (вывод из оборота через регистратор выбытия); медизделия и товары — ГИС МТ.">
            <MenuItem value="MDLP">{markingLabels.MDLP}</MenuItem>
            <MenuItem value="GIS_MT">{markingLabels.GIS_MT}</MenuItem>
          </TextField>}
          <FormControlLabel control={<Checkbox checked={draft.rxOnly} onChange={(event) => set({ rxOnly: event.target.checked })} />} label="Рецептурный препарат" />
          {item && <FormControlLabel control={<Checkbox checked={draft.active !== false} onChange={(event) => set({ active: event.target.checked })} />} label="Позиция используется (выключенную нельзя добавить в накладную)" />}
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" disabled={busy} onClick={() => void save()}>Сохранить</Button></DialogActions>
    </Dialog>
  )
}

function CategoryDialog({ categories, onClose, onSaved }: { categories: InventoryCategory[]; onClose: () => void; onSaved: (category: InventoryCategory) => void }) {
  const [name, setName] = useState('')
  const [parentId, setParentId] = useState('')
  const [kind, setKind] = useState<ItemKind>('CONSUMABLE')
  const [error, setError] = useState('')

  const save = async () => {
    if (!name.trim()) {
      setError('Укажите название категории')
      return
    }
    try {
      onSaved(await createCategory({ name: name.trim(), parentId: parentId || null, kind }))
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Новая категория</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Название" value={name} onChange={(event) => setName(event.target.value)} />
          <TextField select label="Вид" value={kind} onChange={(event) => setKind(event.target.value as ItemKind)}>
            {(Object.keys(kindLabels) as ItemKind[]).map((value) => <MenuItem key={value} value={value}>{kindLabels[value]}</MenuItem>)}
          </TextField>
          <TextField select label="Входит в категорию" value={parentId} onChange={(event) => setParentId(event.target.value)}>
            <MenuItem value="">— верхний уровень —</MenuItem>
            {categories.map((category) => <MenuItem key={category.id} value={category.id}>{category.name}</MenuItem>)}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" onClick={() => void save()}>Создать</Button></DialogActions>
    </Dialog>
  )
}
