import type { Variant } from './api'
type T = (ru: string, uz: string) => string
export const specFields: [keyof Variant, string, string, string?][] = [
  ['color','Цвет','Rang'], ['ramGb','Оперативная память','Operativ xotira'],
  ['storageGb','Встроенная память','Ichki xotira'], ['storageType','Тип накопителя','Disk turi','LAPTOP'],
  ['screen','Экран','Ekran'], ['cpu','Процессор','Protsessor'], ['gpu','Видеокарта','Videokarta','LAPTOP'],
  ['cameras','Камеры','Kameralar','PHONE'], ['battery','Аккумулятор','Batareya','PHONE'],
  ['sim','SIM / eSIM','SIM / eSIM','PHONE'], ['os','ОС','OT'], ['weight','Вес','Vazn','LAPTOP'],
  ['notes','Дополнительно','Qo‘shimcha'],
]
export function specifications(v: Variant, kind: string, t: T): [string, string][] {
  return specFields.filter(([, , , only]) => !only || only === kind).flatMap(([key, ru, uz]) => {
    const value = v[key]
    if (value === null || value === undefined || String(value).trim() === '') return []
    const title = key === 'storageGb' && kind === 'LAPTOP' ? t('Накопитель','Disk') : t(ru,uz)
    const formatted = key === 'ramGb' || key === 'storageGb' ? String(value).replace(/\s*(GB|ГБ)$/i,'') + t(' ГБ',' GB') : String(value)
    return [[title, formatted]]
  })
}
export function variantLabel(v: Variant, kind: string, t: T) {
  return specifications(v,kind,t).filter(([title]) =>
    [t('Цвет','Rang'),t('Оперативная память','Operativ xotira'),t('Встроенная память','Ichki xotira'),t('Накопитель','Disk'),t('Тип накопителя','Disk turi'),t('Процессор','Protsessor'),t('Видеокарта','Videokarta')].includes(title)
  ).map(([title,value]) => title + ': ' + value).concat(t('Артикул','Artikul') + ': ' + v.sku).join(' · ')
}
