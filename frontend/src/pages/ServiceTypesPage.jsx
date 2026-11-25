import { useState } from 'react';
import { fieldErrors, request } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useMeta } from '../meta.jsx';
import { useToast } from '../toast.jsx';
import { formatMoney } from '../format.js';
import Modal from '../components/Modal.jsx';
import { ErrorBox, Field } from '../components/common.jsx';

// справочник видов услуг; изменять может только администратор
export default function ServiceTypesPage() {
  const { can } = useAuth();
  const { serviceTypes, reloadServiceTypes } = useMeta();
  const notify = useToast();
  const [editing, setEditing] = useState(null);
  const isAdmin = can('ADMIN');

  async function toggleActive(type) {
    try {
      await request(`/api/service-types/${type.id}/${type.active ? 'disable' : 'enable'}`, { method: 'POST' });
      notify(type.active ? `Услуга «${type.name}» отключена` : `Услуга «${type.name}» включена`, 'success');
      reloadServiceTypes();
    } catch (err) {
      notify(err.message, 'error');
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>Справочник услуг</h1>
        <span className="spacer" />
        {isAdmin && <button className="btn primary" type="button" onClick={() => setEditing({})}>+ Новая услуга</button>}
      </div>
      {!isAdmin && <p className="muted">Изменять справочник может только администратор.</p>}
      <div className="panel table-wrap">
        <table>
          <thead>
            <tr><th>Код</th><th>Название</th><th>Описание</th><th>Цена</th><th>Длительность</th><th>Статус</th>{isAdmin && <th />}</tr>
          </thead>
          <tbody>
            {serviceTypes.map((s) => (
              <tr key={s.id} className={s.active ? undefined : 'archived'}>
                <td className="mono">{s.code}</td>
                <td>{s.name}</td>
                <td>{s.description || '—'}</td>
                <td className="nowrap">{formatMoney(s.basePrice)}</td>
                <td className="nowrap">{s.durationMinutes} мин</td>
                <td>{s.active ? <span className="badge ok">включена</span> : <span className="badge">отключена</span>}</td>
                {isAdmin && (
                  <td className="actions">
                    <button className="btn small" type="button" onClick={() => setEditing(s)}>Изменить</button>
                    <button className="btn small" type="button" onClick={() => toggleActive(s)}>{s.active ? 'Отключить' : 'Включить'}</button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing && (
        <ServiceTypeForm
          type={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reloadServiceTypes();
          }}
        />
      )}
    </>
  );
}

function ServiceTypeForm({ type, onClose, onSaved }) {
  const notify = useToast();
  const [form, setForm] = useState({
    code: type?.code || '',
    name: type?.name || '',
    description: type?.description || '',
    basePrice: type ? String(type.basePrice) : '',
    durationMinutes: type ? String(type.durationMinutes) : '30',
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const set = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    const body = {
      ...form,
      code: form.code.trim().toUpperCase(),
      description: form.description || null,
      basePrice: form.basePrice === '' ? null : Number(form.basePrice),
      durationMinutes: form.durationMinutes === '' ? null : Number(form.durationMinutes),
      version: type?.version,
    };
    try {
      if (type) await request(`/api/service-types/${type.id}`, { method: 'PUT', body });
      else await request('/api/service-types', { method: 'POST', body });
      notify('Справочник сохранён', 'success');
      onSaved();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(err);
    }
  }

  return (
    <Modal
      title={type ? `Услуга ${type.code}` : 'Новая услуга'}
      onClose={onClose}
      footer={(
        <>
          <button className="btn" type="button" onClick={onClose}>Отмена</button>
          <button className="btn primary" type="submit" form="service-form">Сохранить</button>
        </>
      )}
    >
      <form id="service-form" className="form-grid" onSubmit={submit}>
        <Field label="Код (не меняется после создания)" error={errors.code}>
          <input required value={form.code} onChange={set('code')} disabled={Boolean(type)} placeholder="ENGINE_WASH" />
        </Field>
        <Field label="Название" error={errors.name}>
          <input required value={form.name} onChange={set('name')} />
        </Field>
        <Field label="Базовая цена, ₽" error={errors.basePrice}>
          <input type="number" min="0" step="0.01" required value={form.basePrice} onChange={set('basePrice')} />
        </Field>
        <Field label="Длительность, мин" error={errors.durationMinutes}>
          <input type="number" min="5" max="1440" required value={form.durationMinutes} onChange={set('durationMinutes')} />
        </Field>
        <Field label="Описание" error={errors.description} className="full">
          <textarea value={form.description} onChange={set('description')} maxLength={500} />
        </Field>
        <div className="full"><ErrorBox error={error} /></div>
      </form>
    </Modal>
  );
}
