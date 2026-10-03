import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });
const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;
it('opens a separate coach editor and preserves entered text after save fails', async () => {
  const fetchMock = vi.fn((url: string, options?: RequestInit) => Promise.resolve(
    url === '/api/auth/me' ? json({ id: 'coach', role: 'COACH', name: 'GEER', level: null }) :
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
    url === '/api/coach/profile/draft' ? options?.method === 'PUT'
      ? json({ detail: '草稿版本已更新，请重新载入' }, 409)
      : json({ version: 0, publishedVersion: 0, content: { displayName: 'GEER', tagline: '' }, media: {} }) :
    json({ items: [], nextCursor: null })));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '个人主页' }));
  await userEvent.type(await screen.findByLabelText('一句话介绍'), '我的教学与热爱');
  await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));
  expect((await screen.findByRole('alert')).textContent).toContain('草稿版本已更新');
  expect((screen.getByLabelText('一句话介绍') as HTMLInputElement).value).toBe('我的教学与热爱');
});
