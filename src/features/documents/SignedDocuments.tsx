import { useEffect, useState } from 'react'
import { ArticleRounded, LockOutlined } from '@mui/icons-material'
import { Alert, Box, Button, CircularProgress, MenuItem, TextField, Typography } from '@mui/material'
import { loadSignedDocument, loadSignedDocuments } from '../../api/clinical'
import type { Practitioner, SignedDocumentItem } from '../../api/types'
import type { Role } from '../../shared/roles'
import { SignedPdfList } from '../files/SignedPdfList'
import { SignedDocumentDialog } from './SignedDocumentDialog'
import { documentKind, preview, signedMoment } from './signedDocuments'

type SignedDocumentsProps = {
  role: Role
  apiOnline: boolean
  practitioners: Practitioner[]
}

/**
 * Архив подписанных документов, только чтение. Врач видит то, что подписал сам, менеджер — все
 * документы клиники и может выбрать врача. Черновиков здесь нет; текст — из снимка подписи.
 */
export function SignedDocuments({ role, apiOnline, practitioners }: SignedDocumentsProps) {
  const [doctor, setDoctor] = useState('all')
  const [items, setItems] = useState<SignedDocumentItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [open, setOpen] = useState<SignedDocumentItem | null>(null)
  const [openError, setOpenError] = useState('')
  const manager = role === 'MANAGER'

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    setLoading(true)
    setError('')
    loadSignedDocuments(manager && doctor !== 'all' ? doctor : undefined)
      .then((rows) => { if (!cancelled) setItems(rows) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [apiOnline, manager, doctor])

  /** Карточка читается отдельным запросом: сервер фиксирует факт чтения в журнале. */
  const openDocument = async (id: string) => {
    setOpenError('')
    try {
      setOpen(await loadSignedDocument(id))
    } catch (reason) {
      setOpenError((reason as Error).message)
    }
  }

  return (
    <>
      <Box className="section-intro">
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750 }}>Подписанные документы</Typography>
          <Typography sx={{ color: 'text.secondary' }}>{manager ? 'Все подписанные документы клиники, включая дополнения. Только чтение.' : 'Документы, которые вы подписали, включая дополнения. Только чтение.'}</Typography>
        </Box>
        {manager && <TextField select size="small" label="Врач" value={doctor} onChange={(event) => setDoctor(event.target.value)} sx={{ minWidth: 220 }}>
          <MenuItem value="all">Все врачи</MenuItem>
          {practitioners.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName}</MenuItem>)}
        </TextField>}
      </Box>
      {!apiOnline && <Alert severity="info">Подписанные документы хранятся на сервере и доступны только при подключённом API.</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      {openError && <Alert severity="error" sx={{ mb: 2 }}>{openError}</Alert>}
      {apiOnline && !error && (
        <Box className="panel">
          {loading && items.length === 0 ? <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress size={28} aria-label="Загрузка документов" /></Box> : items.length === 0 ? (
            <Box className="empty" sx={{ minHeight: 160 }}><ArticleRounded /><Typography sx={{ fontWeight: 700 }}>Подписанных документов нет</Typography></Box>
          ) : items.map((doc) => (
            <Box className="history-item signed-document" key={doc.id}>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography sx={{ fontWeight: 700 }}><LockOutlined sx={{ fontSize: 15, verticalAlign: 'middle', mr: 0.5 }} />{doc.patientName ?? 'Пациент'} · {signedMoment(doc.signedAt)}</Typography>
                <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{documentKind(doc, items)} · {doc.practitionerName ?? 'Врач'} · шаблон {doc.templateVersion}</Typography>
                <Typography sx={{ fontSize: 13 }}><b>Диагноз:</b> {preview(doc.diagnosis)}</Typography>
                <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Жалобы: {preview(doc.complaints, 60)} · Статус: {preview(doc.mentalStatus, 60)} · Рекомендации: {preview(doc.recommendations, 60)}</Typography>
              </Box>
              <Button size="small" aria-label={`Открыть документ ${doc.patientName ?? ''} ${signedMoment(doc.signedAt)}`} onClick={() => void openDocument(doc.id)}>Открыть</Button>
            </Box>
          ))}
        </Box>
      )}
      <SignedPdfList role={role} apiOnline={apiOnline} />
      {open && <SignedDocumentDialog document={open} all={items} onOpen={(id) => void openDocument(id)} onClose={() => setOpen(null)} />}
    </>
  )
}
