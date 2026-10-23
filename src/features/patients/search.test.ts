import { describe, expect, it } from 'vitest'
import type { PatientItem } from '../../api/types'
import { filterPatients } from './search'

const patients: PatientItem[] = [
  { id: '1', name: 'Анна Михайлова', birth: '14.05.1991', birthDate: '1991-05-14', phone: '+7 916 245-18-72', consent: 'ACTIVE', consents: [] },
  { id: '2', name: 'Дмитрий Волков', birth: '02.11.1985', birthDate: '1985-11-02', phone: '+7 903 782-44-16', consent: 'ACTIVE', consents: [] },
]

describe('patient search', () => {
  it('searches by case-insensitive name, phone and birth date', () => {
    expect(filterPatients(patients, 'анна').map((p) => p.id)).toEqual(['1'])
    expect(filterPatients(patients, '+7 903').map((p) => p.id)).toEqual(['2'])
    expect(filterPatients(patients, '1985').map((p) => p.id)).toEqual(['2'])
  })

  it('an empty query returns everyone', () => {
    expect(filterPatients(patients, '  ')).toHaveLength(2)
  })
})
