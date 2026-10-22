import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client'
import {
  consentStatus,
  createPatient as createPatientApi,
  grantConsent as grantConsentApi,
  loadPatients,
  localPatient,
  newConsent,
  rememberPatient,
  revokeConsent as revokeConsentApi,
  updatePatient as updatePatientApi,
  type PatientInput,
} from '../api/patients'
import {
  cancelAppointment,
  changeAppointmentStatus,
  createAppointment,
  demoPractitioners,
  demoServices,
  loadPractitioners,
  loadSchedule,
  loadServices,
  rescheduleAppointment,
  statusLabel,
  withStatus,
} from '../api/schedule'
import type { AppointmentStatus, ChargeItem, PatientItem, Practitioner, ScheduleItem, ServiceItem } from '../api/types'
import { localCharge } from '../features/cashier/cashier'
import { localBooking, validateBooking, type BookingDraft } from '../features/schedule/booking'
import type { RescheduleDraft } from '../features/schedule/RescheduleDialog'
import { availableActions } from '../features/schedule/statusActions'
import { OUTSIDE_WORKING_HOURS, fitsWorkingHours } from '../features/schedule/workingHours'
import { apiRoles, type Role } from '../shared/roles'
import { readStored, writeStored } from '../shared/storage'
import { isUuid } from '../shared/text'
import { clinicDay, demoAppointments, demoPatients } from './demoData'

const scheduleDateKey = 'quiet-clinic-schedule-date'

const parseDate = (value: string | null) => (value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : clinicDay)

function sortSchedule(items: ScheduleItem[]) {
  return [...items].sort((left, right) => left.date.localeCompare(right.date) || left.time.localeCompare(right.time))
}

function mergeDay(current: ScheduleItem[], date: string, incoming: ScheduleItem[]) {
  return sortSchedule([...current.filter((item) => item.date !== date), ...incoming])
}

/** Итог действия из диалога: закрыть, показать ошибку или ничего (ответ устарел). */
export type DialogResult = { status: 'done' } | { status: 'error'; message: string } | { status: 'stale' }

/**
 * Данные клиники и действия над ними: загрузка с сервера, а если он недоступен —
 * демо-режим, где изменения живут только до обновления страницы.
 */
