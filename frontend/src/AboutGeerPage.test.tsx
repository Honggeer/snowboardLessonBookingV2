import { render, screen, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';
import { CoachPresentation } from './AboutGeerPage';
import userEvent from '@testing-library/user-event';
import type { CoachProfile } from './coachProfileApi';

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });
const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;

function anonymousSession() {
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/coach-profile' ? json({ published: false, version: 0, content: {}, media: {} }) :
        json({}, 401)));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

it('places the public link in the login subtitle and preserves input on return', async () => {
  anonymousSession();
  render(<App />);
  const prompt = screen.getByText('第一次访问？').closest('p') as HTMLElement;
  expect(screen.getByRole('heading', { name: '欢迎回来' }).nextElementSibling).toBe(prompt);
  const link = within(prompt).getByRole('link', { name: '关于 GEER' });
  expect(link.getAttribute('href')).toBe('/about-geer');
  expect(link.textContent).toContain('↗');
  expect(screen.getAllByRole('link', { name: '关于 GEER' })).toHaveLength(1);
  expect(screen.queryByRole('button', { name: /关于 GEER/ })).toBeNull();
  expect(screen.queryByText('继续你的滑雪旅程')).toBeNull();
  await userEvent.type(screen.getByLabelText('邮箱'), 'visitor@example.test');
  await userEvent.type(screen.getByLabelText('密码'), 'test-password');
  await userEvent.click(link);
  expect(await screen.findByText('教练正在准备个人主页，敬请期待。')).toBeTruthy();
  expect(window.location.pathname).toBe('/about-geer');
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  expect(screen.getByLabelText('邮箱')).toHaveProperty('value', 'visitor@example.test');
  expect(screen.getByLabelText('密码')).toHaveProperty('value', 'test-password');
});

it('supports keyboard entry from the login subtitle without changing registration or recovery copy', async () => {
  anonymousSession();
  render(<App />);
  const link = screen.getByRole('link', { name: '关于 GEER' });
  link.focus();
  await userEvent.keyboard('{Enter}');
  expect(await screen.findByText('教练正在准备个人主页，敬请期待。')).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await userEvent.click(screen.getByRole('button', { name: '创建账号' }));
  expect(screen.getByText('与更多滑雪爱好者一起，刻下属于你的轨迹')).toBeTruthy();
  expect(screen.queryByRole('link', { name: '关于 GEER' })).toBeNull();
  expect(screen.queryByText('第一次访问？')).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await userEvent.click(screen.getByRole('button', { name: '忘记密码' }));
  expect(screen.getByText('输入账号邮箱，获取找回验证码。')).toBeTruthy();
  expect(screen.queryByRole('link', { name: '关于 GEER' })).toBeNull();
});

it('opens the public coach profile directly without booking data or login', async () => {
  window.history.replaceState(null, '', '/about-geer');
  const fetchMock = vi.fn((url: string) => Promise.resolve(url === '/api/coach-profile'
    ? json({ published: true, version: 1, content: { displayName: 'GEER', tagline: '我的滑行、教学与热爱。',
      bio: '从每一次练习中找到进步。', casiLevel: '用户提供的证书说明', xhsAccount: '@geer',
      xhsUrl: 'https://www.xiaohongshu.com/user/profile/test', wechatId: 'test-geer' },
      media: { HERO: { id: 'hero', url: '/test-hero.png', expiresAt: '2099-01-01T00:00:00Z' } } })
    : url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) : json({}, 401)));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  expect(await screen.findByText('你好，我是 GEER。')).toBeTruthy();
  expect(screen.getByText('从每一次练习中找到进步。')).toBeTruthy();
  expect(screen.getByRole('link', { name: /小红书/ }).getAttribute('href')).toContain('xiaohongshu.com');
  expect(fetchMock.mock.calls.some(([url]) => url === '/api/courses' || url === '/api/bookings/mine')).toBe(false);
});

