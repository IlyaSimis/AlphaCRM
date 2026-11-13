import { useState, type ReactNode } from 'react'
import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { cleanPatientProfile, emailProblem, normalizePatientProfile, type PatientInput } from '../../api/patients'
import type { AssignedStaff, PatientItem, PatientProfile, StaffItem } from '../../api/types'
import { phoneSharedWith } from '../registry/registry'

type PatientDialogProps = {
  open: boolean
  mode?: 'create' | 'edit'
  /** Только для правки: новая карточка открывается пустой. */
  initial?: PatientInput
  error?: string
  patients?: PatientItem[]
  selfId?: string
  administrators?: StaffItem[]
  currentAssigned?: AssignedStaff | null
  onClose: () => void
  onSave: (input: PatientInput) => void
}

type TextProfileKey = Exclude<keyof PatientProfile, 'gender' | 'representatives' | 'tags' | 'emailNotifications' | 'smsNotifications' | 'messengerNotifications' | 'pushNotifications'>

const NOBODY = ''

export function PatientDialog({ open, mode = 'create', initial, error = '', patients = [], selfId, administrators = [], currentAssigned = null, onClose, onSave }: PatientDialogProps) {
  const edit = mode === 'edit'
  const start = initial
  const initialProfile = normalizePatientProfile(start?.profile)
  const nameParts = start?.fullName.trim().split(/\s+/) ?? []
  const initialLastName = initialProfile.lastName ?? nameParts[1] ?? nameParts[0] ?? ''
  const initialFirstName = initialProfile.firstName ?? nameParts[0] ?? ''
  const initialMiddleName = initialProfile.middleName ?? nameParts.slice(2).join(' ')
  const [lastName, setLastName] = useState(initialLastName)
  const [firstName, setFirstName] = useState(initialFirstName)
  const [middleName, setMiddleName] = useState(initialMiddleName)
  const [birthDate, setBirthDate] = useState(start?.birthDate ?? '')
  const [phone, setPhone] = useState(start?.phone ?? '')
  const [secondPhone, setSecondPhone] = useState(start?.secondPhone ?? '')
  const [email, setEmail] = useState(start?.email ?? '')
  const [assignedStaffId, setAssignedStaffId] = useState(start?.assignedStaffId ?? NOBODY)
  const [profile, setProfile] = useState<PatientProfile>(initialProfile)
  const [tagsText, setTagsText] = useState(initialProfile.tags?.join(', ') ?? '')
  const duplicates = phoneSharedWith([phone, secondPhone], patients, selfId)
  const emailError = emailProblem(email)
  const keepsInactive = currentAssigned && !currentAssigned.active && !administrators.some((item) => item.id === currentAssigned.id)
  const setField = (field: keyof PatientProfile, value: PatientProfile[keyof PatientProfile]) => setProfile((current) => ({ ...current, [field]: value }))
  const text = (field: TextProfileKey, label: string, type = 'text') => <TextField label={label} type={type} value={profile[field] ?? ''} onChange={(event) => setField(field, event.target.value)} slotProps={{ inputLabel: { shrink: type === 'date' ? true : undefined } }} />
  const section = (title: string, children: ReactNode) => <Box component="section" className="patient-form-section"><Typography variant="h6" sx={{ fontSize: 17, fontWeight: 750, mb: 2 }}>{title}</Typography><Box className="patient-form-grid">{children}</Box></Box>
  const updateRepresentative = (index: number, patch: { fullName?: string; relationship?: string; primary?: boolean }) => setProfile((current) => ({
    ...current,
    representatives: (current.representatives ?? []).map((item, itemIndex) => {
      if (patch.primary) return { ...item, ...(itemIndex === index ? patch : { primary: false }) }
      return itemIndex === index ? { ...item, ...patch } : item
    }),
  }))
  const save = () => {
    const nameUntouched = lastName === initialLastName && firstName === initialFirstName && middleName === initialMiddleName
    const fullName = nameUntouched && start?.fullName ? start.fullName : [lastName, firstName, middleName].map((part) => part.trim()).filter(Boolean).join(' ')
    onSave({
      fullName, birthDate, phone: phone.trim(), secondPhone: secondPhone.trim(), email: email.trim(), assignedStaffId: assignedStaffId || null,
      profile: cleanPatientProfile({ ...profile, lastName, firstName, middleName, tags: tagsText.split(',') }),
    })
  }

  return <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth scroll="paper">
    <DialogTitle>{edit ? 'Данные пациента' : 'Новый пациент'}</DialogTitle>
    <DialogContent>
      <Stack spacing={3} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        {section('ФИО и контакты', <>
          <TextField required label="Фамилия" value={lastName} onChange={(event) => setLastName(event.target.value)} />
          <TextField required label="Имя" value={firstName} onChange={(event) => setFirstName(event.target.value)} />
          <TextField label="Отчество" value={middleName} onChange={(event) => setMiddleName(event.target.value)} />
          <TextField required label="Дата рождения" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField select label="Пол" value={profile.gender ?? 'UNSPECIFIED'} onChange={(event) => setField('gender', event.target.value as PatientProfile['gender'])}>
            <MenuItem value="MALE">Мужчина</MenuItem><MenuItem value="FEMALE">Женщина</MenuItem><MenuItem value="UNSPECIFIED">Не указан</MenuItem>
          </TextField>
          <TextField required label="Телефон" value={phone} onChange={(event) => setPhone(event.target.value)} />
          <TextField label="Телефон (доп.)" value={secondPhone} onChange={(event) => setSecondPhone(event.target.value)} helperText="Необязательно" />
          <TextField label="E-mail" type="email" value={email} error={Boolean(emailError)} helperText={emailError ?? 'Необязательно'} onChange={(event) => setEmail(event.target.value)} slotProps={{ htmlInput: { maxLength: 255 } }} />
          <TextField select label="Постоянный администратор" value={assignedStaffId} onChange={(event) => setAssignedStaffId(event.target.value)}>
            <MenuItem value={NOBODY}>Не назначен</MenuItem>
            {keepsInactive && currentAssigned && <MenuItem value={currentAssigned.id}>{currentAssigned.fullName} · учётная запись отключена</MenuItem>}
            {administrators.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName}</MenuItem>)}
          </TextField>
        </>)}
        {duplicates.length > 0 && <Alert severity="warning">Этот телефон уже есть в карточке: {duplicates.map((item) => item.fullName).join(', ')}. Сохранить можно, но проверьте, не дубль ли это.</Alert>}
        {section('Адрес', <>
          {text('addressType', 'Тип адреса')}
          {text('region', 'Регион')}
          {text('locality', 'Населённый пункт')}
          {text('street', 'Улица')}
          {text('house', 'Дом')}
          {text('apartment', 'Квартира')}
        </>)}
        {section('Другие примечания', <>
          {text('employer', 'Место работы')}
          {text('professionCompany', 'Профессия/должность (по компании)')}
          {text('professionNsi', 'Профессия/должность (по НСИ)')}
          {text('workStartDate', 'Дата начала работы', 'date')}
          {text('bonusCard', 'Бонусная карта')}
        </>)}
        {section('Документ', <>
          {text('documentType', 'Документ')}
          {text('documentSeriesNumber', 'Серия и номер')}
          {text('documentIssueDate', 'Дата выдачи', 'date')}
          {text('documentDepartmentCode', 'Код подразделения')}
          <Box sx={{ gridColumn: '1 / -1' }}>{text('documentIssuedBy', 'Орган выдачи')}</Box>
        </>)}
        {section('Договор', <>
          {text('contractNumber', 'Номер договора')}
          {text('contractFrom', 'Договор от', 'date')}
          {text('contractTo', 'Договор до', 'date')}
        </>)}
        <Box component="section" className="patient-form-section">
          <Typography variant="h6" sx={{ fontSize: 17, fontWeight: 750, mb: 2 }}>Представители</Typography>
          <Stack spacing={2}>
            {(profile.representatives ?? []).map((representative, index) => <Box key={index} className="patient-representative">
              <TextField autoFocus={edit && index === (profile.representatives?.length ?? 0) - 1 && !representative.fullName} label={`Представитель ${index + 1}: ФИО`} value={representative.fullName ?? ''} onChange={(event) => updateRepresentative(index, { fullName: event.target.value })} />
              <TextField label="Кем приходится" value={representative.relationship ?? ''} onChange={(event) => updateRepresentative(index, { relationship: event.target.value })} />
              <FormControlLabel control={<Checkbox checked={Boolean(representative.primary)} onChange={(event) => updateRepresentative(index, { primary: event.target.checked })} />} label="Основной" />
              <Button color="warning" onClick={() => setProfile((current) => ({ ...current, representatives: (current.representatives ?? []).filter((_, itemIndex) => itemIndex !== index) }))}>Удалить</Button>
            </Box>)}
            <Button sx={{ alignSelf: 'flex-start' }} onClick={() => setField('representatives', [...(profile.representatives ?? []), { fullName: '', relationship: '', primary: false }])}>Добавить представителя</Button>
          </Stack>
        </Box>
        {section('Другие реквизиты', <>
          {text('snils', 'СНИЛС')}
          {text('inn', 'ИНН')}
          {text('referralAgent', 'Реферальный агент')}
          <TextField label="Теги" value={tagsText} onChange={(event) => setTagsText(event.target.value)} helperText="Через запятую" />
        </>)}
        {section('Амбулаторная карта', <>
          {text('ambulatoryNumber', 'Номер')}
          <Box sx={{ gridColumn: '1 / -1' }}><TextField fullWidth multiline minRows={3} label="Заметки" value={profile.ambulatoryNotes ?? ''} onChange={(event) => setField('ambulatoryNotes', event.target.value)} /></Box>
        </>)}
        <Box component="section" className="patient-form-section">
          <Typography variant="h6" sx={{ fontSize: 17, fontWeight: 750, mb: 1 }}>Согласия (уведомления)</Typography>
          <Box className="patient-notifications">
            <FormControlLabel control={<Checkbox checked={Boolean(profile.emailNotifications)} onChange={(event) => setField('emailNotifications', event.target.checked)} />} label="Эл. почта (email)" />
            <FormControlLabel control={<Checkbox checked={Boolean(profile.smsNotifications)} onChange={(event) => setField('smsNotifications', event.target.checked)} />} label="SMS" />
            <FormControlLabel control={<Checkbox checked={Boolean(profile.messengerNotifications)} onChange={(event) => setField('messengerNotifications', event.target.checked)} />} label="Мессенджеры" />
            <FormControlLabel control={<Checkbox checked={Boolean(profile.pushNotifications)} onChange={(event) => setField('pushNotifications', event.target.checked)} />} label="Push" />
          </Box>
        </Box>
        {edit && <Alert severity="info">В журнал аудита попадут только названия изменённых полей, без самих значений.</Alert>}
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={!lastName.trim() || !firstName.trim() || !birthDate || !phone.trim() || Boolean(emailError)} onClick={save}>{edit ? 'Сохранить изменения' : 'Сохранить пациента'}</Button>
    </DialogActions>
  </Dialog>
}
