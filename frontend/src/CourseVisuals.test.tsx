import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });
const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;
const cover = { id: 'asset-1', url: 'https://cdn.test/cover.png', width: 1600, height: 900, expiresAt: '2099-01-01T00:00:00Z' };
const course = { id: 'course-1', title: '滑行进阶', description: '练习流畅转弯', priceAmount: '150.00', currency: 'CAD', active: true,
  coverAssetId: cover.id, cover, coverPositionX: 18.25, coverPositionY: 75 };
function setup(role: 'COACH' | 'STUDENT', failSave = false, image = cover) {
  const savedCourse = { ...course, cover: image };
  const writes: Record<string, unknown>[] = [];
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'PATCH') {
      const body = JSON.parse(String(options.body)); writes.push(body);
      return Promise.resolve(failSave ? json({ detail: '保存失败，请重试' }, 503) : json({ ...savedCourse, ...body }));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/student/contact' ? json({ phone: '+14165550123' }) :
      url === '/api/auth/me' ? json({ id: 'account-1', role, name: 'Geer', level: null }) :
        url === '/api/coach/media/uploads/asset-1' ? json({ id: 'asset-1', status: 'READY', preview: { ...cover, url: 'https://cdn.test/refreshed.png' } }) :
        /\/api\/(coach\/)?courses/.test(url) ? json({ items: [savedCourse], nextCursor: null }) : json({ items: [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock); render(<App />);
  return { writes, fetchMock };
}
it('shows a saved cover position and exposes selection without selecting on hover', async () => {
  const { fetchMock } = setup('STUDENT');
  const card = await screen.findByRole('button', { name: /滑行进阶.*CAD 150/ });
  expect(card.getAttribute('aria-pressed')).toBe('true');
  const image = card.querySelector('img') as HTMLImageElement;
  expect(image).toBeTruthy(); expect(image.style.top).toBe('75%'); expect(image.style.transform).toBe('translateY(-75%)');
  fireEvent.mouseEnter(card); fireEvent.mouseLeave(card);
  expect(fetchMock.mock.calls.filter(([, options]) => options?.method)).toHaveLength(0);
  expect(card.getAttribute('aria-pressed')).toBe('true');
});
it('refreshes a failed cover once and keeps the course selectable after another failure', async () => {
  const { fetchMock } = setup('STUDENT');
  const card = await screen.findByRole('button', { name: /滑行进阶.*CAD 150/ });
  fireEvent.error(card.querySelector('img')!);
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.startsWith('/api/courses')).length).toBe(2));
  fireEvent.error(card.querySelector('img')!);
  await waitFor(() => expect(card.querySelector('img')).toBeNull());
  await userEvent.click(card); expect(card.getAttribute('aria-pressed')).toBe('true');
});
it('saves keyboard composition without uploading the original again', async () => {
  const { writes, fetchMock } = setup('COACH');
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.click(screen.getByRole('button', { name: '编辑' }));
  expect(screen.queryByLabelText('水平位置')).toBeNull();
  fireEvent.change(screen.getByLabelText('垂直位置'), { target: { value: '90' } });
  await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
  await waitFor(() => expect(writes).toHaveLength(1));
  expect(writes[0]).toMatchObject({ coverAssetId: cover.id, coverPositionX: 18.25, coverPositionY: 90 });
  expect(fetchMock.mock.calls.some(([url]) => url.includes('/media/uploads'))).toBe(false);
});

