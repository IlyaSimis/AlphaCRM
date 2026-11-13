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
  updateRegistry as updateRegistryApi,
  type PatientInput,
} from '../api/patients'
import {
  cancelAppointment,
  changeAppointmentStatus,
  createAppointment,
  createService,
  demoPractitioners,
  demoServices,
  loadAppointments,
  loadPractitioners,
  loadSchedule,
  loadServices,
  rescheduleAppointment,
  savePractitionerCard,
  saveServicePricing,
  type ServicePricing,
  saveWorkingHours,
  setServiceActive,
  statusLabel,
  withStatus,
} from '../api/schedule'
import type { AppointmentStatus, AssignedStaff, ChargeItem, PatientItem, Practitioner, RegistryInput, ScheduleItem, ServiceItem } from '../api/types'
import { localCharge } from '../features/cashier/cashierRules'
import { estimateOf, localBooking, validateBooking, type BookingDraft } from '../features/schedule/booking'
import type { RescheduleDraft } from '../features/schedule/RescheduleDialog'
import type { ScheduleView } from '../features/schedule/scheduleView'
import { availableActions } from '../features/schedule/statusActions'
import { OUTSIDE_WORKING_HOURS, fitsWorkingHours } from '../features/schedule/workingHours'
import { can } from '../shared/permissions'
import type { Role } from '../shared/roles'
import { localToday, weekOf } from '../shared/dates'
import { useSandboxState } from '../shared/sandbox'
import { readStored, storageKeys, writeStored } from '../shared/storage'
import { isUuid } from '../shared/text'
import { clinicDay, demoAppointments, demoPatients } from './demoData'

/** Новая услуга: фиксированная цена или свободная с пределами. */
export type NewService = { name: string; durationMinutes: number } & ({ priceMode?: 'FIXED'; priceMinor: number } | { priceMode: 'OPEN'; minMinor: number; maxMinor: number })

const scheduleDateKey = storageKeys.scheduleDate

/** Сохранённый день расписания; без него — сегодня (в демо-песочнице — день демо-данных). */
const parseDate = (fallback: string) => (value: string | null) => (value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : fallback)

function sortSchedule(items: ScheduleItem[]) {
  return [...items].sort((left, right) => left.date.localeCompare(right.date) || left.time.localeCompare(right.time))
}

function mergeDay(current: ScheduleItem[], date: string, incoming: ScheduleItem[]) {
  return sortSchedule([...current.filter((item) => item.date !== date), ...incoming])
}

/** Заменяет записи за период from–to включительно: так неделя приходит одним запросом. */
function mergeRange(current: ScheduleItem[], from: string, to: string, incoming: ScheduleItem[]) {
  return sortSchedule([...current.filter((item) => item.date < from || item.date > to), ...incoming])
}

/** Итог действия из диалога: закрыть (с id созданной записи), показать ошибку или ничего (ответ устарел). */
export type DialogResult = { status: 'done'; id?: string } | { status: 'error'; message: string } | { status: 'stale' }

/**
 * Данные клиники и действия над ними. live — вошли на сервер: данные приходят с API.
 * Иначе демо-песочница: запросов нет, изменения живут только до обновления страницы.
 */
