import { useState } from 'react'
import { LockOutlined } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Divider, Stack, TextField, Typography } from '@mui/material'
import { TEMPLATE_VERSION } from '../../api/clinical'
import type { DocumentSummary, NoteFields } from '../../api/types'
import { formatDay } from '../../shared/dates'
import { FormSection, Info } from '../../shared/ui'
import { SignDialog } from './SignDialog'
import { useVisit } from './useVisit'
import { activeDocument, isLocked, latestSigned, noteComplete, noteSections, visitTimeLabel, type VisitTarget } from './visit'

type VisitProps = {
  target: VisitTarget
  doctorName: string
  apiOnline: boolean
  notify: (message: string) => void
  onBack: () => void
}

function formatMoment(iso?: string | null) {
  if (!iso) return '—'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function versionLabel(doc: DocumentSummary) {
  const kind = doc.amendsDocumentId ? 'Дополнение' : 'Документ приёма'
  return doc.state === 'SIGNED_LOCKED' ? `${kind} · подписан ${formatMoment(doc.signedAt)}` : `${kind} · черновик`
}

/** Приём конкретной записи: пациент, время и услуга берутся из строки расписания или истории. */
export function Visit({ target, doctorName, apiOnline, notify, onBack }: VisitProps) {
  const visit = useVisit(target, apiOnline, notify)
  const [signDialog, setSignDialog] = useState(false)
  const locked = isLocked(visit.documents)
  const current = activeDocument(visit.documents)
  const signed = latestSigned(visit.documents)
  const editingAmendment = current?.state === 'DRAFT' && Boolean(current.amendsDocumentId)
  const chip = locked ? 'Подписан · заблокирован' : editingAmendment ? 'Дополнение · черновик' : 'Черновик'
  const setField = (key: keyof NoteFields, value: string) => visit.setNote((note) => ({ ...note, [key]: value }))
  const eyebrow = [target.serviceName ?? 'Приём', target.date ? formatDay(target.date) : null, visitTimeLabel(target) || null].filter(Boolean).join(' · ')

  return (
    <>
      <Button onClick={onBack} sx={{ mb: 2 }}>← Назад</Button>
      <Box className="visit-banner">
        <Box><Typography className="eyebrow">{eyebrow.toLocaleUpperCase('ru-RU')}</Typography><Typography variant="h4" sx={{ fontWeight: 750 }}>{target.patientName}</Typography><Typography sx={{ color: 'text.secondary' }}>{visit.remote ? 'Данные приёма на сервере' : 'Демо-режим: приём виден до обновления страницы'}</Typography></Box>
        <Chip icon={locked ? <LockOutlined /> : undefined} label={chip} color={locked ? 'success' : 'warning'} variant="outlined" />
      </Box>
      {visit.error && <Alert severity="error" sx={{ mb: 2 }}>{visit.error}</Alert>}
      {visit.loading && <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress aria-label="Загрузка приёма" /></Box>}
      {locked && signed && <Alert severity="success" icon={<LockOutlined />} sx={{ mb: 2 }}>Документ подписан {formatMoment(signed.signedAt)} врачом {doctorName}. Изменение исходной версии недоступно — исправления оформляются дополнением.</Alert>}
      {visit.encounter && <Box className="visit-layout">
        <Box className="clinical-form panel">
          {noteSections.map((section) => (
            <FormSection key={section.key} number={section.number} title={section.title}>
              <TextField multiline minRows={section.rows} fullWidth value={visit.note[section.key]} onChange={(event) => setField(section.key, event.target.value)} disabled={locked || visit.busy} slotProps={{ htmlInput: { 'aria-label': section.title } }} helperText={section.key === 'diagnosis' ? 'Свободный текст — кодирование по МКБ в MVP не требуется' : undefined} />
            </FormSection>
          ))}
        </Box>
        <Box>
          <Box className="panel sticky-summary">
            <Typography sx={{ fontWeight: 750 }}>Документ приёма</Typography>
            <Info label="Шаблон" value={`Первичный осмотр · ${TEMPLATE_VERSION}`} />
            <Info label="Автор" value={doctorName} />
            <Info label="Последнее сохранение" value={visit.savedAt ? visit.savedAt.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }) : '—'} />
            {visit.documents.length > 0 && <>
              <Typography sx={{ fontSize: 13, fontWeight: 700, mt: 1 }}>Версии</Typography>
              {visit.documents.map((doc) => <Typography key={doc.id} sx={{ fontSize: 13, color: 'text.secondary' }}>{versionLabel(doc)}</Typography>)}
            </>}
            <Divider sx={{ mt: 1 }} />
            <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 2 }}>Подписание зафиксирует ровно показанную версию и контрольный хеш.</Typography>
            {!locked
              ? <Stack spacing={1.2} sx={{ mt: 2 }}>
                <Button variant="outlined" disabled={visit.busy} onClick={visit.save}>Сохранить черновик</Button>
                <Button variant="contained" startIcon={<LockOutlined />} disabled={visit.busy} onClick={() => { if (noteComplete(visit.note)) setSignDialog(true); else visit.save() }}>Проверить и подписать</Button>
              </Stack>
              : <Button fullWidth variant="outlined" sx={{ mt: 2 }} disabled={visit.busy} onClick={visit.amend}>Создать дополнение</Button>}
          </Box>
        </Box>
      </Box>}
      <SignDialog open={signDialog} onClose={() => setSignDialog(false)} onConfirm={async () => {
        setSignDialog(false)
        await visit.sign()
      }} />
    </>
  )
}
