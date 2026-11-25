import { useState } from 'react';
import { request } from '../api.js';
import { useMeta } from '../meta.jsx';
import { useToast } from '../toast.jsx';

// панель массовых операций над выбранными записями
export default function BulkBar({ ids, onDone, onClear, onShowErrors }) {
  const { meta } = useMeta();
  const notify = useToast();
  const [status, setStatus] = useState('CONFIRMED');
  const [tag, setTag] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(action, extra = {}) {
    setBusy(true);
    try {
      const result = await request('/api/bookings/bulk', { method: 'POST', body: { ids, action, ...extra } });
      notify(`Готово: успешно ${result.succeededCount}, с ошибкой ${result.failedCount}`,
        result.failedCount ? 'error' : 'success');
      if (result.failedCount) onShowErrors(result);
      onDone();
    } catch (err) {
      notify(err.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="bulk-bar">
      <strong>Выбрано: {ids.length}</strong>
      <button className="btn small" type="button" disabled={busy} onClick={() => run('ARCHIVE')}>В архив</button>
      <button className="btn small" type="button" disabled={busy} onClick={() => run('RESTORE')}>Восстановить</button>
      <span className="row">
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Новый статус">
          {meta.statuses.map((s) => <option key={s.code} value={s.code}>{s.title}</option>)}
        </select>
        <button className="btn small" type="button" disabled={busy} onClick={() => run('CHANGE_STATUS', { status })}>Сменить статус</button>
      </span>
      <span className="row">
        <input value={tag} onChange={(e) => setTag(e.target.value)} placeholder="тег" aria-label="Тег" />
        <button className="btn small" type="button" disabled={busy || !tag.trim()} onClick={() => run('ADD_TAG', { tag: tag.trim() })}>Добавить тег</button>
        <button className="btn small" type="button" disabled={busy || !tag.trim()} onClick={() => run('REMOVE_TAG', { tag: tag.trim() })}>Удалить тег</button>
      </span>
      <span className="spacer" />
      <button className="btn ghost small" type="button" onClick={onClear}>Снять выделение</button>
    </div>
  );
}
