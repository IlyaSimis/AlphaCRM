import { useEffect, useRef, useState } from 'react'
import { LockOutlined, PictureAsPdfRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material'
import { downloadPatientFile, fileSize, loadPatientFiles, pdfProblem, previewPatientFile, printPatientFile, signPatientFile, uploadPatientFile } from '../../api/files'
import type { PatientFileItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import type { Role } from '../../shared/roles'
import { isUuid } from '../../shared/text'
import { FilePreviewDialog, type FilePreviewSource } from './FilePreviewDialog'
import { fileRights, fileStateLabels } from './fileRules'
import { SignFileDialog } from './SignFileDialog'

type PatientFilesProps = {
  patientId: string
  /** Страница приёма: загрузка привязывается к этой записи, в списке — только её файлы. */
  appointmentId?: string
  role: Role
  apiOnline: boolean
  notify: (message: string) => void
}

/**
 * Блок «Документы PDF» карточки пациента или «Заключения и файлы приёма» на странице приёма.
 * Файлы хранятся на сервере только зашифрованными.
 */
export function PatientFiles({ patientId, appointmentId, role, apiOnline, notify }: PatientFilesProps) {
  const rights = fileRights(role)
  const visit = Boolean(appointmentId)
  const title = visit ? 'Заключения и файлы приёма' : 'Документы PDF'
  const live = apiOnline && isUuid(patientId) && (!appointmentId || isUuid(appointmentId))
  const [items, setItems] = useState<PatientFileItem[] | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)
  const [signing, setSigning] = useState<PatientFileItem | null>(null)
  const [signError, setSignError] = useState('')
  const [preview, setPreview] = useState<FilePreviewSource | null>(null)
  const [revision, setRevision] = useState(0)
  const input = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (!live || !rights.visible) return
    let cancelled = false
    loadPatientFiles(patientId)
      .then((rows) => { if (!cancelled) { setItems(appointmentId ? rows.filter((row) => row.appointmentId === appointmentId) : rows); setError('') } })
      .catch((reason: Error) => { if (!cancelled) { setItems(null); setError(reason.message) } })
    return () => { cancelled = true }
  }, [live, rights.visible, patientId, appointmentId, revision])

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
      await uploadPatientFile(patientId, file, appointmentId)
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

  const print = (file: PatientFileItem) => {
    setActionError('')
    if (!printPatientFile(file)) setActionError('Браузер не открыл новую вкладку: разрешите всплывающие окна для этого сайта')
  }

  return (
    <Box className="panel patient-files" component="section" aria-label={title}>
      <Box className="panel-title-row">
        <Typography className="panel-label">{title.toLocaleUpperCase('ru-RU')}</Typography>
        {live && rights.upload && !error && <>
          <input ref={input} type="file" accept="application/pdf,.pdf" hidden aria-label="Файл PDF" onChange={(event) => void upload(event.target.files?.[0])} />
          <Button size="small" variant={visit ? 'contained' : 'outlined'} disabled={busy} onClick={() => input.current?.click()}>{visit ? 'Загрузить заключение' : 'Загрузить'}</Button>
        </>}
      </Box>
      {visit && live && !error && <Typography sx={{ fontSize: 13, color: 'text.secondary', mt: 0.5 }}>PDF до 10 МБ привязывается к этому приёму. Все файлы пациента — в его карточке.</Typography>}
      {!live && <Alert severity="info" sx={{ mt: 1 }}>PDF хранятся на сервере в зашифрованном виде и доступны только при подключённом API.</Alert>}
      {live && error && <Alert severity="warning" sx={{ mt: 1 }}>{error}</Alert>}
      {actionError && <Alert severity="error" sx={{ mt: 1 }}>{actionError}</Alert>}
      {live && !error && items === null && <CircularProgress size={22} sx={{ mt: 1 }} aria-label="Загрузка файлов" />}
      {live && !error && items?.length === 0 && <Typography sx={{ color: 'text.secondary', mt: 1 }}>{visit ? 'К этому приёму файлов нет.' : rights.signedOnly ? 'Подписанных файлов нет' : 'Файлов нет. Не больше 10 МБ, только PDF.'}</Typography>}
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
            {rights.download && <Button size="small" aria-label={`Просмотр ${file.originalName}`} onClick={() => setPreview({ title: file.originalName, kind: 'pdf', load: () => previewPatientFile(file) })}>Просмотр</Button>}
            {rights.download && <Button size="small" aria-label={`Печать ${file.originalName}`} onClick={() => print(file)}>Печать</Button>}
            {rights.download && <Button size="small" aria-label={`Скачать ${file.originalName}`} onClick={() => void download(file)}>Скачать</Button>}
          </Box>
        </Box>
      ))}
      {signing && <SignFileDialog file={signing} error={signError} busy={busy} onClose={() => setSigning(null)} onSign={() => void sign()} />}
      <FilePreviewDialog source={preview} onClose={() => setPreview(null)} />
    </Box>
  )
}