async function compositionPreview(image = cover) {
  const result = setup('COACH', false, image);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.click(screen.getByRole('button', { name: '编辑' }));
  const preview = screen.getByLabelText('桌面构图预览');
  vi.spyOn(preview, 'getBoundingClientRect').mockReturnValue({ x: 0, y: 0, top: 0, left: 0, right: 200, bottom: 200, width: 200, height: 200, toJSON: () => ({}) });
  Object.defineProperty(preview, 'setPointerCapture', { configurable: true, value: vi.fn() });
  // jsdom has no PointerEvent; supply pointer coordinates rather than changing production handlers.
  class TestPointerEvent extends MouseEvent {
    readonly pointerId: number; readonly pointerType: string;
    constructor(type: string, options: MouseEventInit & { pointerId?: number; pointerType?: string } = {}) {
      super(type, options); this.pointerId = options.pointerId ?? 1; this.pointerType = options.pointerType ?? 'mouse';
    }
  }
  vi.stubGlobal('PointerEvent', TestPointerEvent);
  return { ...result, preview };
}

it('moves a short image vertically using its width-scaled height and preserves saved horizontal data', async () => {
  const { preview, writes } = await compositionPreview();
  // At 200px wide, 1600x900 scales to 112.5px high, leaving 87.5px vertical space.
  fireEvent.pointerDown(preview, { pointerId: 1, clientX: 100, clientY: 100, button: 0 });
  fireEvent.pointerMove(preview, { pointerId: 1, clientX: 100, clientY: 117.5 });
  fireEvent.pointerUp(preview, { pointerId: 1 });
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('95');
  await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
  await waitFor(() => expect(writes).toHaveLength(1));
  expect(writes[0]).toMatchObject({ coverPositionX: 18.25, coverPositionY: 95, coverAssetId: cover.id });
});

it('ignores horizontal dragging instead of cropping across the image', async () => {
  const { preview, writes } = await compositionPreview();
  fireEvent.pointerDown(preview, { pointerId: 1, clientX: 100, clientY: 100, button: 0 });
  fireEvent.pointerMove(preview, { pointerId: 1, clientX: 40, clientY: 100 });
  fireEvent.pointerUp(preview, { pointerId: 1 });
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('75');
  await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
  await waitFor(() => expect(writes).toHaveLength(1));
  expect(writes[0]).toMatchObject({ coverPositionX: 18.25, coverPositionY: 75 });
});

it('clamps portrait composition to its vertical bounds and supports touch dragging', async () => {
  const { preview } = await compositionPreview({ ...cover, width: 800, height: 1600 });
  fireEvent.pointerDown(preview, { pointerId: 2, pointerType: 'touch', clientX: 100, clientY: 100 });
  fireEvent.pointerMove(preview, { pointerId: 2, pointerType: 'touch', clientX: 140, clientY: 140 });
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('55');
  fireEvent.pointerMove(preview, { pointerId: 2, pointerType: 'touch', clientX: 140, clientY: 1000 });
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('0');
  fireEvent.pointerMove(preview, { pointerId: 2, pointerType: 'touch', clientX: 140, clientY: -1000 });
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('100');
  fireEvent.pointerUp(preview, { pointerId: 2, pointerType: 'touch' });
});

it('keeps the composition unchanged when scaled image height equals the frame', async () => {
  const { preview } = await compositionPreview({ ...cover, width: 800, height: 800 });
  fireEvent.pointerDown(preview, { pointerId: 1, clientX: 100, clientY: 100, button: 0 });
  fireEvent.pointerMove(preview, { pointerId: 1, clientX: 20, clientY: 180 });
  fireEvent.pointerUp(preview, { pointerId: 1 });
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('75');
});
it('resets composition to center and cancels without changing the saved course', async () => {
  const { writes } = setup('COACH');
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.click(screen.getByRole('button', { name: '编辑' }));
  await userEvent.click(screen.getByRole('button', { name: '重置居中' }));
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('50');
  await userEvent.click(screen.getByRole('button', { name: '取消编辑' }));
  await userEvent.click(screen.getByRole('button', { name: '编辑' }));
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('75'); expect(writes).toHaveLength(0);
});
it('keeps edited text and composition after a failed save', async () => {
  setup('COACH', true);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.click(screen.getByRole('button', { name: '编辑' }));
  fireEvent.change(screen.getByLabelText('垂直位置'), { target: { value: '35' } });
  await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
  expect((await screen.findAllByText('保存失败，请重试')).length).toBeGreaterThan(0);
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('35');
});
it('rejects oversize uploads locally and preserves course text', async () => {
  const { fetchMock } = setup('COACH');
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  const input = screen.getByLabelText('上传课程封面') as HTMLInputElement;
  const file = new File(['x'], 'large.png', { type: 'image/png' }); Object.defineProperty(file, 'size', { value: 8 * 1024 * 1024 + 1 });
  fireEvent.change(input, { target: { files: [file] } });
  expect(await screen.findByText(/图片不能超过 8 MiB/)).toBeTruthy();
  expect(fetchMock.mock.calls.some(([url]) => url.includes('/media/uploads'))).toBe(false);
});

