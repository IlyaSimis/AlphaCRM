import type { ReactNode } from 'react'
import { AddRounded } from '@mui/icons-material'
import { Box, Button, Divider, Typography } from '@mui/material'
import { normalizePatientProfile } from '../../api/patients'
import type { PatientItem } from '../../api/types'
import { Info } from '../../shared/ui'

const genderLabels = { MALE: 'Мужчина', FEMALE: 'Женщина', UNSPECIFIED: 'Не указан' }

function date(value?: string | null) {
  if (!value) return '—'
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return <Box component="section" className="patient-profile-section"><Typography variant="h6" sx={{ fontSize: 17, fontWeight: 750 }}>{title}</Typography>{children}</Box>
}

/** Все административные реквизиты карточки; пустые поля видны как незаполненные. */
export function PatientProfileDetails({ patient, onAddRepresentative }: { patient: PatientItem; onAddRepresentative?: () => void }) {
  const profile = normalizePatientProfile(patient.profile)
  const channels = [
    ['Эл. почта (email)', profile.emailNotifications],
    ['SMS', profile.smsNotifications],
    ['Мессенджеры', profile.messengerNotifications],
    ['Push', profile.pushNotifications],
  ] as const
  return <Box className="panel patient-profile-details">
    <Typography className="panel-label">РАСШИРЕННЫЕ ДАННЫЕ ПАЦИЕНТА</Typography>
    <Box className="patient-profile-grid">
      <Section title="ФИО">
        <Info label="Фамилия" value={profile.lastName ?? '—'} />
        <Info label="Имя" value={profile.firstName ?? '—'} />
        <Info label="Отчество" value={profile.middleName ?? '—'} />
        <Info label="Пол" value={genderLabels[profile.gender ?? 'UNSPECIFIED']} />
        <Info label="Дата рождения" value={date(patient.birthDate)} />
      </Section>
      <Section title="Адрес">
        <Info label="Тип адреса" value={profile.addressType ?? '—'} />
        <Info label="Регион" value={profile.region ?? '—'} />
        <Info label="Населённый пункт" value={profile.locality ?? '—'} />
        <Info label="Улица" value={profile.street ?? '—'} />
        <Info label="Дом" value={profile.house ?? '—'} />
        <Info label="Квартира" value={profile.apartment ?? '—'} />
      </Section>
      <Section title="Другие примечания">
        <Info label="Место работы" value={profile.employer ?? '—'} />
        <Info label="Профессия/должность (по компании)" value={profile.professionCompany ?? '—'} />
        <Info label="Профессия/должность (по НСИ)" value={profile.professionNsi ?? '—'} />
        <Info label="Дата начала работы" value={date(profile.workStartDate)} />
        <Info label="Бонусная карта" value={profile.bonusCard ?? '—'} />
      </Section>
      <Section title="Документ">
        <Info label="Документ" value={profile.documentType ?? '—'} />
        <Info label="Серия и номер" value={profile.documentSeriesNumber ?? '—'} />
        <Info label="Дата выдачи" value={date(profile.documentIssueDate)} />
        <Info label="Код подразделения" value={profile.documentDepartmentCode ?? '—'} />
        <Info label="Орган выдачи" value={profile.documentIssuedBy ?? '—'} />
      </Section>
      <Section title="Договор">
        <Info label="Номер договора" value={profile.contractNumber ?? '—'} />
        <Info label="Договор от" value={date(profile.contractFrom)} />
        <Info label="Договор до" value={date(profile.contractTo)} />
      </Section>
      <Section title="Другие реквизиты">
        <Info label="СНИЛС" value={profile.snils ?? '—'} />
        <Info label="ИНН" value={profile.inn ?? '—'} />
        <Info label="Реферальный агент" value={profile.referralAgent ?? '—'} />
        <Info label="Теги" value={profile.tags?.join(', ') || '—'} />
      </Section>
      <Section title="Амбулаторная карта">
        <Info label="Номер" value={profile.ambulatoryNumber ?? '—'} />
        <Info label="Заметки" value={profile.ambulatoryNotes ?? '—'} />
      </Section>
      <Section title="Согласия (уведомления)">
        {channels.map(([label, allowed]) => <Info key={label} label={label} value={allowed ? 'Разрешено' : 'Не разрешено'} />)}
      </Section>
    </Box>
    <Divider sx={{ my: 3 }} />
    <Box className="panel-title-row">
      <Typography variant="h6" sx={{ fontSize: 17, fontWeight: 750 }}>Представители</Typography>
      {onAddRepresentative && <Button size="small" startIcon={<AddRounded />} aria-label="Добавить представителя из карточки" onClick={onAddRepresentative}>Добавить представителя</Button>}
    </Box>
    {profile.representatives?.length ? profile.representatives.map((representative, index) => <Box className="history-item" key={`${index}-${representative.fullName ?? ''}`}>
      <Box><Typography sx={{ fontWeight: 650 }}>{index + 1}. {representative.fullName || 'Без имени'}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{representative.relationship || 'Родство не указано'}</Typography></Box>
      {representative.primary && <Typography sx={{ color: 'primary.main', fontWeight: 650 }}>Основной</Typography>}
    </Box>) : <Typography sx={{ color: 'text.secondary', mt: 1 }}>Представители не указаны</Typography>}
  </Box>
}
