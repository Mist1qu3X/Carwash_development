import { useEffect, useState } from 'react';
import { fieldErrors, request } from '../api.js';
import { useMeta } from '../meta.jsx';
import { useToast } from '../toast.jsx';
import { defaultBookingTime, toInputDateTime } from '../format.js';
import Modal from './Modal.jsx';
import ClientForm from './ClientForm.jsx';
import { ErrorBox, Field } from './common.jsx';

function initialForm(booking) {
  if (!booking) {
    return {
      code: '', clientId: '', carNumber: '', carModel: '', customerComment: '', serviceTypeId: '',
      status: 'CREATED', scheduledAt: defaultBookingTime(), price: '', paid: false, tags: '',
    };
  }
  return {
    code: booking.code,
    clientId: booking.client.id,
    carNumber: booking.carNumber,
    carModel: booking.carModel || '',
    customerComment: booking.customerComment || '',
    serviceTypeId: booking.serviceType.id,
    status: booking.status,
    scheduledAt: toInputDateTime(booking.scheduledAt),
    price: String(booking.price),
    paid: booking.paid,
    tags: booking.tags.join(', '),
  };
}

// создание и изменение записи на мойку
export default function BookingForm({ booking, onClose, onSaved }) {
  const { meta, serviceTypes } = useMeta();
  const notify = useToast();
  const [form, setForm] = useState(() => initialForm(booking));
  const [clients, setClients] = useState(booking ? [booking.client] : []);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [newClient, setNewClient] = useState(false);

  useEffect(() => {
    request('/api/clients', { query: { size: 100, sort: 'fullName,asc' } }).then((page) => {
      setClients((current) => {
        const all = [...page.content];
        current.forEach((c) => {
          if (!all.some((x) => x.id === c.id)) all.push(c);
        });
        return all;
      });
    });
  }, []);

  const set = (name) => (e) => setForm({ ...form, [name]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  // при выборе услуги подставляем её базовую цену
  function changeServiceType(e) {
    const type = serviceTypes.find((s) => s.id === e.target.value);
    setForm({ ...form, serviceTypeId: e.target.value, price: type && (!booking || form.price === '') ? String(type.basePrice) : form.price });
  }

  // отключённую услугу можно оставить у старой записи, но не выбрать заново
  const availableTypes = serviceTypes.filter((s) => s.active || s.id === booking?.serviceType.id);
  // новая запись — "Создана" или "Подтверждена"; при изменении — текущий статус и допустимые переходы
  const statusOptions = booking
    ? [booking.status, ...(meta.statuses.find((s) => s.code === booking.status)?.next || [])]
    : ['CREATED', 'CONFIRMED'];
  const finalStatus = booking && meta.statuses.find((s) => s.code === booking.status)?.finalStatus;

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body = {
      code: form.code.trim() || null,
      clientId: form.clientId || null,
      carNumber: form.carNumber,
      carModel: form.carModel || null,
      customerComment: form.customerComment || null,
      serviceTypeId: form.serviceTypeId || null,
      status: form.status,
      scheduledAt: form.scheduledAt || null,
      price: form.price === '' ? null : Number(form.price),
      paid: form.paid,
      tags: form.tags.split(',').map((t) => t.trim()).filter(Boolean),
      version: booking?.version,
    };
    try {
      const saved = booking
        ? await request(`/api/bookings/${booking.id}`, { method: 'PUT', body })
        : await request('/api/bookings', { method: 'POST', body });
      notify(booking ? `Запись ${saved.code} сохранена` : `Создана запись ${saved.code}`, 'success');
      onSaved(saved);
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={booking ? `Запись ${booking.code}` : 'Новая запись на мойку'}
      onClose={onClose}
      footer={(
        <>
          <button className="btn" type="button" onClick={onClose}>Отмена</button>
          <button className="btn primary" type="submit" form="booking-form" disabled={busy}>
            {busy ? 'Сохранение…' : 'Сохранить'}
          </button>
        </>
      )}
    >
      {finalStatus && (
        <p className="muted">У завершённой или отменённой записи можно менять только оплату, теги и комментарий.</p>
      )}
      <form id="booking-form" className="form-grid" onSubmit={submit}>
        <Field label="Клиент" error={errors.clientId} className="full">
          <span className="row">
            <select required value={form.clientId} onChange={set('clientId')} className="spacer">
              <option value="">— выберите клиента —</option>
              {clients.map((c) => <option key={c.id} value={c.id}>{c.fullName} ({c.phone})</option>)}
            </select>
            <button className="btn" type="button" onClick={() => setNewClient(true)}>+ Клиент</button>
          </span>
        </Field>
        <Field label="Гос. номер" error={errors.carNumber}>
          <input required value={form.carNumber} onChange={set('carNumber')} placeholder="А123ВС77" />
        </Field>
        <Field label="Модель автомобиля" error={errors.carModel}>
          <input value={form.carModel} onChange={set('carModel')} placeholder="Toyota Camry" />
        </Field>
        <Field label="Услуга" error={errors.serviceTypeId}>
          <select required value={form.serviceTypeId} onChange={changeServiceType}>
            <option value="">— выберите услугу —</option>
            {availableTypes.map((s) => <option key={s.id} value={s.id}>{s.name}{s.active ? '' : ' (отключена)'}</option>)}
          </select>
        </Field>
        <Field label="Дата и время" error={errors.scheduledAt}>
          <input type="datetime-local" required value={form.scheduledAt} onChange={set('scheduledAt')} />
        </Field>
        <Field label="Цена, ₽" error={errors.price}>
          <input type="number" min="0" max="1000000" step="0.01" required value={form.price} onChange={set('price')} />
        </Field>
        <Field label="Статус" error={errors.status}>
          <select value={form.status} onChange={set('status')}>
            {statusOptions.map((code) => (
              <option key={code} value={code}>{meta.statuses.find((s) => s.code === code)?.title}</option>
            ))}
          </select>
        </Field>
        <Field label="Теги через запятую" error={errors.tags}>
          <input value={form.tags} onChange={set('tags')} placeholder="vip, постоянный" />
        </Field>
        <Field label="Внешний код (необязательно)" error={errors.code}>
          <input value={form.code} onChange={set('code')} placeholder="сгенерируется: CW-000013" />
        </Field>
        <Field label="Пожелания клиента" error={errors.customerComment} className="full">
          <textarea value={form.customerComment} onChange={set('customerComment')} maxLength={500} />
        </Field>
        <label className="check full">
          <input type="checkbox" checked={form.paid} onChange={set('paid')} /> Оплачено
        </label>
        <div className="full"><ErrorBox error={error} /></div>
      </form>

      {newClient && (
        <ClientForm
          onClose={() => setNewClient(false)}
          onSaved={(client) => {
            setClients((list) => [...list, client]);
            setForm((f) => ({ ...f, clientId: client.id }));
            setNewClient(false);
          }}
        />
      )}
    </Modal>
  );
}
