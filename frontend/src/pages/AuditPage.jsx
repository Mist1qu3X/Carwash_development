import { useEffect, useState } from 'react';
import { request } from '../api.js';
import { useMeta } from '../meta.jsx';
import { formatInstant } from '../format.js';
import { ErrorBox, Pager, SortTh, nextSort } from '../components/common.jsx';

const ENTITY_TYPES = { BOOKING: 'Запись', NOTE: 'Заметка', CLIENT: 'Клиент', SERVICE_TYPE: 'Услуга', USER: 'Пользователь' };
const EMPTY = { q: '', eventType: '', entityType: '', from: '', to: '' };

// datetime-local -> ISO Instant для параметров from/to
const toInstant = (value) => (value ? new Date(value).toISOString() : '');

// журнал аудита (только ADMIN)
export default function AuditPage() {
  const { meta } = useMeta();
  const [draft, setDraft] = useState(EMPTY);
  const [filter, setFilter] = useState(EMPTY);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [sort, setSort] = useState('createdAt,desc');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    request('/api/admin/audit', {
      query: { ...filter, from: toInstant(filter.from), to: toInstant(filter.to), page, size, sort },
    })
      .then((result) => {
        setData(result);
        setError(null);
      })
      .catch(setError);
  }, [filter, page, size, sort]);

  const set = (name) => (e) => setDraft({ ...draft, [name]: e.target.value });
  const changeSort = (field) => setSort(nextSort(sort, field));

  return (
    <>
      <div className="page-head"><h1>Журнал аудита</h1></div>
      <form className="panel filters" onSubmit={(e) => { e.preventDefault(); setFilter(draft); setPage(0); }}>
        <label className="grow">Поиск
          <input type="search" value={draft.q} onChange={set('q')} placeholder="Описание, email или correlation ID" />
        </label>
        <label>Событие
          <select value={draft.eventType} onChange={set('eventType')}>
            <option value="">Все</option>
            {meta.auditEventTypes.map((t) => <option key={t.code} value={t.code}>{t.title}</option>)}
          </select>
        </label>
        <label>Сущность
          <select value={draft.entityType} onChange={set('entityType')}>
            <option value="">Все</option>
            {Object.entries(ENTITY_TYPES).map(([code, title]) => <option key={code} value={code}>{title}</option>)}
          </select>
        </label>
        <label>С
          <input type="datetime-local" value={draft.from} onChange={set('from')} />
        </label>
        <label>По
          <input type="datetime-local" value={draft.to} onChange={set('to')} />
        </label>
        <div className="row">
          <button className="btn primary" type="submit">Найти</button>
          <button className="btn" type="button" onClick={() => { setDraft(EMPTY); setFilter(EMPTY); setPage(0); }}>Сбросить</button>
        </div>
      </form>
      <div className="panel">
        <ErrorBox error={error} />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <SortTh field="createdAt" sort={sort} onSort={changeSort}>Время</SortTh>
                <SortTh field="userEmail" sort={sort} onSort={changeSort}>Пользователь</SortTh>
                <SortTh field="eventType" sort={sort} onSort={changeSort}>Событие</SortTh>
                <SortTh field="entityType" sort={sort} onSort={changeSort}>Сущность</SortTh>
                <th>Описание</th>
                <th>IP</th>
                <th>Correlation ID</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((e) => (
                <tr key={e.id}>
                  <td className="nowrap">{formatInstant(e.createdAt)}</td>
                  <td>{e.userEmail || '—'}</td>
                  <td>{e.eventTitle}</td>
                  <td>
                    {ENTITY_TYPES[e.entityType] || e.entityType || '—'}
                    {e.entityId && <div className="mono muted">{e.entityId.slice(0, 8)}…</div>}
                  </td>
                  <td>{e.description}</td>
                  <td className="mono">{e.ipAddress || '—'}</td>
                  <td className="mono">{e.correlationId ? `${e.correlationId.slice(0, 8)}…` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {data?.content.length === 0 && <div className="empty">Событий не найдено</div>}
        </div>
        <Pager data={data} onPage={setPage} size={size} onSize={(n) => { setSize(n); setPage(0); }} />
      </div>
    </>
  );
}
