import { useState } from 'react';
import { useAuth } from '../auth.jsx';
import { ErrorBox } from '../components/common.jsx';

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
    } catch (err) {
      setError(err);
      setPassword('');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-screen">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-logo" aria-hidden="true">CW</div>
        <h1>Автомойка</h1>
        <p className="muted">Вход для сотрудников</p>
        <label>Email
          <input type="email" required autoComplete="username" value={email}
                 onChange={(e) => setEmail(e.target.value)} placeholder="admin@example.org" />
        </label>
        <label>Пароль
          <input type="password" required autoComplete="current-password" value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </label>
        <ErrorBox error={error} />
        <button className="btn primary wide" type="submit" disabled={busy}>{busy ? 'Вход…' : 'Войти'}</button>
      </form>
    </div>
  );
}
