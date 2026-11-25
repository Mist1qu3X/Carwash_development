import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { request } from '../api.js';
import { useAuth } from '../auth.jsx';
import { formatInstant } from '../format.js';
import { ErrorBox, Pager, SortTh, nextSort } from '../components/common.jsx';
import ClientForm from '../components/ClientForm.jsx';

export default function ClientsPage() {
  const { can } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [applied, setApplied] = useState('');
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('fullName,asc');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null); // null | {} (новый) | client
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    request('/api/clients', { query: { q: applied, page, size: 20, sort } })
      .then((result) => {
        setData(result);
        setError(null);
      })
      .catch(setError);
  }, [applied, page, sort, reloadKey]);

  const changeSort = (field) => setSort(nextSort(sort, field));

  return (
    <>
      <div className="page-head">
        <h1>Клиенты</h1>
        <span className="spacer" />
        {can('MANAGER') && <button className="btn primary" type="button" onClick={() => setEditing({})}>+ Новый клиент</button>}
      </div>
      <form className="panel row" onSubmit={(e) => { e.preventDefault(); setApplied(q); setPage(0); }}>
        <input type="search" className="spacer" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Имя, телефон или email" />
        <button className="btn primary" type="submit">Найти</button>
      </form>
      <div className="panel">
        <ErrorBox error={error} />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <SortTh field="fullName" sort={sort} onSort={changeSort}>ФИО</SortTh>
                <SortTh field="phone" sort={sort} onSort={changeSort}>Телефон</SortTh>
                <th>Email</th>
                <SortTh field="createdAt" sort={sort} onSort={changeSort}>Добавлен</SortTh>
                <th className="right">Действия</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((c) => (
                <tr key={c.id}>
                  <td>{c.fullName}</td>
                  <td className="nowrap">{c.phone}</td>
                  <td>{c.email || '—'}</td>
                  <td className="nowrap">{formatInstant(c.createdAt)}</td>
                  <td className="actions">
                    <button className="btn small" type="button"
                            onClick={() => navigate(`/bookings?clientId=${c.id}&clientName=${encodeURIComponent(c.fullName)}`)}>
                      Записи клиента
                    </button>
                    {can('MANAGER') && <button className="btn small" type="button" onClick={() => setEditing(c)}>Изменить</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data?.content.length === 0 && <div className="empty">Клиенты не найдены</div>}
        </div>
        <Pager data={data} onPage={setPage} />
      </div>
      {editing && (
        <ClientForm
          client={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            setReloadKey((k) => k + 1);
          }}
        />
      )}
    </>
  );
}
