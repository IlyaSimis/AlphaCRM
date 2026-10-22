import { useEffect, useState } from 'react'
import { amendDocument, createDocument, loadEncounters, saveNote, signDocument, startEncounter, TEMPLATE_VERSION } from '../../api/clinical'
import type { DocumentSummary, EncounterView, NoteFields } from '../../api/types'
import { isUuid } from '../../shared/text'
import { activeDocument, emptyNote, latestSigned, noteComplete, noteOf, upsertDocument, type VisitTarget } from './visit'

const INCOMPLETE = 'Заполните все четыре раздела заметки'

function localEncounter(target: VisitTarget): EncounterView {
  return {
    encounterId: `local-${target.appointmentId ?? target.patientId}`,
    appointmentId: target.appointmentId ?? '',
    patientId: target.patientId,
    practitionerId: target.practitionerId,
    status: 'IN_PROGRESS',
    startedAt: new Date().toISOString(),
    documents: [],
  }
}

/**
 * Состояние приёма: старт по записи, черновик заметки, подпись и дополнение.
 * На сервере — настоящие вызовы; без сервера всё живёт до обновления страницы.
 */
export function useVisit(target: VisitTarget, apiOnline: boolean, notify: (message: string) => void) {
  const remote = apiOnline && isUuid(target.patientId) && (Boolean(target.encounter) || isUuid(target.appointmentId ?? ''))
  const [encounter, setEncounter] = useState<EncounterView | null>(target.encounter ?? (remote ? null : localEncounter(target)))
  const [note, setNote] = useState<NoteFields>(target.encounter ? noteOf(target.encounter) : emptyNote)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [savedAt, setSavedAt] = useState<Date | null>(null)

  useEffect(() => {
    if (!remote || target.encounter || !target.appointmentId) return
    let cancelled = false
    const appointmentId = target.appointmentId
    startEncounter({ appointmentId, patientId: target.patientId, practitionerId: target.practitionerId })
      .then(async (started) => {
        if (cancelled) return
        setEncounter(started)
        try {
          // Текст заметки приходит только из истории — после проверки связи с лечением.
          const full = (await loadEncounters(target.patientId)).find((item) => item.encounterId === started.encounterId)
          if (!cancelled && full) {
            setEncounter(full)
            setNote(noteOf(full))
          }
        } catch (reason) {
          if (!cancelled) setError((reason as Error).message)
        }
      })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [remote, target.encounter, target.appointmentId, target.patientId, target.practitionerId])

  const documents = encounter?.documents ?? []
  const setDocuments = (change: (docs: DocumentSummary[]) => DocumentSummary[]) =>
    setEncounter((current) => (current ? { ...current, documents: change(current.documents) } : current))

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Ошибка')
    } finally {
      setBusy(false)
    }
  }

  const persistNote = async (current: EncounterView) => {
    const saved = await saveNote(current.encounterId, note)
    setEncounter((previous) => (previous ? { ...previous, ...saved, documents: previous.documents } : saved))
    setSavedAt(new Date())
  }

  const save = () => run(async () => {
    if (!encounter) return
    if (!noteComplete(note)) throw new Error(INCOMPLETE)
    if (!remote) {
      setSavedAt(new Date())
      notify('Демо-режим: черновик виден только до обновления страницы')
      return
    }
    await persistNote(encounter)
    notify('Черновик сохранён')
  })

  const sign = () => run(async () => {
    if (!encounter) return
    if (!noteComplete(note)) throw new Error(INCOMPLETE)
    const current = activeDocument(documents)
    if (!remote) {
      const signedAt = new Date().toISOString()
      const draft = current?.state === 'DRAFT' ? current : { id: crypto.randomUUID(), state: 'DRAFT' as const, templateVersion: TEMPLATE_VERSION, signedAt: null, amendsDocumentId: null }
      setDocuments((docs) => upsertDocument(docs, { ...draft, state: 'SIGNED_LOCKED', signedAt }))
      setSavedAt(new Date())
      notify('Демо-режим: документ подписан до обновления страницы')
      return
    }
    // Подписывается ровно то, что в форме: сначала сохраняем заметку, затем подписываем черновик.
    await persistNote(encounter)
    const draft = current?.state === 'DRAFT' ? current : await createDocument(encounter.encounterId)
    const signedDoc = await signDocument(draft.id)
    setDocuments((docs) => upsertDocument(docs, signedDoc))
    notify('Документ подписан и заблокирован')
  })

  const amend = () => run(async () => {
    const original = latestSigned(documents)
    if (!original) return
    if (!remote) {
      setDocuments((docs) => [...docs, { id: crypto.randomUUID(), state: 'DRAFT', templateVersion: TEMPLATE_VERSION, signedAt: null, amendsDocumentId: original.id }])
      notify('Демо-режим: дополнение видно до обновления страницы')
      return
    }
    const amendment = await amendDocument(original.id)
    setDocuments((docs) => upsertDocument(docs, amendment))
    notify('Создан черновик дополнения. Исходная версия остаётся заблокированной')
  })

  return { remote, encounter, documents, note, setNote, error, busy, savedAt, save, sign, amend, loading: remote && !encounter && !error }
}
