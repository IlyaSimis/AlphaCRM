import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField, Typography } from '@mui/material'
import { createPatientContract } from '../../api/contracts'
import type { ContractTemplateItem, PatientContractItem, ServiceItem } from '../../api/types'

const fieldLabels: Record<string, string> = {
  'service.price': 'Стоимость услуги',
  'custom.comment': 'Комментарий',
  'custom.representative': 'Представитель',
}

type ContractCreateDialogProps = {
  open: boolean
  patientId: string
  templates: ContractTemplateItem[]
  services: ServiceItem[]
  onClose: () => void
  onCreated: (contract: PatientContractItem) => void
}

/** Единый сценарий формирования договора из карточки пациента и раздела «Документы». */
export function ContractCreateDialog({ open, patientId, templates, services, onClose, onCreated }: ContractCreateDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [templateId, setTemplateId] = useState(templates[0]?.id ?? '')
  const [serviceId, setServiceId] = useState('')
  const [values, setValues] = useState<Record<string, string>>({})
  const template = templates.find((row) => row.id === templateId)
  const activeServices = services.filter((service) => service.active)
  const service = activeServices.find((row) => row.id === serviceId)
  const needsService = Boolean(template?.placeholders.some((key) => key.startsWith('service.')))
  const manualFields = template?.placeholders.filter((key) => {
    if (key.startsWith('patient.') || key.startsWith('clinic.') || key.startsWith('contract.')) return false
    if (key.startsWith('service.')) return key === 'service.price' && service?.priceMode === 'OPEN'
    return true
  }) ?? []
  const incomplete = manualFields.some((key) => !values[key]?.trim())

  const create = async () => {
    if (!template || incomplete || (needsService && !service)) return
    setBusy(true)
    setError('')
    try {
      const manualValues = Object.fromEntries(manualFields.map((key) => [key, values[key].trim()]))
      const contract = await createPatientContract(patientId, { templateId: template.id, serviceId: service?.id, values: manualValues })
      onCreated(contract)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onClose={() => { if (!busy) onClose() }} fullWidth maxWidth="sm">
      <DialogTitle>Сформировать договор</DialogTitle>
      <DialogContent sx={{ display: 'grid', gap: 2, pt: 1 }}>
        <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>Данные пациента, клиники, дата и номер подставятся автоматически. После создания документ не изменяется.</Typography>
        <TextField select label="Шаблон" value={templateId} onChange={(event) => { setTemplateId(event.target.value); setValues({}) }} sx={{ mt: 1 }}>
          {templates.map((row) => <MenuItem key={row.id} value={row.id}>{row.name} · версия {row.version}</MenuItem>)}
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
        {error && <Alert severity="error">{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button disabled={busy} onClick={onClose}>Отмена</Button>
        <Button variant="contained" disabled={busy || !template || incomplete || (needsService && !service)} onClick={() => void create()}>Сформировать</Button>
      </DialogActions>
    </Dialog>
  )
}
