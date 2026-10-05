import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';
import CoachProfileEditor from './CoachProfileEditor';

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

it.each(['FAILED', 'REJECTED'])('explains a %s video probe timeout without blaming the file format', async (status) => {
  await uploadResult(status, 'PROBE_TIMEOUT');
  expect((await screen.findByRole('alert')).textContent).toContain('视频校验暂时超时，请稍后重试。');
});

it('keeps the format rejection message for genuinely invalid video content', async () => {
  await uploadResult('REJECTED', 'INVALID_CONTENT');
  expect((await screen.findByRole('alert')).textContent).toContain('文件格式或内容不符合要求，请重新选择文件。');
});

async function uploadResult(status: string, errorCode: string) {
  const ticket = { id: 'video', purpose: 'HIGHLIGHT_VIDEO', status: 'VERIFYING', size: 10, uploadUrl: null,
    headers: {}, expiresAt: '2099-01-01T00:00:00Z', preview: null, errorCode: null };
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/coach/profile/draft' ? json({ version: 0, publishedVersion: 0, content: { displayName: 'GEER' }, media: {} }) :
    url === '/api/coach/media/uploads' ? json(ticket, 201) :
    url === '/api/coach/media/uploads/video/complete' ? json({ ...ticket, status, errorCode }, 202) : json({}, 404))));
  render(<CoachProfileEditor refreshCsrf={async () => ({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })} onBack={vi.fn()} onUnauthorized={vi.fn()} />);
  await userEvent.upload(await screen.findByLabelText('上传高光滑行视频'), new File(['test-video'], 'video.mp4', { type: 'video/mp4' }));
}
