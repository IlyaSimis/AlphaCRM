import { useEffect, useRef, useState } from 'react'
import { DescriptionOutlined, PictureAsPdfRounded, PrintRounded, UploadFileRounded } from '@mui/icons-material'
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, MenuItem, TextField, Typography,
} from '@mui/material'
import {
  contractTemplateProblem, downloadPatientContract, loadContractPatients, loadContractTemplates,
  printPatientContract, uploadContractTemplate,
} from '../../api/contracts'
import type { ContractPatientItem, ContractTemplateItem, PatientContractItem, ServiceItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'
import { ContractCreateDialog } from './ContractCreateDialog'

type ContractTemplatesPageProps = {
  role: Role
  apiOnline: boolean
  services: ServiceItem[]
  notify: (message: string) => void
}

/** Каталог шаблонов и общее для всех сотрудников формирование договоров. */
export function ContractTemplatesPage({ role, apiOnline, services, notify }: ContractTemplatesPageProps) {
  const mayUpload = can(role, 'clinical.patient-file.write')
  const [items, setItems] = useState<ContractTemplateItem[] | null>(null)
  const [patients, setPatients] = useState<ContractPatientItem[] | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [revision, setRevision] = useState(0)
  const [open, setOpen] = useState(false)
  const [choosingPatient, setChoosingPatient] = useState(false)
  const [patientId, setPatientId] = useState('')
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState<PatientContractItem | null>(null)
  const [busy, setBusy] = useState(false)
  const [name, setName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const input = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    Promise.all([loadContractTemplates(), loadContractPatients()])
      .then(([templateRows, patientRows]) => {
        if (cancelled) return
        setItems(templateRows)
        setPatients(patientRows)
        setError('')
      })
      .catch((reason: Error) => {
        if (cancelled) return
        setItems(null)
        setPatients(null)
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

  const download = async (format: 'docx' | 'pdf') => {
    if (!created) return
    setActionError('')
    try {
      await downloadPatientContract(created, format)
    } catch (reason) {
      setActionError((reason as Error).message)
    }
  }

  const print = async () => {
    if (!created) return
    setActionError('')
    try {
      await printPatientContract(created)
    } catch (reason) {
      setActionError((reason as Error).message)
    }
  }

  return (
    <Box component="section" aria-label="Шаблоны договоров">
      <Box className="section-intro">
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750 }}>Документы и договоры</Typography>
          <Typography sx={{ color: 'text.secondary' }}>Выберите пациента и DOCX-шаблон: реквизиты подставятся автоматически, а готовый договор можно скачать или распечатать.</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {apiOnline && !error && <Button variant="contained" startIcon={<DescriptionOutlined />} disabled={!items?.length || !patients?.length} onClick={() => { setActionError(''); setCreated(null); setPatientId(''); setChoosingPatient(true) }}>Сформировать договор</Button>}
          {apiOnline && mayUpload && !error && <Button variant="outlined" startIcon={<UploadFileRounded />} onClick={() => { setActionError(''); setOpen(true) }}>Загрузить шаблон</Button>}
        </Box>
      </Box>
      {!apiOnline && <Alert severity="info">Шаблоны и договоры хранятся на сервере и доступны только при подключённом API.</Alert>}
      {error && <Alert severity="warning">{error}</Alert>}
      {actionError && !open && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      {created && (
        <Alert severity="success" sx={{ mb: 2 }} action={<Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
          <Button color="inherit" size="small" onClick={() => void download('docx')}>DOCX</Button>
          {created.pdfAvailable && <Button color="inherit" size="small" startIcon={<PictureAsPdfRounded />} onClick={() => void download('pdf')}>PDF</Button>}
          {created.pdfAvailable && <Button color="inherit" size="small" startIcon={<PrintRounded />} onClick={() => void print()}>Печать PDF</Button>}
        </Box>}>Договор {created.contractNumber} сформирован.</Alert>
      )}
      {apiOnline && !error && items === null && <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress size={28} aria-label="Загрузка шаблонов" /></Box>}
      {apiOnline && !error && items !== null && (
        <Box className="panel">
          {items.length === 0 ? (
            <Box className="empty" sx={{ minHeight: 160 }}>
              <DescriptionOutlined />
              <Typography sx={{ fontWeight: 700 }}>Шаблонов пока нет</Typography>
              {mayUpload && <Typography sx={{ color: 'text.secondary' }}>Загрузите первый DOCX-шаблон, чтобы формировать договоры.</Typography>}
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

      <Dialog open={choosingPatient} onClose={() => setChoosingPatient(false)} fullWidth maxWidth="xs">
        <DialogTitle>Пациент для договора</DialogTitle>
        <DialogContent>
          <TextField select required fullWidth label="Пациент" value={patientId} onChange={(event) => setPatientId(event.target.value)} sx={{ mt: 1 }}>
            {patients?.map((patient) => <MenuItem key={patient.id} value={patient.id}>{patient.name}</MenuItem>)}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setChoosingPatient(false)}>Отмена</Button>
          <Button variant="contained" disabled={!patientId} onClick={() => { setChoosingPatient(false); setCreating(true) }}>Далее</Button>
        </DialogActions>
      </Dialog>
      <ContractCreateDialog
        key={`${patientId}-${creating}`}
        open={creating}
        patientId={patientId}
        templates={items ?? []}
        services={services}
        onClose={() => setCreating(false)}
        onCreated={(contract) => {
          setCreating(false)
          setCreated(contract)
          notify('Договор сформирован')
        }}
      />

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
