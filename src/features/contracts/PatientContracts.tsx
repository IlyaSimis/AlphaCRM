import { useEffect, useState } from 'react'
import { DescriptionOutlined, PictureAsPdfRounded } from '@mui/icons-material'
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, MenuItem, TextField, Typography,
} from '@mui/material'
import { createPatientContract, downloadPatientContract, loadContractTemplates, loadPatientContracts } from '../../api/contracts'
import type { ContractTemplateItem, PatientContractItem, ServiceItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { can } from '../../shared/permissions'
import { canEditPatients, type Role } from '../../shared/roles'
import { isUuid } from '../../shared/text'

const fieldLabels: Record<string, string> = {
  'service.price': 'Стоимость услуги',
  'custom.comment': 'Комментарий',
  'custom.representative': 'Представитель',
}

type PatientContractsProps = {
  patientId: string
  role: Role
  apiOnline: boolean
  services: ServiceItem[]
  notify: (message: string) => void
}

/** Договоры формируются из версионированных DOCX; готовый документ дальше не изменяется. */
export function PatientContracts({ patientId, role, apiOnline, services, notify }: PatientContractsProps) {
  const visible = can(role, 'clinical.patient.read') && can(role, 'clinical.patient.contacts-read')
  const live = apiOnline && visible && isUuid(patientId)
  const mayCreate = canEditPatients(role)
  const [templates, setTemplates] = useState<ContractTemplateItem[] | null>(null)
  const [items, setItems] = useState<PatientContractItem[] | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [revision, setRevision] = useState(0)
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [templateId, setTemplateId] = useState('')
  const [serviceId, setServiceId] = useState('')
  const [values, setValues] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!live) return
    let cancelled = false
    Promise.all([loadContractTemplates(), loadPatientContracts(patientId)])
      .then(([templateRows, contractRows]) => {
        if (cancelled) return
        setTemplates(templateRows)
        setItems(contractRows)
        setError('')
      })
      .catch((reason: Error) => {
        if (cancelled) return
        setTemplates(null)
        setItems(null)
        setError(reason.message)
      })
    return () => { cancelled = true }
  }, [live, patientId, revision])

  if (!visible) return null

  const template = templates?.find((row) => row.id === templateId)
  const activeServices = services.filter((service) => service.active)
  const service = activeServices.find((row) => row.id === serviceId)
  const needsService = Boolean(template?.placeholders.some((key) => key.startsWith('service.')))
  const manualFields = template?.placeholders.filter((key) => {
    if (key.startsWith('patient.') || key.startsWith('clinic.') || key.startsWith('contract.')) return false
    if (key.startsWith('service.')) return key === 'service.price' && service?.priceMode === 'OPEN'
    return true
  }) ?? []
  const incomplete = manualFields.some((key) => !values[key]?.trim())

  const openCreate = () => {
    setActionError('')
    setTemplateId(templates?.[0]?.id ?? '')
    setServiceId('')
    setValues({})
    setCreating(true)
  }

  const create = async () => {
    if (!template || incomplete || (needsService && !service)) return
    setBusy(true)
    setActionError('')
    try {
      const manualValues = Object.fromEntries(manualFields.map((key) => [key, values[key].trim()]))
      await createPatientContract(patientId, { templateId: template.id, serviceId: service?.id, values: manualValues })
      setCreating(false)
      notify('Договор сформирован')
      setRevision((value) => value + 1)
    } catch (reason) {
      setActionError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const download = async (contract: PatientContractItem, format: 'docx' | 'pdf') => {
    setActionError('')
    try {
      await downloadPatientContract(contract, format)
    } catch (reason) {
      setActionError((reason as Error).message)
    }
  }

  return (
    <Box className="panel patient-contracts" component="section" aria-label="Договоры пациента">
      <Box className="panel-title-row">
        <Typography className="panel-label">ДОГОВОРЫ</Typography>
        {live && mayCreate && !error && <Button size="small" variant="contained" disabled={!templates?.length} onClick={openCreate}>Сформировать</Button>}
      </Box>
      {!live && <Alert severity="info" sx={{ mt: 1 }}>Договоры доступны при подключённом API.</Alert>}
      {live && error && <Alert severity="warning" sx={{ mt: 1 }}>{error}</Alert>}
      {actionError && !creating && <Alert severity="error" sx={{ mt: 1 }}>{actionError}</Alert>}
      {live && !error && items === null && <CircularProgress size={22} sx={{ mt: 1 }} aria-label="Загрузка договоров" />}
      {live && !error && items?.length === 0 && <Typography sx={{ color: 'text.secondary', mt: 1 }}>Договоров пока нет.</Typography>}
      {live && !error && templates?.length === 0 && <Typography sx={{ color: 'text.secondary', mt: 1 }}>Сначала загрузите DOCX-шаблон во вкладке «Документы».</Typography>}
      {items?.map((contract) => (
        <Box className="history-item" key={contract.id}>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography sx={{ fontWeight: 650, overflowWrap: 'anywhere' }}><DescriptionOutlined sx={{ fontSize: 17, verticalAlign: 'middle', mr: 0.5 }} />{contract.contractNumber} · {contract.templateName}</Typography>
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>
              {[contract.serviceName, contract.createdByName ?? 'сотрудник', formatClinicMoment(contract.createdAt)].filter(Boolean).join(' · ')}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            {contract.pdfAvailable && <Chip size="small" variant="outlined" icon={<PictureAsPdfRounded />} label="PDF" />}
            <Button size="small" aria-label={`Скачать DOCX ${contract.contractNumber}`} onClick={() => void download(contract, 'docx')}>DOCX</Button>
            {contract.pdfAvailable && <Button size="small" aria-label={`Скачать PDF ${contract.contractNumber}`} onClick={() => void download(contract, 'pdf')}>PDF</Button>}
          </Box>
        </Box>
      ))}

      <Dialog open={creating} onClose={() => { if (!busy) setCreating(false) }} fullWidth maxWidth="sm">
        <DialogTitle>Сформировать договор</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: 1 }}>
          <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>Данные пациента, клиники, дата и номер подставятся автоматически. После создания документ не изменяется.</Typography>
          <TextField select label="Шаблон" value={templateId} onChange={(event) => { setTemplateId(event.target.value); setValues({}) }} sx={{ mt: 1 }}>
            {templates?.map((row) => <MenuItem key={row.id} value={row.id}>{row.name} · версия {row.version}</MenuItem>)}
          </TextField>
          {needsService && <TextField select required label="Услуга" value={serviceId} onChange={(event) => setServiceId(event.target.value)}>
            {activeServices.map((row) => <MenuItem key={row.id} value={row.id}>{row.name}</MenuItem>)}
          </TextField>}
          {manualFields.map((key) => <TextField
            key={key}
            required
            label={fieldLabels[key] ?? key}
            value={values[key] ?? ''}
            onChange={(event) => setValues((current) => ({ ...current, [key]: event.target.value }))}
            helperText={`Переменная ${key}`}
            multiline={key.includes('comment') || key.includes('terms')}
            minRows={key.includes('comment') || key.includes('terms') ? 3 : undefined}
          />)}
          {actionError && <Alert severity="error">{actionError}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button disabled={busy} onClick={() => setCreating(false)}>Отмена</Button>
          <Button variant="contained" disabled={busy || !template || incomplete || (needsService && !service)} onClick={() => void create()}>Сформировать</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
