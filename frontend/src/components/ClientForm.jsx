import { useState } from 'react';
import { fieldErrors, request } from '../api.js';
import { useToast } from '../toast.jsx';
import Modal from './Modal.jsx';
import { ErrorBox, Field } from './common.jsx';

// создание и изменение клиента
export default function ClientForm({ client, onClose, onSaved }) {
  const notify = useToast();
  const [form, setForm] = useState({
    fullName: client?.fullName || '',
    phone: client?.phone || '+7-',
    email: client?.email || '',
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      const body = { ...form, email: form.email || null, version: client?.version };
      const saved = client
        ? await request(`/api/clients/${client.id}`, { method: 'PUT', body })
        : await request('/api/clients', { method: 'POST', body });
      notify(client ? 'Клиент изменён' : 'Клиент добавлен', 'success');
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
      title={client ? 'Изменение клиента' : 'Новый клиент'}
      onClose={onClose}
      footer={(
        <>
          <button className="btn" type="button" onClick={onClose}>Отмена</button>
          <button className="btn primary" type="submit" form="client-form" disabled={busy}>Сохранить</button>
        </>
      )}
    >
      <form id="client-form" className="form-grid" onSubmit={submit}>
        <Field label="ФИО" error={errors.fullName} className="full">
          <input required value={form.fullName} onChange={set('fullName')} placeholder="Иван Петров" />
        </Field>
        <Field label="Телефон" error={errors.phone}>
          <input required value={form.phone} onChange={set('phone')} placeholder="+7-900-123-45-67" />
        </Field>
        <Field label="Email (необязательно)" error={errors.email}>
          <input type="email" value={form.email} onChange={set('email')} />
        </Field>
        <div className="full"><ErrorBox error={error} /></div>
      </form>
    </Modal>
  );
}
