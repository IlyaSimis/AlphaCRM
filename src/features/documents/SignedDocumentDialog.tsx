import { LockOutlined } from '@mui/icons-material'
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import type { SignedDocumentItem } from '../../api/types'
import { Info } from '../../shared/ui'
import { documentKind, signedMoment, snapshotFields } from './signedDocumentRules'

type SignedDocumentDialogProps = {
  document: SignedDocumentItem
  all: SignedDocumentItem[]
  onOpen: (id: string) => void
  onClose: () => void
}

/** Карточка подписанного документа: только чтение, текст — из снимка подписи. */
export function SignedDocumentDialog({ document, all, onOpen, onClose }: SignedDocumentDialogProps) {
  return <Dialog open onClose={onClose} maxWidth="md" fullWidth>
    <DialogTitle sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
      {documentKind(document, all)}
      <Chip size="small" icon={<LockOutlined />} label="Подписан · заблокирован" color="success" variant="outlined" />
    </DialogTitle>
    <DialogContent>
      <Box className="patient-columns">
        <Box>
          <Info label="Пациент" value={document.patientName ?? '—'} />
          <Info label="Врач" value={document.practitionerName ?? '—'} />
          <Info label="Подписан" value={signedMoment(document.signedAt)} />
          <Info label="Шаблон" value={document.templateVersion} />
          {document.amendsDocumentId && <Box className="info-row">
            <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>Исходный документ</Typography>
            {all.some((item) => item.id === document.amendsDocumentId)
              ? <Button size="small" sx={{ px: 0 }} onClick={() => onOpen(document.amendsDocumentId as string)}>Открыть исходный документ</Button>
              : <Typography sx={{ fontWeight: 600 }}>№ {document.amendsDocumentId.slice(-4)}</Typography>}
          </Box>}
        </Box>
        <Box>
          {snapshotFields.map((field) => (
            <Box key={field.key} className="info-row">
              <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>{field.label}</Typography>
              <Typography sx={{ whiteSpace: 'pre-wrap' }}>{document[field.key] || '—'}</Typography>
            </Box>
          ))}
        </Box>
      </Box>
      <Alert severity="info" sx={{ mt: 2 }}>Текст из подписанной версии. Изменить или подписать документ отсюда нельзя.</Alert>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Закрыть</Button></DialogActions>
  </Dialog>
}
