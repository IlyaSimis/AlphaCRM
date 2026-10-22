export function initialsOf(name: string) {
  return name.split(' ').map((part) => part[0]).join('').slice(0, 2)
}

export function isUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
}
