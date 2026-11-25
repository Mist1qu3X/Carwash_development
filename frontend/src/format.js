// форматирование значений для отображения

const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 2 });
const dateTime = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });

export function formatMoney(value) {
  return value === null || value === undefined ? '—' : money.format(Number(value));
}

// LocalDateTime с сервера ("2026-12-01T10:00:00") — время автомойки, без часового пояса
export function formatLocal(value) {
  return value ? dateTime.format(new Date(value)) : '—';
}

// Instant с сервера ("2026-11-25T07:00:00Z") — показываем в часовом поясе браузера
export function formatInstant(value) {
  return value ? dateTime.format(new Date(value)) : '—';
}

// значение для <input type="datetime-local">
export function toInputDateTime(value) {
  return value ? String(value).slice(0, 16) : '';
}

export function defaultBookingTime() {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(10, 0, 0, 0);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T10:00`;
}

export function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function fileNameFromResponse(res, fallback) {
  const header = res.headers.get('Content-Disposition') || '';
  const match = header.match(/filename="?([^";]+)"?/);
  return match ? match[1] : fallback;
}
