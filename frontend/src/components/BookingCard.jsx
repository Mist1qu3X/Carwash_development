import { useCallback, useEffect, useState } from 'react';
import { request } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { formatInstant, formatLocal, formatMoney } from '../format.js';
import Modal from './Modal.jsx';
import { ErrorBox, RiskBadge, StatusBadge, Tags } from './common.jsx';

// карточка записи: все поля + история заметок
export default function BookingCard({ id, onClose, onEdit }) {
  const { user, can } = useAuth();
  const notify = useToast();
  const [booking, setBooking] = useState(null);
  const [notes, setNotes] = useState([]);
  const [error, setError] = useState(null);
  const [text, setText] = useState('');
  const [pinned, setPinned] = useState(false);
  const [editing, setEditing] = useState(null); // { id, text }

  const load = useCallback(async () => {
    try {
      const [b, n] = await Promise.all([request(`/api/bookings/${id}`), request(`/api/bookings/${id}/notes`)]);
      setBooking(b);
      setNotes(n);
    } catch (err) {
      setError(err);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function run(promise, message) {
    try {
      await promise;
      if (message) notify(message, 'success');
      await load();
    } catch (err) {
      notify(err.message, 'error');
    }
  }

  async function addNote(e) {
    e.preventDefault();
    await run(request(`/api/bookings/${id}/notes`, { method: 'POST', body: { text, pinned } }), 'Заметка добавлена');
    setText('');
    setPinned(false);
  }

  const saveEdit = (note) => run(
    request(`/api/bookings/${id}/notes/${note.id}`, { method: 'PUT', body: { text: editing.text, pinned: note.pinned, version: note.version } }),
    'Заметка изменена',
  ).then(() => setEditing(null));
  const togglePin = (note) => run(
    request(`/api/bookings/${id}/notes/${note.id}`, { method: 'PUT', body: { text: note.text, pinned: !note.pinned, version: note.version } }),
  );
  const removeNote = (note) => {
    if (window.confirm('Удалить заметку?')) run(request(`/api/bookings/${id}/notes/${note.id}`, { method: 'DELETE' }), 'Заметка удалена');
  };
  const canModify = (note) => can('MANAGER') && (can('ADMIN') || note.authorId === user.id);

  return (
    <Modal
      wide
      title={booking ? `Запись ${booking.code}` : 'Запись'}
      onClose={onClose}
      footer={(
        <>
          {booking && can('MANAGER') && !booking.archived && (
            <button className="btn" type="button" onClick={() => onEdit(booking)}>Изменить</button>
          )}
          <button className="btn primary" type="button" onClick={onClose}>Закрыть</button>
        </>
      )}
    >
      <ErrorBox error={error} />
      {booking && (
        <dl className="details">
          <dt>Клиент</dt><dd>{booking.client.fullName}, {booking.client.phone}</dd>
          <dt>Автомобиль</dt><dd>{booking.carNumber} {booking.carModel && `· ${booking.carModel}`}</dd>
          <dt>Услуга</dt><dd>{booking.serviceType.name}</dd>
          <dt>Дата и время</dt><dd>{formatLocal(booking.scheduledAt)}</dd>
          <dt>Цена</dt><dd>{formatMoney(booking.price)} · {booking.paid ? 'оплачено' : 'не оплачено'}</dd>
          <dt>Статус</dt><dd><StatusBadge code={booking.status} title={booking.statusTitle} /></dd>
          <dt>Риск неоплаты</dt><dd><RiskBadge code={booking.paymentRisk} title={booking.paymentRiskTitle} /></dd>
          <dt>Теги</dt><dd><Tags tags={booking.tags} /></dd>
          <dt>Пожелания</dt><dd>{booking.customerComment || '—'}</dd>
          <dt>Архив</dt><dd>{booking.archived ? `да, с ${formatInstant(booking.archivedAt)}` : 'нет'}</dd>
          <dt>Создана / изменена</dt><dd>{formatInstant(booking.createdAt)} / {formatInstant(booking.updatedAt)}</dd>
          <dt>UUID · version</dt><dd className="mono">{booking.id} · v{booking.version}</dd>
        </dl>
      )}

      <h3>Заметки ({notes.length})</h3>
      {notes.length === 0 && <p className="muted">Заметок пока нет.</p>}
      {notes.map((note) => (
        <div key={note.id} className={`note ${note.pinned ? 'pinned' : ''}`}>
          <div className="note-head">
            {note.pinned && <strong>📌 Закреплена</strong>}
            <span>{note.authorName}</span>
            <span>{formatInstant(note.createdAt)}</span>
            {note.updatedAt !== note.createdAt && <span>(изменена {formatInstant(note.updatedAt)})</span>}
            <span className="spacer" />
            {canModify(note) && editing?.id !== note.id && (
              <>
                <button className="btn ghost small" type="button" onClick={() => togglePin(note)}>{note.pinned ? 'Открепить' : 'Закрепить'}</button>
                <button className="btn ghost small" type="button" onClick={() => setEditing({ id: note.id, text: note.text })}>Изменить</button>
                <button className="btn ghost small" type="button" onClick={() => removeNote(note)}>Удалить</button>
              </>
            )}
          </div>
          {editing?.id === note.id ? (
            <div className="auth-form">
              <textarea value={editing.text} maxLength={2000} onChange={(e) => setEditing({ ...editing, text: e.target.value })} />
              <div className="row">
                <button className="btn primary small" type="button" disabled={!editing.text.trim()} onClick={() => saveEdit(note)}>Сохранить</button>
                <button className="btn small" type="button" onClick={() => setEditing(null)}>Отмена</button>
              </div>
            </div>
          ) : (
            <div className="note-text">{note.text}</div>
          )}
        </div>
      ))}

      {can('MANAGER') && (
        <form className="auth-form" onSubmit={addNote}>
          <textarea value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} placeholder="Новая заметка…" />
          <div className="row">
            <label className="check"><input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} /> Закрепить</label>
            <span className="spacer" />
            <button className="btn primary" type="submit" disabled={!text.trim()}>Добавить заметку</button>
          </div>
        </form>
      )}
    </Modal>
  );
}
