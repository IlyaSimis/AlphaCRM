import { useEffect, useRef, useState } from 'react'
import { DescriptionOutlined, UploadFileRounded } from '@mui/icons-material'
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, TextField, Typography,
} from '@mui/material'
import { contractTemplateProblem, loadContractTemplates, uploadContractTemplate } from '../../api/contracts'
import type { ContractTemplateItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'

type ContractTemplatesPageProps = {
  role: Role
  apiOnline: boolean
  notify: (message: string) => void
}

/** Каталог версий DOCX-шаблонов. Готовые договоры формируются уже в карточке пациента. */
export function ContractTemplatesPage({ role, apiOnline, notify }: ContractTemplatesPageProps) {
  const mayUpload = can(role, 'clinical.patient-file.write')
  const [items, setItems] = useState<ContractTemplateItem[] | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [revision, setRevision] = useState(0)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [name, setName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const input = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    loadContractTemplates()
      .then((rows) => {
        if (cancelled) return
        setItems(rows)
        setError('')
      })
      .catch((reason: Error) => {
        if (cancelled) return
        setItems(null)
        setError(reason.message)
      })
    return () => { cancelled = true }
  }, [apiOnline, revision])

  const upload = async () => {
    if (!file || !name.trim()) return
    const problem = contractTemplateProblem(file)
    if (problem) {
      setActionError(problem)
      return
    }
    setBusy(true)
    setActionError('')
    try {
      await uploadContractTemplate(name.trim(), file)
      setOpen(false)
      setFile(null)
      setName('')
      notify('Шаблон договора загружен')
      setRevision((value) => value + 1)
    } catch (reason) {
      setActionError((reason as Error).message)
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <Box component="section" aria-label="Шаблоны договоров">
      <Box className="section-intro">
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750 }}>Шаблоны договоров</Typography>
          <Typography sx={{ color: 'text.secondary' }}>Загружайте DOCX-шаблоны клиники. Формирование договора для конкретного пациента выполняется в его карточке.</Typography>
        </Box>
        {apiOnline && mayUpload && !error && <Button variant="contained" startIcon={<UploadFileRounded />} onClick={() => { setActionError(''); setOpen(true) }}>Загрузить шаблон</Button>}
      </Box>
      {!apiOnline && <Alert severity="info">Шаблоны хранятся на сервере и доступны только при подключённом API.</Alert>}
      {error && <Alert severity="warning">{error}</Alert>}
      {apiOnline && !error && items === null && <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress size={28} aria-label="Загрузка шаблонов" /></Box>}
      {apiOnline && !error && items !== null && (
        <Box className="panel">
          {items.length === 0 ? (
            <Box className="empty" sx={{ minHeight: 160 }}>
              <DescriptionOutlined />
              <Typography sx={{ fontWeight: 700 }}>Шаблонов пока нет</Typography>
              {mayUpload && <Typography sx={{ color: 'text.secondary' }}>Загрузите первый DOCX-шаблон, чтобы формировать договоры в карточках пациентов.</Typography>}
            </Box>
          ) : items.map((template) => (
            <Box className="history-item" key={template.id} sx={{ alignItems: 'flex-start' }}>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}><DescriptionOutlined sx={{ fontSize: 18, verticalAlign: 'middle', mr: 0.5 }} />{template.name} · версия {template.version}</Typography>
                <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 1 }}>{template.originalName} · {formatClinicMoment(template.createdAt)}</Typography>
                <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
                  {template.placeholders.map((placeholder) => <Chip key={placeholder} size="small" variant="outlined" label={`{{${placeholder}}}`} />)}
                </Box>
              </Box>
            </Box>
          ))}
        </Box>
      )}
      <Alert severity="info" sx={{ mt: 2 }}>
        В DOCX укажите переменные в двойных фигурных скобках: {'{{patient.fullName}}'}, {'{{service.name}}'}, {'{{service.price}}'}, {'{{contract.date}}'}, {'{{contract.number}}'}. Собственные переменные латиницей, например {'{{custom.note}}'}, станут полями для ручного заполнения.
      </Alert>

      <Dialog open={open} onClose={() => { if (!busy) setOpen(false) }} fullWidth maxWidth="sm">
        <DialogTitle>Шаблон договора</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: 1 }}>
          <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>Загрузите DOCX до 5 МБ. Стили, таблицы и колонтитулы сохранятся; новая загрузка создаёт отдельную версию шаблона.</Typography>
          <TextField required label="Название шаблона" value={name} onChange={(event) => setName(event.target.value)} sx={{ mt: 1 }} />
          <input ref={input} type="file" hidden accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" aria-label="Файл шаблона DOCX" onChange={(event) => {
            const selected = event.target.files?.[0] ?? null
            setFile(selected)
            if (selected && !name.trim()) setName(selected.name.replace(/\.docx$/i, ''))
          }} />
          <Button variant="outlined" onClick={() => input.current?.click()}>{file?.name ?? 'Выбрать DOCX'}</Button>
          {actionError && <Alert severity="error">{actionError}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button disabled={busy} onClick={() => setOpen(false)}>Отмена</Button>
          <Button variant="contained" disabled={busy || !file || !name.trim()} onClick={() => void upload()}>Загрузить</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
