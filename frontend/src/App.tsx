import { useEffect, useState, type FormEvent } from 'react';
import './style.css';

type View = 'login' | 'register' | 'check-email' | 'verify' | 'verified' | 'account';
type Level = '零基础' | '入门' | '进阶';
type Account = { id: string; role: 'STUDENT' | 'COACH'; name: string; level: Level | null };
type Csrf = { token: string; headerName: string };

class ApiError extends Error {
  constructor(readonly status: number, message: string) { super(message); }
}

async function readJson(response: Response) {
  return response.json().catch(() => ({}));
}

export default function App() {
  const initialToken = window.location.hash.startsWith('#verify?token=')
    ? new URLSearchParams(window.location.hash.slice('#verify?'.length)).get('token') : null;
  const [verificationToken] = useState(initialToken);
  const [view, setView] = useState<View>(initialToken ? 'verify' : 'login');
  const [csrf, setCsrf] = useState<Csrf | null>(null);
  const [account, setAccount] = useState<Account | null>(null);
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [level, setLevel] = useState<Level>('零基础');
  const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  async function refreshCsrf() {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('无法建立安全连接，请稍后重试。');
    const next = await readJson(response) as Csrf;
    setCsrf(next);
    return next;
  }

  useEffect(() => {
    if (initialToken) window.history.replaceState(null, '', window.location.pathname + window.location.search);
    let active = true;
    Promise.all([
      fetch('/api/auth/csrf', { credentials: 'same-origin' }).then(readJson),
      fetch('/api/auth/me', { credentials: 'same-origin' }).then(async (response) => response.ok ? readJson(response) : null),
    ]).then(([token, current]) => {
      if (!active) return;
      setCsrf(token as Csrf);
      if (current && !initialToken) { setAccount(current as Account); setView('account'); }
    }).catch(() => { if (active) setMessage('无法连接服务，请稍后重试。'); });
    return () => { active = false; };
  }, []);

  async function post(path: string, body?: object) {
    const currentCsrf = csrf ?? await refreshCsrf();
    const response = await fetch(path, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [currentCsrf.headerName]: currentCsrf.token },
      body: JSON.stringify(body ?? {}),
    });
    const result = await readJson(response);
    if (!response.ok) {
      if (response.status === 429) throw new ApiError(429, '操作太频繁，请稍后再试。');
      if (response.status === 410) throw new ApiError(410, '验证链接已失效，请重新发送。');
      throw new ApiError(response.status, (result as { detail?: string }).detail ?? '操作未完成，请检查输入后重试。');
    }
    return result;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage('');
    try {
      if (view === 'login') {
        const current = await post('/api/auth/login', { email, password }) as Account;
        setAccount(current);
        setPassword('');
        setView('account');
        await refreshCsrf();
      } else if (view === 'register') {
        const passwordLength = Array.from(password).length;
        if (passwordLength < 8 || passwordLength > 128) {
          setMessage('密码需为 8–128 个字符。');
          return;
        }
        await post('/api/auth/register', { name, level, email, password });
        setPassword('');
        setView('check-email');
      } else if (view === 'verify') {
        await post('/api/auth/email-verification', { token: verificationToken });
        setView('verified');
      } else if (view === 'check-email') {
        await post('/api/auth/email-verification/resend', { email });
        setMessage('请检查邮箱；如果可以重发，我们会尽快处理。');
      }
    } catch (error) {
      if (view === 'verify' && error instanceof ApiError && error.status === 410) setView('check-email');
      setMessage(error instanceof Error ? error.message : '操作未完成，请重试。');
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    setMessage('');
    try {
      await post('/api/auth/logout');
      setAccount(null);
      setView('login');
      await refreshCsrf();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '退出失败，请重试。');
    } finally { setBusy(false); }
  }

  return (
    <main className={'auth-layout view-' + view}>
      <section className="brand-panel" aria-label="GEER 单板教学">
        <picture>
          <source media="(max-width: 900px)" srcSet={view === 'register' ? '/images/geer-blue-mobile-register.png' : '/images/geer-blue-mobile-login.png'} />
          <img className="brand-artwork" src="/images/geer-blue-desktop.png" alt="GEER 品牌视觉：雪道上的高速刻滑与锐角字标" />
        </picture>
      </section>

      <section className="form-panel">
        <div className="form-wrap">
          {view === 'account' && account ? (
            <>
              <h1>欢迎回来，{account.name}</h1>
              <p className="section-intro">{account.role === 'COACH' ? '教练账号已登录。' : '准备好留下新的雪道轨迹了吗？'}</p>
              <div className="account-card"><span>当前身份</span><strong>{account.role === 'COACH' ? '教练' : '学员'}</strong>{account.level && <small>当前水平 · {account.level}</small>}</div>
              <button className="primary-button" type="button" disabled={busy} onClick={logout}>退出登录</button>
            </>
          ) : view === 'verified' ? (
            <>
              <h1>邮箱已验证</h1>
              <p className="section-intro">现在可以用邮箱和密码登录。</p>
              <button className="primary-button" type="button" onClick={() => { setView('login'); setMessage(''); }}>前往登录</button>
            </>
          ) : (
            <>
              <h1>{view === 'register' ? '加入 GEER' : view === 'login' ? '欢迎回来' : view === 'verify' ? '验证你的邮箱' : '请检查邮箱'}</h1>
              <p className="section-intro">{view === 'register' ? '与更多滑雪爱好者一起，刻下属于你的轨迹' : view === 'login' ? '继续你的滑雪旅程' : view === 'verify' ? '点击下方按钮完成邮箱验证。' : '如果该邮箱可以注册，验证链接会发送到你的邮箱。'}</p>
              <form onSubmit={submit}>
                {view === 'register' && (
                  <>
                    <label htmlFor="name">姓名</label>
                    <input id="name" autoComplete="name" required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} placeholder="请输入姓名" />
                    <fieldset className="levels"><legend>当前水平</legend><div className="level-options">
                      {(['零基础', '入门', '进阶'] as Level[]).map((option) => (
                        <label key={option} className={'level-option ' + (level === option ? 'selected' : '')}>
                          <input type="radio" name="level" value={option} checked={level === option} onChange={() => setLevel(option)} />
                          {option}
                        </label>
                      ))}
                    </div></fieldset>
                  </>
                )}
                {(view === 'login' || view === 'register' || view === 'check-email') && (
                  <><label htmlFor="email">邮箱</label><input id="email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="请输入邮箱地址" /></>
                )}
                {(view === 'login' || view === 'register') && (
                  <>
                    <label htmlFor="password">密码</label>
                    <div className="password-control">
                      <input id="password" type={visible ? 'text' : 'password'} autoComplete={view === 'register' ? 'new-password' : 'current-password'} required value={password} onChange={(event) => setPassword(event.target.value)} placeholder={view === 'register' ? '请输入密码（8–128 个字符）' : '请输入密码'} />
                      <button type="button" className="visibility-button" aria-label={visible ? '隐藏密码' : '显示密码'} onClick={() => setVisible((value) => !value)}>
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" /><circle cx="12" cy="12" r="2.5" />{!visible && <path d="M3 3 21 21" />}</svg>
                      </button>
                    </div>
                  </>
                )}
                <button className="primary-button" type="submit" disabled={busy || !csrf}>{busy ? '处理中…' : view === 'login' ? '登录' : view === 'register' ? '创建账号' : view === 'verify' ? '验证邮箱' : '重新发送验证邮件'}</button>
              </form>
              {(view === 'login' || view === 'register') && <p className="form-switch">{view === 'register' && '已有账号？'} <button type="button" className="text-button" onClick={() => { setView(view === 'login' ? 'register' : 'login'); setMessage(''); }}>{view === 'login' ? '创建账号' : '登录'}</button></p>}
              {(view === 'check-email' || view === 'verify') && <p className="form-switch"><button type="button" className="text-button" onClick={() => { setView('login'); setMessage(''); }}>返回登录</button></p>}
            </>
          )}
          {message && <p role="alert" className="feedback">{message}</p>}
        </div>
        <div className="panel-bottom"><span>MORE THAN A RIDE</span></div>
      </section>
    </main>
  );
}
