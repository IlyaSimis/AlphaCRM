import { useEffect, useRef, useState } from 'react'
import { LockOutlined, PictureAsPdfRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material'
import { downloadPatientFile, fileSize, loadPatientFiles, pdfProblem, signPatientFile, uploadPatientFile } from '../../api/files'
import type { PatientFileItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import type { Role } from '../../shared/roles'
import { isUuid } from '../../shared/text'
import { fileRights, fileStateLabels } from './fileRules'
import { SignFileDialog } from './SignFileDialog'

type PatientFilesProps = {
  patientId: string
  role: Role
  apiOnline: boolean
  notify: (message: string) => void
}

/** Блок «Документы PDF» карточки пациента: файлы хранятся на сервере только зашифрованными. */
export function PatientFiles({ patientId, role, apiOnline, notify }: PatientFilesProps) {
  const rights = fileRights(role)
  const live = apiOnline && isUuid(patientId)
  const [items, setItems] = useState<PatientFileItem[] | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)
  const [signing, setSigning] = useState<PatientFileItem | null>(null)
  const [signError, setSignError] = useState('')
  const [revision, setRevision] = useState(0)
  const input = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (!live || !rights.visible) return
    let cancelled = false
    loadPatientFiles(patientId)
      .then((rows) => { if (!cancelled) { setItems(rows); setError('') } })
      .catch((reason: Error) => { if (!cancelled) { setItems(null); setError(reason.message) } })
    return () => { cancelled = true }
  }, [live, rights.visible, patientId, revision])

  if (!rights.visible) return null

  const upload = async (file: File | undefined) => {
    if (!file) return
    setActionError('')
    const problem = await pdfProblem(file)
    if (problem) {
      setActionError(problem)
      return
    }
    setBusy(true)
    try {
      await uploadPatientFile(patientId, file)
      notify('Файл загружен')
      setRevision((value) => value + 1)
    } catch (reason) {
      setActionError((reason as Error).message)
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  const sign = async () => {
    if (!signing) return
    setBusy(true)
    try {
      await signPatientFile(signing.id)
      notify('Файл подписан')
      setSigning(null)
      setRevision((value) => value + 1)
    } catch (reason) {
      setSignError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const download = async (file: PatientFileItem) => {
    setActionError('')
    try {
      await downloadPatientFile(file)
    } catch (reason) {
      setActionError((reason as Error).message)
    }
  }

  return (
    <Box className="panel patient-files" component="section" aria-label="Документы PDF">
      <Box className="panel-title-row">
        <Typography className="panel-label">ДОКУМЕНТЫ PDF</Typography>
        {live && rights.upload && !error && <>
          <input ref={input} type="file" accept="application/pdf,.pdf" hidden aria-label="Файл PDF" onChange={(event) => void upload(event.target.files?.[0])} />
          <Button size="small" variant="outlined" disabled={busy} onClick={() => input.current?.click()}>Загрузить</Button>
        </>}
      </Box>
      {!live && <Alert severity="info" sx={{ mt: 1 }}>PDF хранятся на сервере в зашифрованном виде и доступны только при подключённом API.</Alert>}
      {live && error && <Alert severity="warning" sx={{ mt: 1 }}>{error}</Alert>}
      {actionError && <Alert severity="error" sx={{ mt: 1 }}>{actionError}</Alert>}
      {live && !error && items === null && <CircularProgress size={22} sx={{ mt: 1 }} aria-label="Загрузка файлов" />}
      {live && !error && items?.length === 0 && <Typography sx={{ color: 'text.secondary', mt: 1 }}>{role === 'MANAGER' ? 'Подписанных файлов нет' : 'Файлов нет. Не больше 10 МБ, только PDF.'}</Typography>}
      {items?.map((file) => (
        <Box className="history-item" key={file.id}>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography sx={{ fontWeight: 650, overflowWrap: 'anywhere' }}><PictureAsPdfRounded sx={{ fontSize: 16, verticalAlign: 'middle', mr: 0.5 }} />{file.originalName} · {fileSize(file.sizeBytes)}</Typography>
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Загрузил {file.uploadedByName ?? 'сотрудник'} · {formatClinicMoment(file.uploadedAt)}</Typography>
            {file.state === 'SIGNED_LOCKED' && <Typography sx={{ fontSize: 13, color: 'text.secondary' }}><LockOutlined sx={{ fontSize: 13, verticalAlign: 'middle', mr: 0.5 }} />Подписал {file.signedByName ?? 'сотрудник'} · {formatClinicMoment(file.signedAt)}</Typography>}
          </Box>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" variant="outlined" color={file.state === 'SIGNED_LOCKED' ? 'success' : 'default'} label={fileStateLabels[file.state]} />
            {rights.sign(file) && <Button size="small" aria-label={`Подписать ${file.originalName}`} onClick={() => { setSignError(''); setSigning(file) }}>Подписать</Button>}
            {rights.download && <Button size="small" aria-label={`Скачать ${file.originalName}`} onClick={() => void download(file)}>Скачать</Button>}
          </Box>
        </Box>
      ))}
      {signing && <SignFileDialog file={signing} error={signError} busy={busy} onClose={() => setSigning(null)} onSign={() => void sign()} />}
    </Box>
  )
}
