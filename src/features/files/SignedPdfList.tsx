import { useEffect, useState } from 'react'
import { LockOutlined } from '@mui/icons-material'
import { Alert, Box, Button, Typography } from '@mui/material'
import { downloadPatientFile, fileSize, loadSignedFiles } from '../../api/files'
import type { PatientFileItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import type { Role } from '../../shared/roles'

type SignedPdfListProps = { role: Role; apiOnline: boolean }

/** Подписанные PDF в архиве: врач видит подписанные им самим, менеджер — все файлы клиники. */
export function SignedPdfList({ role, apiOnline }: SignedPdfListProps) {
  const [items, setItems] = useState<PatientFileItem[]>([])
  const [error, setError] = useState('')
  const manager = role === 'MANAGER'

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    loadSignedFiles()
      .then((rows) => { if (!cancelled) { setItems(rows); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline])

  if (!apiOnline) return null

  const download = async (file: PatientFileItem) => {
    setError('')
    try {
      await downloadPatientFile(file)
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  return (
    <Box className="panel" component="section" aria-label="Подписанные PDF" sx={{ mt: 2 }}>
      <Typography className="panel-label">ПОДПИСАННЫЕ PDF</Typography>
      <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 1 }}>{manager ? 'Все подписанные файлы клиники.' : 'Файлы, которые вы подписали.'}</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {!error && items.length === 0 && <Typography sx={{ color: 'text.secondary' }}>Подписанных PDF нет</Typography>}
      {items.map((file) => (
        <Box className="history-item" key={file.id}>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}><LockOutlined sx={{ fontSize: 15, verticalAlign: 'middle', mr: 0.5 }} />{file.patientName ?? 'Пациент'} · {file.originalName}</Typography>
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Подписал {file.signedByName ?? 'сотрудник'} · {formatClinicMoment(file.signedAt)} · {fileSize(file.sizeBytes)}</Typography>
          </Box>
          <Button size="small" aria-label={`Скачать ${file.originalName}`} onClick={() => void download(file)}>Скачать</Button>
        </Box>
      ))}
    </Box>
  )
}
