import { useEffect, useState } from 'react';
import { fieldErrors, request } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useMeta } from '../meta.jsx';
import { useToast } from '../toast.jsx';
import { formatInstant } from '../format.js';
import Modal from '../components/Modal.jsx';
import { ErrorBox, Field, Pager, SortTh, nextSort } from '../components/common.jsx';

// администрирование пользователей (только ADMIN)
export default function UsersPage() {
  const { meta, roleTitle } = useMeta();
  const notify = useToast();
  const [draft, setDraft] = useState({ q: '', active: '', role: '' });
  const [filter, setFilter] = useState(draft);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('email,asc');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [password, setPassword] = useState(null); // { email, temporaryPassword }
  const [reloadKey, setReloadKey] = useState(0);
  const reload = () => setReloadKey((k) => k + 1);

  useEffect(() => {
    request('/api/admin/users', { query: { ...filter, page, size: 20, sort } })
      .then((result) => {
        setData(result);
        setError(null);
      })
      .catch(setError);
  }, [filter, page, sort, reloadKey]);

  async function resetPassword(user) {
    if (!window.confirm(`Сбросить пароль пользователя ${user.email}? Все его сессии будут завершены.`)) return;
    try {
      const result = await request(`/api/admin/users/${user.id}/reset-password`, { method: 'POST' });
      setPassword({ email: user.email, temporaryPassword: result.temporaryPassword });
      reload();
    } catch (err) {
      notify(err.message, 'error');
    }
  }

  const set = (name) => (e) => setDraft({ ...draft, [name]: e.target.value });

  return (
    <>
      <div className="page-head">
        <h1>Пользователи</h1>
        <span className="spacer" />
        <button className="btn primary" type="button" onClick={() => setEditing({})}>+ Новый пользователь</button>
      </div>
      <form className="panel filters" onSubmit={(e) => { e.preventDefault(); setFilter(draft); setPage(0); }}>
        <label className="grow">Поиск
          <input type="search" value={draft.q} onChange={set('q')} placeholder="Email или имя" />
        </label>
        <label>Активность
          <select value={draft.active} onChange={set('active')}>
            <option value="">Все</option>
            <option value="true">Активные</option>
            <option value="false">Отключённые</option>
          </select>
        </label>
        <label>Роль
          <select value={draft.role} onChange={set('role')}>
            <option value="">Любая</option>
            {meta.roles.map((r) => <option key={r.code} value={r.code}>{r.title}</option>)}
          </select>
        </label>
        <div className="row"><button className="btn primary" type="submit">Найти</button></div>
      </form>
      <div className="panel">
        <ErrorBox error={error} />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <SortTh field="email" sort={sort} onSort={(f) => setSort(nextSort(sort, f))}>Email</SortTh>
                <SortTh field="fullName" sort={sort} onSort={(f) => setSort(nextSort(sort, f))}>Имя</SortTh>
                <th>Роли</th>
                <th>Состояние</th>
                <SortTh field="lastLoginAt" sort={sort} onSort={(f) => setSort(nextSort(sort, f))}>Последний вход</SortTh>
                <th className="right">Действия</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((u) => (
                <tr key={u.id} className={u.active ? undefined : 'archived'}>
                  <td>{u.email}</td>
                  <td>{u.fullName}</td>
                  <td>{u.roles.map((r) => <span key={r} className="tag">{roleTitle(r)}</span>)}</td>
                  <td>
                    {u.active ? <span className="badge ok">активен</span> : <span className="badge">отключён</span>}{' '}
                    {u.locked && <span className="badge bad">заблокирован до {formatInstant(u.lockedUntil)}</span>}{' '}
                    {u.mustChangePassword && <span className="badge warn">временный пароль</span>}
                  </td>
                  <td className="nowrap">{formatInstant(u.lastLoginAt)}</td>
                  <td className="actions">
                    <button className="btn small" type="button" onClick={() => setEditing(u)}>Изменить</button>
                    <button className="btn small" type="button" onClick={() => resetPassword(u)}>Сбросить пароль</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager data={data} onPage={setPage} />
      </div>

      {editing && (
        <UserForm
          user={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={(result) => {
            setEditing(null);
            if (result?.temporaryPassword) {
              setPassword({ email: result.user.email, temporaryPassword: result.temporaryPassword });
            } else {
              notify('Пользователь сохранён', 'success');
            }
            reload();
          }}
        />
      )}
      {password && <TemporaryPassword {...password} onClose={() => setPassword(null)} />}
    </>
  );
}

function UserForm({ user, onClose, onSaved }) {
  const { user: me } = useAuth();
  const { meta } = useMeta();
  const [form, setForm] = useState({
    email: user?.email || '',
    fullName: user?.fullName || '',
    roles: user?.roles || ['VIEWER'],
    active: user ? user.active : true,
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);

  const toggleRole = (code) => setForm({
    ...form,
    roles: form.roles.includes(code) ? form.roles.filter((r) => r !== code) : [...form.roles, code],
  });

  async function submit(e) {
    e.preventDefault();
    try {
      const result = user
        ? await request(`/api/admin/users/${user.id}`, { method: 'PUT', body: { ...form, version: user.version } })
        : await request('/api/admin/users', { method: 'POST', body: { email: form.email, fullName: form.fullName, roles: form.roles } });
      onSaved(result);
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(err);
    }
  }

  return (
    <Modal
      title={user ? `Пользователь ${user.email}` : 'Новый пользователь'}
      onClose={onClose}
      footer={(
        <>
          <button className="btn" type="button" onClick={onClose}>Отмена</button>
          <button className="btn primary" type="submit" form="user-form">Сохранить</button>
        </>
      )}
    >
      <form id="user-form" className="form-grid" onSubmit={submit}>
        <Field label="Email (логин)" error={errors.email}>
          <input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Имя" error={errors.fullName}>
          <input required value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
        </Field>
        <Field label="Роли" error={errors.roles} className="full">
          <span className="roles-box">
            {meta.roles.map((r) => (
              <label key={r.code} className="check">
                <input type="checkbox" checked={form.roles.includes(r.code)} onChange={() => toggleRole(r.code)} /> {r.title}
              </label>
            ))}
          </span>
        </Field>
        {user && (
          <label className="check full">
            <input type="checkbox" checked={form.active} disabled={user.id === me.id}
                   onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Учётная запись активна (отключённый пользователь не может войти)
          </label>
        )}
        {!user && <p className="muted full">Временный пароль сгенерируется автоматически и будет показан один раз.</p>}
        <div className="full"><ErrorBox error={error} /></div>
      </form>
    </Modal>
  );
}

function TemporaryPassword({ email, temporaryPassword, onClose }) {
  const notify = useToast();
  const copy = () => navigator.clipboard.writeText(temporaryPassword).then(() => notify('Пароль скопирован', 'success'));
  return (
    <Modal
      title="Временный пароль"
      onClose={onClose}
      footer={(
        <>
          <button className="btn" type="button" onClick={copy}>Скопировать</button>
          <button className="btn primary" type="button" onClick={onClose}>Готово</button>
        </>
      )}
    >
      <p>Передайте пароль пользователю <strong>{email}</strong>. Он показывается только сейчас;
        при первом входе пользователь обязан его сменить.</p>
      <div className="password-box">{temporaryPassword}</div>
    </Modal>
  );
}
