import { useAuth } from '../auth.jsx';
import PasswordForm from '../components/PasswordForm.jsx';

// показывается сразу после входа с временным паролем: без смены работать нельзя
export default function ChangePasswordPage() {
  const { user, logout } = useAuth();
  return (
    <div className="auth-screen">
      <div className="auth-card">
        <h1>Смена временного пароля</h1>
        <p className="muted">
          {user.fullName}, перед началом работы задайте собственный пароль: не меньше 12 символов,
          заглавная и строчная буква, цифра и спецсимвол, без пробелов.
        </p>
        <PasswordForm submitLabel="Сменить пароль" />
        <button className="btn ghost" type="button" onClick={logout}>Выйти</button>
      </div>
    </div>
  );
}
