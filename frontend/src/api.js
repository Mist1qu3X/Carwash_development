// Обёртка над fetch для REST API.
// access-токен хранится только в памяти (не в localStorage),
// refresh-токен лежит в HttpOnly-cookie, которую JavaScript не видит.

const session = { accessToken: null };
let refreshInFlight = null;
const sessionLostListeners = new Set();

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Ошибка сервера (${status})`);
    this.status = status;
    this.code = body?.error;
    this.details = body?.details || [];
    this.correlationId = body?.correlationId;
  }
}

// ошибки по полям формы: { carNumber: 'сообщение', ... }
export function fieldErrors(error) {
  const result = {};
  for (const d of error?.details || []) {
    const field = String(d.field).split(/[.[]/)[0];
    if (!result[field]) result[field] = d.message;
  }
  return result;
}

export function onSessionLost(listener) {
  sessionLostListeners.add(listener);
  return () => sessionLostListeners.delete(listener);
}

function applyTokens(data) {
  session.accessToken = data.accessToken;
  return data.user;
}

// Обновление access-токена по cookie. Одновременные вызовы объединяются в один запрос,
// иначе второй запрос пришёл бы с уже отозванным refresh-токеном.
function refreshTokens() {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
      .then(async (res) => (res.ok ? applyTokens(await res.json()) : null))
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

export async function request(path, { method = 'GET', query, body, form, raw = false } = {}) {
  const url = new URL(path, window.location.origin);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    }
  }
  const send = () => {
    const headers = {};
    if (session.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
    let payload;
    if (form) {
      payload = form;
    } else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    return fetch(url, { method, headers, body: payload, credentials: 'same-origin' });
  };

  let res = await send();
  if (res.status === 401 && session.accessToken) {
    // access-токен истёк — пробуем обновить и повторить запрос один раз
    if (await refreshTokens()) {
      res = await send();
    } else {
      session.accessToken = null;
      sessionLostListeners.forEach((listener) => listener());
    }
  }
  if (!res.ok) {
    let data = null;
    try {
      data = await res.json();
    } catch {
      // тело не JSON
    }
    throw new ApiError(res.status, data);
  }
  if (raw) return res;
  if (res.status === 204) return null;
  return res.json();
}

export const auth = {
  async login(email, password) {
    return applyTokens(await request('/api/auth/login', { method: 'POST', body: { email, password } }));
  },
  // при загрузке страницы восстанавливаем сессию по refresh-cookie
  restore() {
    return refreshTokens();
  },
  async changePassword(currentPassword, newPassword) {
    return applyTokens(await request('/api/auth/change-password', {
      method: 'POST',
      body: { currentPassword, newPassword },
    }));
  },
  async logout() {
    try {
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
    } finally {
      session.accessToken = null;
    }
  },
};
