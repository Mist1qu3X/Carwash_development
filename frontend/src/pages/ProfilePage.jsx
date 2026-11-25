import { useAuth } from '../auth.jsx';
import { useMeta } from '../meta.jsx';
import { formatInstant } from '../format.js';
import PasswordForm from '../components/PasswordForm.jsx';

export default function ProfilePage() {
  const { user } = useAuth();
  const { roleTitle } = useMeta();
  return (
    <>
      <div className="page-head"><h1>Профиль</h1></div>
      <div className="charts">
        <div className="panel">
          <h3>Учётная запись</h3>
          <dl className="details">
            <dt>Имя</dt><dd>{user.fullName}</dd>
            <dt>Email</dt><dd>{user.email}</dd>
            <dt>Роли</dt><dd>{user.roles.map(roleTitle).join(', ')}</dd>
            <dt>Последний вход</dt><dd>{formatInstant(user.lastLoginAt)}</dd>
          </dl>
        </div>
        <div className="panel">
          <h3>Смена пароля</h3>
          <p className="muted small-text">После смены пароля все остальные сессии будут завершены.</p>
          <PasswordForm />
        </div>
      </div>
    </>
  );
}
