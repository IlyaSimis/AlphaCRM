import { useEffect, useMemo, useState } from 'react'
import { ArrowForwardRounded, AutoAwesomeRounded, SearchRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItemButton, ListItemText, Stack, TextField, Typography } from '@mui/material'
import { loadAssistantStatus, navigateWithAssistant, type AssistantStatus } from '../../api/assistant'
import type { Page } from '../../app/pages'
import type { Role } from '../../shared/roles'
import { navigationCatalog, searchNavigation, type NavigationDestination } from './navigationCatalog'

type AssistantDialogProps = {
  open: boolean
  role: Role
  currentPage: Page
  apiOnline: boolean
  onClose: () => void
  onNavigate: (destination: NavigationDestination) => void
}

/** Диалог выбирает действие из каталога роли; URL и код от модели не принимаются. */
export function AssistantDialog({ open, role, currentPage, apiOnline, onClose, onNavigate }: AssistantDialogProps) {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<AssistantStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const catalog = useMemo(() => navigationCatalog(role), [role])
  const results = useMemo(() => searchNavigation(query, role), [query, role])

  useEffect(() => {
    if (!open || !apiOnline) return
    let cancelled = false
    loadAssistantStatus().then((next) => { if (!cancelled) setStatus(next) }).catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [open, apiOnline])

  const select = (destination: NavigationDestination) => {
    setQuery('')
    setSubmitted(false)
    setError('')
    onNavigate(destination)
    onClose()
  }

  const submit = async () => {
    const text = query.trim()
    if (!text || loading) return
    setSubmitted(true)
    setError('')
    const local = searchNavigation(text, role)[0]
    if (local && local.score >= 85) {
      select(local)
      return
    }
    if (!apiOnline || !status?.enabled) return
    setLoading(true)
    try {
      const answer = await navigateWithAssistant(text, currentPage, catalog.map(({ id, title, description, keywords }) => ({ id, title, description, keywords })))
      const destination = catalog.find((item) => item.id === answer.destinationId)
      if (!destination) throw new Error('Модель выбрала неизвестный раздел. Выберите вариант из списка.')
      setStatus((current) => current ? { ...current, usage: answer.usage } : current)
      select(destination)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="sm" aria-labelledby="assistant-title">
      <DialogTitle id="assistant-title"><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><AutoAwesomeRounded color="primary" /><span>ИИ-навигатор</span></Stack></DialogTitle>
      <DialogContent>
        <Typography sx={{ color: 'text.secondary', mb: 2 }}>Напишите, что хотите сделать. Откроем подходящий раздел, а не инструкцию с путём по меню.</Typography>
        <Box component="form" onSubmit={(event) => { event.preventDefault(); void submit() }}>
          <TextField autoFocus fullWidth label="Что открыть?" placeholder="Например: мои просроченные задачи" value={query} onChange={(event) => { setQuery(event.target.value); setSubmitted(false); setError('') }} disabled={loading} slotProps={{ input: { startAdornment: <SearchRounded sx={{ mr: 1, color: 'text.secondary' }} /> } }} />
        </Box>
        <Stack direction="row" spacing={0.7} useFlexGap sx={{ mt: 1.5, flexWrap: 'wrap' }}>
          {['Задачи на сегодня', 'Услуги и цены', 'Общие файлы'].map((example) => <Chip key={example} size="small" variant="outlined" label={example} onClick={() => setQuery(example)} />)}
        </Stack>
        {status && <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 2 }}>Тариф «{status.usage.planName}»: осталось {status.usage.remainingAiRequests} из {status.usage.monthlyAiRequests} ИИ-запросов в этом месяце. Локальный поиск лимит не расходует.</Typography>}
        {!apiOnline && <Alert severity="info" sx={{ mt: 2 }}>В песочнице работает локальный поиск без обращения к внешней модели.</Alert>}
        {apiOnline && status && !status.enabled && <Alert severity="info" sx={{ mt: 2 }}>Внешняя модель не настроена. Точные совпадения по-прежнему открываются локально.</Alert>}
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        {query.trim() && results.length > 0 && <List aria-label="Подходящие разделы" sx={{ mt: 1 }}>
          {results.map((result) => <ListItemButton key={result.id} onClick={() => select(result)} disabled={loading} sx={{ border: 1, borderColor: 'divider', borderRadius: 2, mb: 0.7 }}>
            <ListItemText primary={result.title} secondary={result.description} />
            <ArrowForwardRounded color="action" />
          </ListItemButton>)}
        </List>}
        {submitted && !loading && !error && results.length === 0 && <Alert severity="warning" sx={{ mt: 2 }}>Раздел не найден. Переформулируйте запрос без данных пациентов.</Alert>}
        {loading && <Stack direction="row" spacing={1} sx={{ mt: 2, alignItems: 'center' }}><CircularProgress size={20} /><Typography>Уточняю нужный раздел…</Typography></Stack>}
        <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 2 }}>Не указывайте ФИО, контакты и медицинские сведения. Модель получает только вопрос и каталог доступных страниц.</Typography>
      </DialogContent>
      <DialogActions><Button onClick={onClose} disabled={loading}>Отмена</Button><Button variant="contained" onClick={() => void submit()} disabled={!query.trim() || loading}>{loading ? 'Ищу…' : 'Открыть раздел'}</Button></DialogActions>
    </Dialog>
  )
}