it('uploads a cover through PUT and complete, blocks publishing until READY, and preserves it on save failure', async () => {
  const writes: Array<{ url: string; body: Record<string, unknown> }> = [];
  let finishPut: (() => void) | undefined;
  class FakeXhr {
    status = 200; timeout = 0; upload = {}; onload?: () => void; onabort?: () => void;
    open = vi.fn(); setRequestHeader = vi.fn();
    send() { finishPut = () => this.onload?.(); }
    abort() { this.onabort?.(); }
  }
  vi.stubGlobal('XMLHttpRequest', FakeXhr);
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'POST') {
      const body = JSON.parse(String(options.body)); writes.push({ url, body });
      if (url === '/api/coach/media/uploads') return Promise.resolve(json({ id: 'upload-1', status: 'UPLOADING', uploadUrl: 'https://storage.test/put', headers: { 'Content-Type': 'image/png' } }, 201));
      if (url.endsWith('/complete')) return Promise.resolve(json({ id: 'upload-1', status: 'READY', preview: { ...cover, id: 'upload-1' } }, 202));
      return Promise.resolve(json({ detail: '课程保存失败' }, 503));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/student/contact' ? json({ phone: '+14165550123' }) :
      url === '/api/auth/me' ? json({ id: 'coach', role: 'COACH', name: 'GEER', level: null }) : json({ items: [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock); render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.type(screen.getByLabelText('课程名称'), '测试封面课');
  await userEvent.type(screen.getByLabelText('课程价格（CAD）'), '100');
  await userEvent.upload(screen.getByLabelText('上传课程封面'), new File(['png'], 'cover.png', { type: 'image/png' }));
  await waitFor(() => expect(finishPut).toBeTruthy());
  expect((screen.getByRole('button', { name: '发布课程' }) as HTMLButtonElement).disabled).toBe(true);
  finishPut!(); await screen.findByText('封面已验证，保存课程后生效。');
  fireEvent.change(screen.getByLabelText('垂直位置'), { target: { value: '67.5' } });
  await userEvent.click(screen.getByRole('button', { name: '发布课程' }));
  await screen.findByText('课程保存失败');
  expect(writes.find(({ url }) => url === '/api/coach/courses')?.body).toMatchObject({ title: '测试封面课', coverAssetId: 'upload-1', coverPositionY: 67.5 });
  expect((screen.getByLabelText('课程名称') as HTMLInputElement).value).toBe('测试封面课');
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('67.5');
});

it('recovers a failed editor preview without changing composition or uploading again', async () => {
  const { fetchMock } = setup('COACH');
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.click(screen.getByRole('button', { name: '编辑' }));
  fireEvent.error(screen.getAllByAltText('课程封面预览')[0]);
  await userEvent.click(await screen.findByRole('button', { name: '刷新封面预览' }));
  await waitFor(() => expect(screen.getAllByAltText('课程封面预览')[0].getAttribute('src')).toBe('https://cdn.test/refreshed.png'));
  expect(fetchMock.mock.calls.some(([url]) => url === '/api/coach/media/uploads/asset-1')).toBe(true);
  expect((screen.getByLabelText('垂直位置') as HTMLInputElement).value).toBe('75');
  expect(fetchMock.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false);
});
