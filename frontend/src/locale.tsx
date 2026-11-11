import { createContext, useContext } from 'react'
import { ApiError } from './api'

export type Lang = 'ru' | 'uz'
export type Text = (ru: string, uz: string) => string
export const Locale = createContext({ lang: 'ru' as Lang, t: ((ru: string) => ru) as Text })
export const useLocale = () => useContext(Locale)
export const money = (amount: number, lang: Lang) => new Intl.NumberFormat(lang === 'uz' ? 'uz-UZ' : 'ru-RU',
  { maximumFractionDigits: 2 }).format(amount) + (lang === 'uz' ? ' so‘m' : ' сум')

const errors: Record<string, [string, string]> = {
  CATEGORY_SPEC_MISMATCH: ['Характеристики не соответствуют типу категории.', 'Xususiyatlar toifa turiga mos emas.'],
  PRODUCT_TYPE_IMMUTABLE: ['У товара есть комплектации. Прямая смена типа техники запрещена.', 'Mahsulotda variantlar bor. Texnika turini bevosita o‘zgartirish mumkin emas.'],
  CATEGORY_TYPE_IMMUTABLE: ['Тип существующей категории менять нельзя.', 'Mavjud toifa turini o‘zgartirib bo‘lmaydi.'],
  CATEGORY_TYPE_REQUIRED: ['Укажите тип категории PHONE или LAPTOP.', 'PHONE yoki LAPTOP toifa turini ko‘rsating.'],
  SPECS_REVIEW_REQUIRED: ['Проверьте сохранённые несовместимые поля и подтвердите очистку.', 'Mos kelmaydigan saqlangan maydonlarni tekshirib, tozalashni tasdiqlang.'],
  SKU_IMMUTABLE: ['Сохранённый артикул нельзя менять.', 'Saqlangan artikulni o‘zgartirib bo‘lmaydi.'],
  SKU_INVALID: ['Артикул содержит недопустимые символы.', 'Artikulda ruxsat etilmagan belgilar bor.'],
  NOT_FOUND: ['Страница или товар недоступны.', 'Sahifa yoki mahsulot mavjud emas.'],
  VALIDATION: ['Проверьте введённые данные.', 'Kiritilgan ma’lumotlarni tekshiring.'],
  PRICE_RANGE: ['Минимальная цена выше максимальной.', 'Minimal narx maksimal narxdan yuqori.'],
  PRICE_CHANGED: ['Цена изменилась. Проверьте новую цену и подтвердите заявку.', 'Narx o‘zgardi. Yangi narxni tekshirib, arizani tasdiqlang.'],
  PRIVACY_CHANGED: ['Условия обработки данных обновились. Обновите страницу.', 'Ma’lumotlarga ishlov berish shartlari yangilandi. Sahifani yangilang.'],
  RATE_LIMITED: ['Слишком много попыток. Подождите и повторите.', 'Urinishlar juda ko‘p. Birozdan keyin qayta urinib ko‘ring.'],
  NAME_INVALID: ['Проверьте имя. Допустимы буквы, пробелы, дефисы и апострофы.', 'Ismni tekshiring. Harflar, bo‘sh joy, chiziqcha va tutuq belgisi mumkin.'],
  PHONE_INVALID: ['Проверьте номер телефона, включая код страны.', 'Telefon raqami va mamlakat kodini tekshiring.'],
  LOGIN_FAILED: ['Проверьте логин и пароль.', 'Login va parolni tekshiring.'],
  AUTH_REQUIRED: ['Войдите в панель управления.', 'Boshqaruv paneliga kiring.'],
  FORBIDDEN: ['Недостаточно прав.', 'Ruxsat yetarli emas.'],
  CSRF_INVALID: ['Сессия обновилась. Повторите действие.', 'Sessiya yangilandi. Amalni takrorlang.'],
  PASSWORD_INVALID: ['Проверьте текущий пароль. Новый должен отличаться.', 'Joriy parolni tekshiring. Yangi parol boshqacha bo‘lishi kerak.'],
  PASSWORD_CHANGE_REQUIRED: ['Смените временный пароль.', 'Vaqtinchalik parolni o‘zgartiring.'],
  VERSION_CONFLICT: ['Данные уже изменены. Обновите страницу перед повторным сохранением.', 'Ma’lumot o‘zgargan. Saqlashdan oldin sahifani yangilang.'],
  DATA_CONFLICT: ['Проверьте уникальность логина, адреса страницы или артикула.', 'Login, sahifa manzili yoki artikul noyobligini tekshiring.'],
  INVALID_DISCOUNT: ['Скидочная цена должна быть ниже обычной. Проверьте даты.', 'Chegirma narxi odatiy narxdan past bo‘lishi kerak. Sanalarni tekshiring.'],
  PUBLICATION_REQUIREMENTS: ['Для публикации нужны фотография и комплектация.', 'Nashr qilish uchun surat va variant kerak.'],
  IMAGE_LIMIT: ['На модель можно загрузить до 8 фото.', 'Modelga 8 tagacha surat yuklash mumkin.'],
  IMAGE_INVALID: ['Выберите корректное изображение JPEG, PNG или WebP.', 'Yaroqli JPEG, PNG yoki WebP suratini tanlang.'],
  IMAGE_DIMENSIONS: ['Разрешение фотографии слишком большое.', 'Surat o‘lchami juda katta.'],
  PAYLOAD_TOO_LARGE: ['Файл или запрос слишком большой.', 'Fayl yoki so‘rov juda katta.'],
  INVALID_LEAD_STATUS: ['Этот переход статуса недоступен. Для повторного открытия нужна причина.', 'Bu holatga o‘tib bo‘lmaydi. Qayta ochish uchun sabab kerak.'],
  DATE_RANGE: ['Дата начала позже даты окончания.', 'Boshlanish sanasi yakun sanasidan keyin.'],
  IDEMPOTENCY_CONFLICT: ['Данные попытки изменились. Проверьте поля и повторите.', 'Urinish ma’lumotlari o‘zgardi. Maydonlarni tekshirib, takrorlang.'],
  NOTIFICATION_IN_PROGRESS: ['Уведомление отправляется. Повторите действие позже.', 'Xabar yuborilmoqda. Keyinroq takrorlang.'],
  LEAD_ANONYMIZED: ['Контактные данные заявки уже обезличены.', 'Arizaning aloqa ma’lumotlari shaxssizlantirilgan.'],
  TELEGRAM_INVALID: ['Введите имя Telegram или ссылку t.me.', 'Telegram nomi yoki t.me havolasini kiriting.'],
  MAP_INVALID: ['Введите HTTPS-ссылку на поддерживаемую карту.', 'Qo‘llab-quvvatlanadigan xaritaning HTTPS havolasini kiriting.'],
}
export function ErrorText({ error }: { error: unknown }) {
  const { t } = useLocale()
  const code = error instanceof ApiError ? error.code : 'NETWORK'
  return <p className="alert" role="alert">{t(...(errors[code] ?? [
    'Не удалось выполнить запрос. Проверьте соединение и повторите.',
    'So‘rov bajarilmadi. Aloqani tekshirib, qayta urinib ko‘ring.',
  ]))}</p>
}
export const statusName = (value: string, t: Text) => {
  const labels: Record<string, [string, string]> = {
    NEW: ['Новая', 'Yangi'], CONTACTED: ['Связались', 'Bog‘lanildi'], COMPLETED: ['Завершена', 'Yakunlandi'],
    CANCELLED: ['Отменена', 'Bekor qilindi'], DRAFT: ['Черновик', 'Qoralama'], PUBLISHED: ['Опубликован', 'Nashr qilingan'],
    ARCHIVED: ['В архиве', 'Arxivda'], IN_STOCK: ['В наличии', 'Mavjud'], OUT_OF_STOCK: ['Нет в наличии', 'Mavjud emas'],
    PENDING: ['Ожидает', 'Kutilmoqda'], SENT: ['Отправлено', 'Yuborildi'], FAILED: ['Ошибка', 'Xato'],
  }
  return labels[value] ? t(...labels[value]) : value
}