it('offers retry when the public profile fails to load', async () => {
  window.history.replaceState(null, '', '/about-geer');
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(url === '/api/coach-profile'
    ? json({ detail: '暂时无法读取主页' }, 503) : json({}, 401))));
  render(<App />);
  expect(await screen.findByRole('button', { name: '重新加载' })).toBeTruthy();
});

it('initializes the session cookie before requesting the current account', async () => {
  let complete: (value: Response) => void = () => {};
  const token = new Promise<Response>((resolve) => { complete = resolve; });
  const fetchMock = vi.fn((url: string) => url === '/api/auth/csrf' ? token : Promise.resolve(json({}, 401)));
  vi.stubGlobal('fetch', fetchMock); render(<App />);
  expect(fetchMock.mock.calls.some(([url]) => url === '/api/auth/me')).toBe(false);
  complete(json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }));
});

const profile = (content: Record<string, string>, media: CoachProfile['media'] = {}): CoachProfile =>
  ({ published: true, version: 1, publishedVersion: 1, content: { displayName: 'GEER', ...content }, media });

it.each(['', 'legacy-wechat'])('shows only the WeChat QR and supports enlargement without an account (%s)', async (wechatId) => {
  render(<CoachPresentation profile={profile({ wechatId }, { WECHAT_QR: { id: 'qr', url: '/qr.png', expiresAt: '2099-01-01T00:00:00Z' } })} onBook={vi.fn()} />);
  expect(screen.getByRole('img', { name: 'GEER 微信二维码' })).toBeTruthy();
  expect(screen.queryByText('legacy-wechat')).toBeNull();
  expect(screen.queryByRole('button', { name: '复制微信号' })).toBeNull();
  const open = screen.getByRole('button', { name: '放大微信二维码' });
  await userEvent.click(open);
  expect(screen.getByRole('dialog', { name: '联系 GEER' })).toBeTruthy();
  expect(screen.queryByText(/微信号：/)).toBeNull();
  await userEvent.keyboard('{Escape}');
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(document.activeElement).toBe(open);
});

it('shows social account names when no links are supplied', () => {
  render(<CoachPresentation profile={profile({ xhsAccount: '小红书 自由昵称', douyinAccount: '抖音号 @GEER' })} onBook={vi.fn()} />);
  expect(screen.getByText('小红书 自由昵称')).toBeTruthy();
  expect(screen.getByText('抖音号 @GEER')).toBeTruthy();
  expect(screen.queryByRole('link', { name: /查看主页/ })).toBeNull();
});

it('opens HTTP and non-platform links without requiring account names', () => {
  render(<CoachPresentation profile={profile({ xhsUrl: 'http://short.example.test/one', douyinUrl: 'https://share.example.test/two' })} onBook={vi.fn()} />);
  expect(screen.getByRole('link', { name: '小红书 · 查看主页' }).getAttribute('href')).toBe('http://short.example.test/one');
  expect(screen.getByRole('link', { name: '抖音 · 查看主页' }).getAttribute('href')).toBe('https://share.example.test/two');
  expect(screen.getByRole('link', { name: '抖音 · 查看主页' }).getAttribute('rel')).toBe('noopener noreferrer');
});

it('displays non-address text and other protocols without turning them into navigation', () => {
  render(<CoachPresentation profile={profile({ xhsUrl: '主页链接稍后补充', douyinUrl: 'javascript:alert(1)' })} onBook={vi.fn()} />);
  expect(screen.getByText('主页链接稍后补充')).toBeTruthy();
  expect(screen.getByText('javascript:alert(1)')).toBeTruthy();
  expect(screen.queryByRole('link', { name: /查看主页/ })).toBeNull();
});

it('hides empty contacts and a legacy WeChat account without a QR', () => {
  render(<CoachPresentation profile={profile({ wechatId: 'legacy-wechat' })} onBook={vi.fn()} />);
  expect(screen.queryByRole('heading', { name: '在这里找到我' })).toBeNull();
});
