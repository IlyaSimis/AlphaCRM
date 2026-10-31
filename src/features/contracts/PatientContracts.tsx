import { useEffect, useState } from 'react'
import { DescriptionOutlined, PictureAsPdfRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material'
import { downloadPatientContract, loadContractTemplates, loadPatientContracts, previewPatientContract, printPatientContract } from '../../api/contracts'
import type { ContractTemplateItem, PatientContractItem, ServiceItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'
import { isUuid } from '../../shared/text'
import { FilePreviewDialog, type FilePreviewSource } from '../files/FilePreviewDialog'
import { ContractCreateDialog } from './ContractCreateDialog'

type PatientContractsProps = {
  patientId: string
  role: Role
  apiOnline: boolean
  services: ServiceItem[]
  notify: (message: string) => void
}

/** Договоры формируются из версионированных DOCX; готовый документ дальше не изменяется. */
export function PatientContracts({ patientId, role, apiOnline, services, notify }: PatientContractsProps) {
  const visible = can(role, 'clinical.contract.use')
  const live = apiOnline && visible && isUuid(patientId)
  const [templates, setTemplates] = useState<ContractTemplateItem[] | null>(null)
  const [items, setItems] = useState<PatientContractItem[] | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [preview, setPreview] = useState<FilePreviewSource | null>(null)
  const [revision, setRevision] = useState(0)
  const [creating, setCreating] = useState(false)

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

  const download = async (contract: PatientContractItem, format: 'docx' | 'pdf') => {
    setActionError('')
    try {
      await downloadPatientContract(contract, format)
    } catch (reason) {
      setActionError((reason as Error).message)
    }
  }

  const print = async (contract: PatientContractItem) => {
    setActionError('')
    try {
      await printPatientContract(contract)
    } catch (reason) {
      setActionError((reason as Error).message)
    }
  }

  return (
    <Box className="panel patient-contracts" component="section" aria-label="Договоры пациента">
      <Box className="panel-title-row">
        <Typography className="panel-label">ДОГОВОРЫ</Typography>
        {live && !error && <Button size="small" variant="contained" disabled={!templates?.length} onClick={() => { setActionError(''); setCreating(true) }}>Сформировать</Button>}
      </Box>
      {!live && <Alert severity="info" sx={{ mt: 1 }}>Договоры доступны при подключённом API.</Alert>}
      {live && error && <Alert severity="warning" sx={{ mt: 1 }}>{error}</Alert>}
      {actionError && <Alert severity="error" sx={{ mt: 1 }}>{actionError}</Alert>}
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
            {contract.pdfAvailable && <Button size="small" aria-label={`Просмотр PDF ${contract.contractNumber}`} onClick={() => setPreview({ title: `${contract.contractNumber} · ${contract.templateName}`, kind: 'pdf', load: () => previewPatientContract(contract) })}>Просмотр</Button>}
            <Button size="small" aria-label={`Скачать DOCX ${contract.contractNumber}`} onClick={() => void download(contract, 'docx')}>DOCX</Button>
            {contract.pdfAvailable && <Button size="small" aria-label={`Скачать PDF ${contract.contractNumber}`} onClick={() => void download(contract, 'pdf')}>PDF</Button>}
            {contract.pdfAvailable && <Button size="small" aria-label={`Печать PDF ${contract.contractNumber}`} onClick={() => void print(contract)}>Печать</Button>}
          </Box>
        </Box>
      ))}
      <FilePreviewDialog source={preview} onClose={() => setPreview(null)} />
      <ContractCreateDialog
        key={`${patientId}-${creating}`}
        open={creating}
        patientId={patientId}
        templates={templates ?? []}
        services={services}
        onClose={() => setCreating(false)}
        onCreated={() => {
          setCreating(false)
          notify('Договор сформирован')
          setRevision((value) => value + 1)
        }}
      />
    </Box>
  )
}
