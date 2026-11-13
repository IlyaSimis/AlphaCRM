import { useEffect, useState } from 'react'
import { CloseRounded } from '@mui/icons-material'
import { Alert, Box, CircularProgress, Dialog, DialogContent, DialogTitle, IconButton, Typography } from '@mui/material'

export type FilePreviewSource = {
  title: string
  kind: 'pdf' | 'image'
  /** Файл запрашивается через apiFetch: сервер повторно проверяет сессию и права. */
  load: () => Promise<Blob>
}

type FilePreviewDialogProps = {
  source: FilePreviewSource | null
  onClose: () => void
}

/** Встроенный просмотр без публичных ссылок. Blob URL живёт только, пока открыто это окно. */
export function FilePreviewDialog({ source, onClose }: FilePreviewDialogProps) {
  if (!source) return null
  return <PreviewDialog source={source} onClose={onClose} />
}

function PreviewDialog({ source, onClose }: { source: FilePreviewSource; onClose: () => void }) {
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null
    source.load()
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [source])

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xl" aria-label={`Просмотр ${source.title}`} slotProps={{ paper: { sx: { height: { xs: '100dvh', sm: '90dvh' }, maxHeight: '100dvh', m: { xs: 0, sm: 3 } } } }}>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5 }}>
        <Typography component="span" sx={{ flex: 1, minWidth: 0, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{source.title}</Typography>
        <IconButton aria-label="Закрыть просмотр" onClick={onClose}><CloseRounded /></IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ p: source.kind === 'image' ? 2 : 0, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {loading && <Box className="empty" sx={{ flex: 1, minHeight: 240 }}><CircularProgress aria-label="Загрузка предпросмотра" /></Box>}
        {error && <Alert severity="error" sx={{ m: 2 }}>{error}</Alert>}
        {url && source.kind === 'pdf' && <Box component="iframe" src={url} title={`Просмотр: ${source.title}`} sx={{ width: '100%', height: '100%', minHeight: { xs: '70vh', sm: '75vh' }, border: 0, bgcolor: '#525659' }} />}
        {url && source.kind === 'image' && <Box sx={{ flex: 1, minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'auto', bgcolor: 'action.hover', borderRadius: 1 }}>
          <Box component="img" src={url} alt={source.title} sx={{ display: 'block', maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
        </Box>}
      </DialogContent>
    </Dialog>
  )
}
