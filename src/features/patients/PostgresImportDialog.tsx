import { useState } from 'react'
import { CloudDownloadRounded, StorageRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { commitPostgresImport, previewPostgresImport, type ImportPreview, type ImportResult, type PostgresImportConfig } from '../../api/patientImport'

const INITIAL: PostgresImportConfig = {
  url: 'jdbc:postgresql://localhost:5432/clinic_old', username: '', password: '', table: 'public.patients', limit: 1000,
  columns: { fullName: 'full_name', birthDate: 'birth_date', phone: 'phone', secondPhone: 'second_phone', email: 'email' },
}

const REASONS: Record<string, string> = {
  INVALID_FULL_NAME: 'Проверьте ФИО', INVALID_BIRTH_DATE: 'Некорректная дата рождения', INVALID_PHONE: 'Нет телефона',
  INVALID_EMAIL: 'Некорректный e-mail', CONTACT_TOO_LONG: 'Слишком длинный контакт',
  DUPLICATE_IN_CRM: 'Уже есть в AlphaCRM', DUPLICATE_IN_SOURCE: 'Дубль в исходной базе',
}

/** Мастер одноразового переноса: пароль живёт только в состоянии открытой формы. */
export function PostgresImportDialog({ onClose, onDone }: { onClose: () => void; onDone: (message: string) => void }) {
  const [config, setConfig] = useState(INITIAL)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (field: keyof PostgresImportConfig, value: string | number) => {
    setConfig((current) => ({ ...current, [field]: value }))
    setPreview(null)
    setResult(null)
  }
  const setColumn = (field: keyof PostgresImportConfig['columns'], value: string) => {
    setConfig((current) => ({ ...current, columns: { ...current.columns, [field]: value } }))
    setPreview(null)
    setResult(null)
  }
  const valid = config.url.startsWith('jdbc:postgresql://') && config.username.trim() && config.password
    && /^[A-Za-z_][A-Za-z0-9_]*\.[A-Za-z_][A-Za-z0-9_]*$/.test(config.table)
    && config.columns.fullName && config.columns.birthDate && config.columns.phone && config.limit >= 1 && config.limit <= 5000

  const run = async (commit: boolean) => {
    setBusy(true)
    setError('')
    try {
      if (commit) {
        const done = await commitPostgresImport(config)
        setResult(done)
        setPreview(null)
        onDone(`Импорт завершён: создано ${done.created}, дублей ${done.duplicates}, ошибок ${done.errors}`)
      } else {
        setPreview(await previewPostgresImport(config))
        setResult(null)
      }
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={() => !busy && onClose()} fullWidth maxWidth="lg" aria-labelledby="postgres-import-title">
      <DialogTitle id="postgres-import-title"><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><StorageRounded color="primary" /><span>Импорт пациентов из PostgreSQL</span></Stack></DialogTitle>
      <DialogContent>
        <Alert severity="info" icon={<CloudDownloadRounded />} sx={{ mb: 2 }}>Используйте учётную запись исходной базы только для чтения. Пароль не сохраняется. Сначала CRM покажет, что именно будет перенесено; существующие карточки и согласия не изменяются.</Alert>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="JDBC-адрес исходной базы" value={config.url} onChange={(event) => set('url', event.target.value)} /></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth required label="Пользователь только для чтения" value={config.username} onChange={(event) => set('username', event.target.value)} autoComplete="off" /></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth required type="password" label="Пароль исходной базы" value={config.password} onChange={(event) => set('password', event.target.value)} autoComplete="new-password" /></Grid>
          <Grid size={{ xs: 12, md: 8 }}><TextField fullWidth label="Таблица пациентов" value={config.table} onChange={(event) => set('table', event.target.value)} helperText="Схема и таблица, например public.patients" /></Grid>
          <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label="Максимум строк" value={config.limit} onChange={(event) => set('limit', Number(event.target.value))} slotProps={{ htmlInput: { min: 1, max: 5000 } }} /></Grid>
          <Grid size={12}><Typography className="panel-label">СОПОСТАВЛЕНИЕ СТОЛБЦОВ</Typography></Grid>
          {([
            ['fullName', 'ФИО'], ['birthDate', 'Дата рождения'], ['phone', 'Основной телефон'],
            ['secondPhone', 'Второй телефон'], ['email', 'E-mail'],
          ] as const).map(([field, label]) => <Grid key={field} size={{ xs: 12, sm: 6, md: 4 }}><TextField fullWidth label={label} value={config.columns[field]} onChange={(event) => setColumn(field, event.target.value)} helperText={field === 'secondPhone' || field === 'email' ? 'Можно оставить пустым' : 'Обязательное поле'} /></Grid>)}
        </Grid>
        {preview && <Box sx={{ mt: 3 }}>
          <Stack direction="row" spacing={1} sx={{ mb: 1.5, flexWrap: 'wrap' }}>
            <Chip color="success" label={`Новых: ${preview.created}`} />
            <Chip color="warning" label={`Дублей: ${preview.duplicates}`} />
            <Chip color="error" label={`Ошибок: ${preview.errors}`} />
            <Chip variant="outlined" label={`Всего: ${preview.total}`} />
          </Stack>
          <TableContainer sx={{ maxHeight: 360, border: 1, borderColor: 'divider', borderRadius: 2 }}>
            <Table stickyHeader size="small" aria-label="Предпросмотр импорта">
              <TableHead><TableRow><TableCell>Строка</TableCell><TableCell>Пациент</TableCell><TableCell>Телефон</TableCell><TableCell>Результат</TableCell></TableRow></TableHead>
              <TableBody>{preview.rows.map((row) => <TableRow key={row.row}>
                <TableCell>{row.row}</TableCell>
                <TableCell><Typography sx={{ fontWeight: 650 }}>{row.fullName || '—'}</Typography><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{row.birthDate}</Typography></TableCell>
                <TableCell>{row.phone || '—'}</TableCell>
                <TableCell><Chip size="small" color={row.status === 'NEW' ? 'success' : row.status === 'DUPLICATE' ? 'warning' : 'error'} label={row.status === 'NEW' ? 'Создать' : REASONS[row.reason] ?? row.reason} />{row.existingPatientName && <Typography sx={{ mt: 0.5, fontSize: 12, color: 'text.secondary' }}>{row.existingPatientName}</Typography>}</TableCell>
              </TableRow>)}</TableBody>
            </Table>
          </TableContainer>
          <Typography sx={{ mt: 1, color: 'text.secondary', fontSize: 13 }}>Будут созданы только строки «Создать». Для новых карточек потребуется отдельно зарегистрировать согласие на обработку данных.</Typography>
        </Box>}
        {result && <Alert severity="success" sx={{ mt: 2 }}>Создано: {result.created}. Дублей пропущено: {result.duplicates}. Строк с ошибками: {result.errors}.</Alert>}
      </DialogContent>
      <DialogActions>
        <Button disabled={busy} onClick={onClose}>Закрыть</Button>
        <Button disabled={busy || !valid} onClick={() => void run(false)}>{busy && !preview ? 'Читаем…' : preview ? 'Обновить предпросмотр' : 'Проверить данные'}</Button>
        {preview && <Button variant="contained" disabled={busy || preview.created === 0} onClick={() => void run(true)}>Импортировать новые ({preview.created})</Button>}
      </DialogActions>
    </Dialog>
  )
}
