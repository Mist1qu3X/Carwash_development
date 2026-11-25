import { useState } from 'react';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { ErrorBox } from './common.jsx';

export default function PasswordForm({ submitLabel = 'Сохранить' }) {
  const { changePassword } = useAuth();
  const notify = useToast();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', repeatPassword: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    if (form.newPassword !== form.repeatPassword) {
      setError('Новый пароль и повтор не совпадают');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await changePassword(form.currentPassword, form.newPassword);
      setForm({ currentPassword: '', newPassword: '', repeatPassword: '' });
      notify('Пароль изменён', 'success');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <label>Текущий пароль
        <input type="password" required autoComplete="current-password" value={form.currentPassword} onChange={set('currentPassword')} />
      </label>
      <label>Новый пароль
        <input type="password" required minLength={12} autoComplete="new-password" value={form.newPassword} onChange={set('newPassword')} />
      </label>
      <label>Повторите новый пароль
        <input type="password" required minLength={12} autoComplete="new-password" value={form.repeatPassword} onChange={set('repeatPassword')} />
      </label>
      <ErrorBox error={error} />
      <button className="btn primary" type="submit" disabled={busy}>{busy ? 'Сохранение…' : submitLabel}</button>
    </form>
  );
}