export function useClinicData(doctorId: string) {
  const [scheduleItems, setScheduleItems] = useState(demoAppointments)
  const [patientItems, setPatientItems] = useState(demoPatients)
  const [practitioners, setPractitioners] = useState<Practitioner[]>(demoPractitioners)
  const [services, setServices] = useState<ServiceItem[]>(demoServices)
  const [localCharges, setLocalCharges] = useState<ChargeItem[]>([])
  const [selectedPatientId, setSelectedPatientId] = useState(demoPatients[0].id)
  const [apiOnline, setApiOnline] = useState(false)
  const [scheduleDate, setScheduleDate] = useState(() => readStored(scheduleDateKey, parseDate))
  const [notice, setNotice] = useState({ message: '', open: false })
  // Растёт после каждого изменения записей: карточка пациента и касса по нему перечитывают данные.
  const [revision, setRevision] = useState(0)
  // Номер последней загрузки расписания: ответы устаревших запросов игнорируются.
  const loadGeneration = useRef(0)

  const notify = useCallback((message: string) => setNotice({ message, open: true }), [])
  const closeNotice = useCallback(() => setNotice((current) => ({ ...current, open: false })), [])
  const bump = () => setRevision((value) => value + 1)

  useEffect(() => { writeStored(scheduleDateKey, scheduleDate) }, [scheduleDate])

  useEffect(() => {
    let cancelled = false
    loadPatients()
      .then((remotePatients) => {
        if (cancelled) return
        setPatientItems(remotePatients)
        setSelectedPatientId((current) => remotePatients.some((patient) => patient.id === current) ? current : remotePatients[0]?.id ?? current)
        setApiOnline(true)
      })
      .catch(() => {
        if (!cancelled) setApiOnline(false)
      })
    loadPractitioners()
      .then((remote) => { if (!cancelled) setPractitioners(remote) })
      .catch(() => { /* список врачей по умолчанию остаётся из демо-данных */ })
    loadServices()
      .then((remote) => { if (!cancelled) setServices(remote) })
      .catch(() => { /* справочник услуг по умолчанию совпадает с seed */ })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    if (!apiOnline) return
    const generation = ++loadGeneration.current
    let cancelled = false
    loadSchedule(scheduleDate)
      .then((items) => {
        if (cancelled || generation !== loadGeneration.current) return
        setScheduleItems((current) => mergeDay(current, scheduleDate, items))
      })
      .catch(() => {
        if (!cancelled && generation === loadGeneration.current) setApiOnline(false)
      })
    return () => { cancelled = true }
  }, [apiOnline, scheduleDate])

  const updateLocally = (id: string, change: (item: ScheduleItem) => ScheduleItem) =>
    setScheduleItems((items) => items.map((row) => row.id === id ? change(row) : row))

  const reloadDays = async (dates: string[]) => {
    const days = await Promise.all([...new Set(dates)].map((date) => loadSchedule(date).then((items) => ({ date, items }))))
    setScheduleItems((current) => days.reduce((items, day) => mergeDay(items, day.date, day.items), current))
  }

  /**
   * Общая схема изменения записи: на сервере — запрос и перезагрузка затронутых дней;
   * при ошибке сервера или в демо-режиме — локальное изменение с предупреждением.
   */
  const mutateAppointment = async (item: ScheduleItem & { id: string }, remote: () => Promise<unknown>, local: () => void, messages: { done: string; offline: string; demo: string }, days: string[] = [item.date]): Promise<DialogResult> => {
    if (apiOnline && isUuid(item.id)) {
      const generation = ++loadGeneration.current
      try {
        await remote()
        if (generation !== loadGeneration.current) return { status: 'stale' }
        await reloadDays(days)
        notify(messages.done)
        bump()
        return { status: 'done' }
      } catch (error) {
        if (generation !== loadGeneration.current) return { status: 'stale' }
        if (error instanceof ApiError && error.status < 500) return { status: 'error', message: error.message }
        setApiOnline(false)
        local()
        notify(messages.offline)
        bump()
        return { status: 'done' }
      }
    }
    loadGeneration.current += 1
    local()
    notify(messages.demo)
    bump()
    return { status: 'done' }
  }

  const notifyError = (result: DialogResult) => {
    if (result.status === 'error') notify(result.message)
  }

  const addLocalCharge = (appointment: ScheduleItem) => {
    const charge = localCharge(appointment)
    if (charge) setLocalCharges((charges) => charges.some((item) => item.appointmentId === charge.appointmentId) ? charges : [...charges, charge])
  }

  const payLocalCharge = (chargeId: string) =>
    setLocalCharges((charges) => charges.map((charge) => charge.chargeId === chargeId ? { ...charge, status: 'PAID', paymentStatus: 'CONFIRMED' } : charge))

  const cancelBooking = async (item: ScheduleItem, role: Role) => {
    if (role !== 'Администратор' || item.statusCode === 'CANCELLED' || !item.id) return
    const id = item.id
    notifyError(await mutateAppointment({ ...item, id }, () => cancelAppointment(id), () => updateLocally(id, (row) => withStatus(row, 'CANCELLED')), {
      done: 'Запись отменена',
      offline: 'Сервер недоступен: отмена видна только до обновления страницы',
      demo: 'Демо-режим: отмена видна только до обновления страницы',
    }))
  }

  const changeStatus = async (item: ScheduleItem, to: AppointmentStatus, role: Role) => {
    if (!item.id || !availableActions(item, role, doctorId).some((action) => action.to === to)) return
    const id = item.id
    const label = statusLabel(to)
    const local = () => {
      updateLocally(id, (row) => withStatus(row, to))
      // Как на сервере: завершённый приём получает счёт на снимок цены.
      if (to === 'COMPLETED') addLocalCharge({ ...item, statusCode: to })
    }
    notifyError(await mutateAppointment({ ...item, id }, () => changeAppointmentStatus(id, to, apiRoles[role]), local, {
      done: `Статус: ${label}`,
      offline: 'Сервер недоступен: статус виден только до обновления страницы',
      demo: `Демо-режим: статус «${label}» виден только до обновления страницы`,
    }))
  }

  const reschedule = async (item: ScheduleItem, draft: RescheduleDraft): Promise<DialogResult> => {
    if (!item.id) return { status: 'stale' }
    const practitioner = practitioners.find((doctor) => doctor.id === draft.practitionerId)
    if (!fitsWorkingHours(practitioner, draft.date, draft.time, draft.minutes)) return { status: 'error', message: OUTSIDE_WORKING_HOURS }
    const id = item.id
    const local = () => setScheduleItems((items) => sortSchedule(items.map((row) => row.id === id ? { ...row, date: draft.date, time: draft.time, practitionerId: draft.practitionerId } : row)))
    const result = await mutateAppointment({ ...item, id }, () => rescheduleAppointment(id, draft), local, {
      done: 'Запись перенесена',
      offline: 'Сервер недоступен: перенос виден только до обновления страницы',
      demo: 'Демо-режим: перенос виден только до обновления страницы',
    }, [item.date, draft.date])
    if (result.status === 'done') setScheduleDate(draft.date)
    return result
  }

  const book = async (draft: BookingDraft, role: Role): Promise<DialogResult> => {
    const invalid = validateBooking(draft, patientItems, services, practitioners)
    if (invalid) return { status: 'error', message: invalid }
    const patient = patientItems.find((item) => item.id === draft.patientId) as PatientItem
    const service = services.find((item) => item.id === draft.serviceId) as ServiceItem
    const showLocal = (message: string): DialogResult => {
      loadGeneration.current += 1
      setScheduleDate(draft.date)
      setScheduleItems((items) => sortSchedule([...items, localBooking(draft, patient, service)]))
      notify(message)
      bump()
      return { status: 'done' }
    }
    if (!apiOnline || !isUuid(patient.id)) return showLocal('Демо-режим: запись видна только до обновления страницы')
    const generation = ++loadGeneration.current
    try {
      await createAppointment(
        { patientId: patient.id, patientName: patient.name, practitionerId: draft.practitionerId, date: draft.date, time: draft.time, minutes: service.durationMinutes, serviceName: service.name },
        apiRoles[role] === 'ADMINISTRATOR' ? 'ADMINISTRATOR' : 'DOCTOR',
      )
      const day = await loadSchedule(draft.date)
      if (generation !== loadGeneration.current) return { status: 'stale' }
      setScheduleDate(draft.date)
      setScheduleItems((current) => mergeDay(current, draft.date, day))
      notify('Запись создана')
      bump()
      return { status: 'done' }
    } catch (error) {
      if (generation !== loadGeneration.current) return { status: 'stale' }
      if (error instanceof ApiError && error.status < 500) return { status: 'error', message: error.message }
      setApiOnline(false)
      return showLocal('Сервер недоступен: запись видна только до обновления страницы')
    }
  }

  const createPatient = async (input: PatientInput) => {
    if (apiOnline) {
      try {
        const created = await createPatientApi(input)
        setPatientItems((items) => [created, ...items])
        notify('Пациент сохранён')
        return
      } catch {
        setApiOnline(false)
        setPatientItems((items) => [localPatient(crypto.randomUUID(), input), ...items])
        notify('Сервер недоступен: пациент виден только до обновления страницы')
        return
      }
    }
    setPatientItems((items) => [localPatient(crypto.randomUUID(), input), ...items])
    notify('Демо-режим: пациент виден только до обновления страницы')
  }

  const replacePatient = (updated: PatientItem) => setPatientItems((items) => items.map((item) => item.id === updated.id ? updated : item))

  /** Возвращает текст ошибки для формы или null, если форму можно закрыть. */
  const updatePatient = async (patient: PatientItem, input: PatientInput): Promise<string | null> => {
    const applyLocally = (updated: PatientItem) => {
      replacePatient(updated)
      if (updated.name !== patient.name) {
        rememberPatient(updated.id, updated.name)
        setScheduleItems((items) => items.map((item) => item.name === patient.name ? { ...item, name: updated.name } : item))
      }
    }
    if (apiOnline && isUuid(patient.id)) {
      try {
        applyLocally(await updatePatientApi(patient.id, input))
        notify('Данные пациента обновлены')
      } catch (error) {
        if (error instanceof ApiError && error.status < 500) return error.message
        setApiOnline(false)
        applyLocally(localPatient(patient.id, input, patient.consents))
        notify('Сервер недоступен: изменения видны только до обновления страницы')
      }
      return null
    }
    applyLocally(localPatient(patient.id, input, patient.consents))
    notify('Демо-режим: изменения видны только до обновления страницы')
    return null
  }

  const withConsents = (patient: PatientItem, consents: PatientItem['consents']): PatientItem => ({ ...patient, consents, consent: consentStatus(consents) })

  const grantConsent = async (patient: PatientItem, input: { version: string; basis: string }): Promise<string | null> => {
    if (apiOnline && isUuid(patient.id)) {
      try {
        replacePatient(await grantConsentApi(patient.id, input))
        notify('Согласие обновлено')
        return null
      } catch (error) {
        return (error as Error).message
      }
    }
    replacePatient(withConsents(patient, [...patient.consents, newConsent(input.version, input.basis)]))
    notify('Демо-режим: согласие видно только до обновления страницы')
    return null
  }

  const revokeConsent = async (patient: PatientItem, consentId: string) => {
    if (apiOnline && isUuid(patient.id)) {
      try {
        replacePatient(await revokeConsentApi(patient.id, consentId))
        notify('Согласие отозвано')
      } catch (error) {
        notify((error as Error).message)
      }
      return
    }
    replacePatient(withConsents(patient, patient.consents.map((consent) => consent.id === consentId ? { ...consent, revokedAt: new Date().toISOString() } : consent)))
    notify('Демо-режим: отзыв согласия виден только до обновления страницы')
  }

  return {
    apiOnline,
    scheduleItems,
    patientItems,
    practitioners,
    services,
    localCharges,
    revision,
    selectedPatientId,
    setSelectedPatientId,
    scheduleDate,
    setScheduleDate,
    notice,
    notify,
    closeNotice,
    cancelBooking,
    changeStatus,
    reschedule,
    book,
    createPatient,
    updatePatient,
    grantConsent,
    revokeConsent,
    addLocalCharge,
    payLocalCharge,
  }
}
