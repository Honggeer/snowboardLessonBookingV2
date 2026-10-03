import { render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });
const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;

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
