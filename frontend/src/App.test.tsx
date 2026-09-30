import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });

const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;

it('gets a CSRF token, logs in, and recovers the account after rendering', async () => {
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/auth/csrf') return Promise.resolve(json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }));
    if (url === '/api/auth/me') return Promise.resolve(json({ title: 'Unauthorized' }, 401));
    if (url === '/api/auth/login') {
      expect(options?.method).toBe('POST');
      expect((options?.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('csrf-1');
      return Promise.resolve(json({ id: '1', role: 'STUDENT', name: 'Geer', level: '入门' }));
    }
    throw new Error(url);
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.type(await screen.findByLabelText('邮箱'), 'geer@example.com');
  await userEvent.type(screen.getByLabelText('密码'), 'a long test password');
  await userEvent.click(screen.getByRole('button', { name: /^登录/ }));
  expect(await screen.findByText(/欢迎回来，Geer/)).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.anything());
});

it('registers with one of three levels and shows the neutral check-email state', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({}, 401) : json({ message: '请检查邮箱' }),
  )));
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建账号' }));
  expect(screen.getAllByRole('radio')).toHaveLength(3);
  await userEvent.type(screen.getByLabelText('姓名'), 'Geer');
  const middleLevel = screen.getByLabelText('入门') as HTMLInputElement;
  middleLevel.focus();
  await userEvent.keyboard(' ');
  expect(middleLevel.checked).toBe(true);
  await userEvent.type(screen.getByLabelText('邮箱'), 'geer@example.com');
  await userEvent.type(screen.getByLabelText('密码'), 'a long test password');
  await userEvent.click(screen.getByRole('button', { name: '创建账号' }));
  await waitFor(() => expect(screen.getByText(/请检查邮箱/)).toBeTruthy());
});

it('uses the approved GEER artwork and offers a verification flow', async () => {
  window.history.replaceState(null, '', '/#verify?token=sample');
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) : json({}, 401),
  )));
  render(<App />);
  expect(screen.getByRole('img', { name: /GEER/ }).getAttribute('src')).toMatch(/geer-blue-desktop/);
  expect(await screen.findByRole('button', { name: /^验证邮箱/ })).toBeTruthy();
  expect(window.location.hash).toBe('');
});

it('offers resend when a verification link has expired', async () => {
  window.history.replaceState(null, '', '/#verify?token=expired');
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/email-verification' ? json({ detail: 'expired' }, 410) : json({}, 401),
  )));
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: /^验证邮箱/ }));
  expect(await screen.findByRole('button', { name: /^重新发送验证邮件/ })).toBeTruthy();
  expect(screen.getByLabelText('邮箱')).toBeTruthy();
});

it('uses the approved blue visual assets and copy for the login screen', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) : json({}, 401),
  )));
  render(<App />);
  const artwork = screen.getByRole('img', { name: /GEER 品牌视觉/ });
  expect(artwork.getAttribute('src')).toBe('/images/geer-blue-desktop.png');
  expect(screen.getByRole('heading', { level: 1, name: '欢迎回来' })).toBeTruthy();
  expect(screen.queryByText(/刻出自己的/)).toBeNull();
  await userEvent.click(await screen.findByRole('button', { name: '创建账号' }));
  expect(screen.getByRole('heading', { name: '加入 GEER' })).toBeTruthy();
  expect(document.querySelector('source[media="(max-width: 900px)"]')?.getAttribute('srcset'))
    .toBe('/images/geer-blue-mobile-register.png');
});

it('submits an eight-character registration password and rejects seven', async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({}, 401) : json({ message: '请检查邮箱' }),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建账号' }));
  await userEvent.type(screen.getByLabelText('姓名'), 'Geer');
  await userEvent.type(screen.getByLabelText('邮箱'), 'geer@example.com');
  await userEvent.type(screen.getByLabelText('密码'), '1234567');
  await userEvent.click(screen.getByRole('button', { name: '创建账号' }));
  expect(fetchMock.mock.calls.some(([path]) => path === '/api/auth/register')).toBe(false);
  await userEvent.type(screen.getByLabelText('密码'), '8');
  await userEvent.click(screen.getByRole('button', { name: '创建账号' }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path === '/api/auth/register')).toBe(true));
});

it('counts Unicode code points at the 128-character registration limit', async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({}, 401) : json({ message: '请检查邮箱' }),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建账号' }));
  await userEvent.type(screen.getByLabelText('姓名'), 'Geer');
  await userEvent.type(screen.getByLabelText('邮箱'), 'geer@example.com');
  const passwordInput = screen.getByLabelText('密码');
  fireEvent.change(passwordInput, { target: { value: '😀'.repeat(129) } });
  await userEvent.click(screen.getByRole('button', { name: '创建账号' }));
  expect(fetchMock.mock.calls.some(([path]) => path === '/api/auth/register')).toBe(false);
  fireEvent.change(passwordInput, { target: { value: '😀'.repeat(128) } });
  await userEvent.click(screen.getByRole('button', { name: '创建账号' }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path === '/api/auth/register')).toBe(true));
});