export function useClinicData(doctorId: string, live: boolean) {
  const sandbox = !live
  const [scheduleItems, setScheduleItems] = useSandboxState<ScheduleItem[]>('schedule', demoAppointments, sandbox)
  const [patientItems, setPatientItems] = useSandboxState<PatientItem[]>('patients', demoPatients, sandbox)
  const [practitioners, setPractitioners] = useSandboxState<Practitioner[]>('practitioners', demoPractitioners, sandbox)
  // На живом API справочник услуг — только с сервера; демо-услуги — у песочницы.
  const [services, setServices] = useSandboxState<ServiceItem[]>('services', live ? [] : demoServices, sandbox)
  const [localCharges, setLocalCharges] = useSandboxState<ChargeItem[]>('charges', [], sandbox)
  const [selectedPatientId, setSelectedPatientId] = useState(demoPatients[0].id)
  const [apiOnline, setApiOnline] = useState(false)
  const [scheduleDate, setScheduleDate] = useState(() => readStored(scheduleDateKey, parseDate(live ? localToday() : clinicDay)))
  const [scheduleView, setScheduleView] = useState<ScheduleView>('day')
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
    if (!live) return
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
      .catch(() => { /* справочник недоступен: запись покажет, что услуг нет */ })
    return () => { cancelled = true }
  }, [live, setPatientItems, setPractitioners, setServices])

  useEffect(() => {
    if (!apiOnline) return
    const generation = ++loadGeneration.current
    let cancelled = false
    const week = weekOf(scheduleDate)
    const request = scheduleView === 'week'
      ? loadAppointments({ from: week[0], to: week[6] }).then((items) => (current: ScheduleItem[]) => mergeRange(current, week[0], week[6], items))
      : loadSchedule(scheduleDate).then((items) => (current: ScheduleItem[]) => mergeDay(current, scheduleDate, items))
    request
      .then((apply) => {
        if (cancelled || generation !== loadGeneration.current) return
        setScheduleItems(apply)
      })
      .catch(() => {
        if (!cancelled && generation === loadGeneration.current) setApiOnline(false)
      })
    return () => { cancelled = true }
  }, [apiOnline, scheduleDate, scheduleView, setScheduleItems])

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

  /** Демо-режим: сумма свободной цены — счёт становится обычным, к оплате. */
  const setLocalChargeAmount = (chargeId: string, amountMinor: number) =>
    setLocalCharges((charges) => charges.map((charge) => charge.chargeId === chargeId ? { ...charge, amountMinor, status: 'PENDING' } : charge))

  /** Отменяют записи сотрудники с schedule.appointment.write; врач — только свои. */
  const mayChange = (item: ScheduleItem, role: Role) => can(role, 'schedule.appointment.write') || (can(role, 'schedule.appointment.write-own') && item.practitionerId === doctorId)

  const cancelBooking = async (item: ScheduleItem, role: Role) => {
    if (!mayChange(item, role) || item.statusCode === 'CANCELLED' || !item.id) return
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
    notifyError(await mutateAppointment({ ...item, id }, () => changeAppointmentStatus(id, to), local, {
      done: `Статус: ${label}`,
      offline: 'Сервер недоступен: статус виден только до обновления страницы',
      demo: `Демо-режим: статус «${label}» виден только до обновления страницы`,
    }))
  }

  /**
   * Врач начал приём по записи. На сервере статус ставит сам старт приёма — перечитываем день;
   * в песочнице запись до приёма сразу становится «На приёме». Другие записи не трогаются.
   */
  const visitStarted = async (item: ScheduleItem) => {
    if (!item.id) return
    const id = item.id
    if (apiOnline && isUuid(id)) {
      try {
        await reloadDays([item.date])
        bump()
      } catch {
        // расписание обновится при следующей загрузке дня
      }
      return
    }
    if (item.statusCode === 'BOOKED' || item.statusCode === 'CONFIRMED' || item.statusCode === 'ARRIVED') {
      updateLocally(id, (row) => withStatus(row, 'IN_PROGRESS'))
      bump()
    }
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

  const book = async (draft: BookingDraft, patientOverride?: PatientItem): Promise<DialogResult> => {
    // Только что созданный пациент уже есть в ответе API, но ещё не успел попасть в React state.
    const patients = patientOverride ? [patientOverride, ...patientItems.filter((item) => item.id !== patientOverride.id)] : patientItems
    const invalid = validateBooking(draft, patients, services, practitioners, apiOnline ? new Date() : undefined)
    if (invalid) return { status: 'error', message: invalid }
    const patient = patients.find((item) => item.id === draft.patientId) as PatientItem
    const service = services.find((item) => item.id === draft.serviceId) as ServiceItem
    const showLocal = (message: string): DialogResult => {
      loadGeneration.current += 1
      const created = localBooking(draft, patient, service)
      setScheduleDate(draft.date)
      setScheduleItems((items) => sortSchedule([...items, created]))
      notify(message)
      bump()
      return { status: 'done', id: created.id }
    }
    if (!apiOnline || !isUuid(patient.id)) return showLocal('Демо-режим: запись видна только до обновления страницы')
    const generation = ++loadGeneration.current
    try {
      const created = await createAppointment(
        { patientId: patient.id, patientName: patient.name, practitionerId: draft.practitionerId, date: draft.date, time: draft.time, minutes: service.durationMinutes, serviceName: service.name, estimateMinor: service.priceMode === 'OPEN' ? estimateOf(draft) : null },
      )
      const day = await loadSchedule(draft.date)
      if (generation !== loadGeneration.current) return { status: 'stale' }
      setScheduleDate(draft.date)
      setScheduleItems((current) => mergeDay(current, draft.date, day))
      notify('Запись создана')
      bump()
      return { status: 'done', id: created?.id as string | undefined }
    } catch (error) {
      if (generation !== loadGeneration.current) return { status: 'stale' }
      if (error instanceof ApiError && error.status < 500) return { status: 'error', message: error.message }
      setApiOnline(false)
      return showLocal('Сервер недоступен: запись видна только до обновления страницы')
    }
  }

  /** Возвращает текст ошибки для формы или null. onCreated нужен связанному сценарию записи. */
  const createPatient = async (input: PatientInput, assigned: AssignedStaff | null = null, onCreated?: (patient: PatientItem) => void): Promise<string | null> => {
    const local = () => localPatient(crypto.randomUUID(), input, undefined, assigned)
    if (apiOnline) {
      try {
        const created = await createPatientApi(input)
        setPatientItems((items) => [created, ...items])
        onCreated?.(created)
        notify('Пациент сохранён')
        return null
      } catch (error) {
        if (error instanceof ApiError && error.status < 500) return error.message
        setApiOnline(false)
        const created = local()
        setPatientItems((items) => [created, ...items])
        onCreated?.(created)
        notify('Сервер недоступен: пациент виден только до обновления страницы')
        return null
      }
    }
    const created = local()
    setPatientItems((items) => [created, ...items])
    onCreated?.(created)
    notify('Демо-режим: пациент виден только до обновления страницы')
    return null
  }

  const replacePatient = (updated: PatientItem) => setPatientItems((items) => items.map((item) => item.id === updated.id ? updated : item))

  const refreshPatients = async () => {
    if (!apiOnline) return
    setPatientItems(await loadPatients())
    bump()
  }

  /** Возвращает текст ошибки для формы или null, если форму можно закрыть. */
  const updatePatient = async (patient: PatientItem, input: PatientInput, assigned: AssignedStaff | null = null): Promise<string | null> => {
    const local = (): PatientItem => ({ ...patient, ...localPatient(patient.id, input, patient.consents, assigned) })
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
        applyLocally(local())
        notify('Сервер недоступен: изменения видны только до обновления страницы')
      }
      return null
    }
    applyLocally(local())
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

  /** Недельный график врача: на сервере — PUT целиком, в песочнице — локально. */
  const saveHours = async (practitioner: Practitioner, shifts: Practitioner['workingHours']): Promise<string | null> => {
    const replace = (updated: Practitioner) => setPractitioners((items) => items.map((item) => item.id === updated.id ? updated : item))
    if (apiOnline && isUuid(practitioner.id)) {
      try {
        replace(await saveWorkingHours(practitioner.id, shifts))
        notify('График сохранён')
        return null
      } catch (error) {
        return (error as Error).message
      }
    }
    replace({ ...practitioner, workingHours: shifts })
    notify('Демо-режим: график виден только до обновления страницы')
    return null
  }

  /** Специальность и кабинет врача. Возвращает текст ошибки или null. */
  const saveCard = async (practitioner: Practitioner, input: { specialty: string; room: string }): Promise<string | null> => {
    const replace = (updated: Practitioner) => setPractitioners((items) => items.map((item) => item.id === updated.id ? updated : item))
    if (apiOnline && isUuid(practitioner.id)) {
      try {
        replace(await savePractitionerCard(practitioner.id, input))
        notify('Карточка врача сохранена')
        return null
      } catch (error) {
        return (error as Error).message
      }
    }
    replace({ ...practitioner, specialty: input.specialty, room: input.room || null })
    notify('Демо-режим: карточка видна только до обновления страницы')
    return null
  }

  /** Новая карточка врача из «Сотрудников»: на сервере — перечитать справочник, в песочнице — добавить. */
  const practitionerAdded = (local?: Practitioner) => {
    if (local) {
      setPractitioners((items) => [...items, local].sort((a, b) => a.fullName.localeCompare(b.fullName, 'ru')))
      return
    }
    loadPractitioners()
      .then(setPractitioners)
      .catch(() => notify('Не удалось обновить список врачей'))
  }

  /**
   * Новая цена или режим цены — только для новых записей: снимок цены в уже созданных записях не
   * трогаем. Число — фиксированная цена (как раньше), объект — режим со своей суммой или пределами.
   */
  const savePrice = async (service: ServiceItem, price: number | ServicePricing): Promise<string | null> => {
    const pricing: ServicePricing = typeof price === 'number' ? { priceMode: 'FIXED', priceMinor: price } : price
    const replace = (updated: ServiceItem) => setServices((items) => items.map((item) => item.id === updated.id ? updated : item))
    if (apiOnline && isUuid(service.id)) {
      try {
        replace(await saveServicePricing(service.id, pricing))
        notify('Цена сохранена')
        return null
      } catch (error) {
        return (error as Error).message
      }
    }
    replace(pricing.priceMode === 'FIXED'
      ? { ...service, priceMode: 'FIXED', priceMinor: pricing.priceMinor, minMinor: null, maxMinor: null }
      : { ...service, priceMode: 'OPEN', priceMinor: null, minMinor: pricing.minMinor, maxMinor: pricing.maxMinor })
    notify('Демо-режим: цена видна только до обновления страницы')
    return null
  }

  /** Новая услуга сразу доступна в записи на приём. Возвращает текст ошибки или null. */
  const addService = async (input: NewService): Promise<string | null> => {
    const name = input.name.trim()
    if (apiOnline) {
      try {
        const created = await createService({ ...input, name })
        setServices((items) => [...items, created])
        notify('Услуга добавлена')
        return null
      } catch (error) {
        return (error as Error).message
      }
    }
    if (services.some((item) => item.name.toLowerCase() === name.toLowerCase())) return 'Услуга с таким названием уже есть'
    const pricing = input.priceMode === 'OPEN'
      ? { priceMode: 'OPEN' as const, priceMinor: null, minMinor: input.minMinor, maxMinor: input.maxMinor }
      : { priceMode: 'FIXED' as const, priceMinor: input.priceMinor, minMinor: null, maxMinor: null }
    setServices((items) => [...items, { id: crypto.randomUUID(), name, durationMinutes: input.durationMinutes, currency: 'RUB', active: true, ...pricing }])
    notify('Демо-режим: услуга видна только до обновления страницы')
    return null
  }

  /** Выключенная услуга не предлагается в новой записи; старые записи хранят свой снимок. */
  const toggleService = async (service: ServiceItem): Promise<string | null> => {
    const active = !service.active
    const replace = (updated: ServiceItem) => setServices((items) => items.map((item) => item.id === updated.id ? updated : item))
    if (apiOnline && isUuid(service.id)) {
      try {
        replace(await setServiceActive(service.id, active))
        notify(active ? 'Услуга включена' : 'Услуга выключена')
        return null
      } catch (error) {
        return (error as Error).message
      }
    }
    replace({ ...service, active })
    notify('Демо-режим: изменение видно только до обновления страницы')
    return null
  }

  /** Источник пациента и заметка регистратуры. Возвращает текст ошибки или null. */
  const updateRegistry = async (patient: PatientItem, input: RegistryInput): Promise<string | null> => {
    if (apiOnline && isUuid(patient.id)) {
      try {
        replacePatient(await updateRegistryApi(patient.id, input))
        notify('Данные регистратуры сохранены')
        return null
      } catch (error) {
        return (error as Error).message
      }
    }
    const telegramConnected = input.removeTelegram ? false : Boolean(input.telegramChatId?.trim()) || patient.telegramConnected
    replacePatient({ ...patient, referralSource: input.referralSource, registryNote: input.registryNote, telegramConnected })
    notify('Демо-режим: изменения видны только до обновления страницы')
    return null
  }

  return {
    apiOnline,
    scheduleView,
    setScheduleView,
    saveHours,
    saveCard,
    practitionerAdded,
    savePrice,
    updateRegistry,
    scheduleItems,
    patientItems,
    practitioners,
    services,
    addService,
    toggleService,
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
    visitStarted,
    reschedule,
    book,
    createPatient,
    refreshPatients,
    updatePatient,
    grantConsent,
    revokeConsent,
    addLocalCharge,
    payLocalCharge,
    setLocalChargeAmount,
  }
}

export type ClinicData = ReturnType<typeof useClinicData>
