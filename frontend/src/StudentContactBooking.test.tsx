import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;
afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });
const sample = { id: 'booking-1', slotId: 'slot-1', studentName: '学员', status: 'PENDING', courseTitle: '基础课',
  priceAmount: '150.00', currency: 'CAD', localDate: '2026-10-10', zoneId: 'America/Toronto',
  startAt: '2026-10-10T14:00:00Z', endAt: '2026-10-10T16:00:00Z', location: 'Blue Mountain', decisionReason: null };
function studentApi(initial: string | null = null, failures: { read?: boolean; save?: boolean; booking?: 'reject' | 'network' } = {}) {
  let phone = initial;
  const writes: Array<{ url: string; body: { phone?: string }; key?: string }> = [];
  const keys: string[] = [];
  const fetchMock = vi.fn(async (url: string, options?: RequestInit) => {
    if (url === '/api/student/contact') {
      if (options?.method === 'PATCH') {
        const body = JSON.parse(String(options.body)); writes.push({ url, body });
        if (failures.save) return json({ detail: '电话保存失败' }, 503);
        phone = body.phone.replace(/[ ()-]/g, ''); return json({ phone });
      }
      return failures.read ? json({ detail: '联系电话加载失败' }, 503) : json({ phone });
    }
    if (url === '/api/bookings' && options?.method === 'POST') {
      const key = (options.headers as Record<string, string>)['Idempotency-Key']; keys.push(key);
      writes.push({ url, body: JSON.parse(String(options.body)), key });
      if (failures.booking === 'network' && keys.length === 1) throw new TypeError('Failed to fetch');
      if (failures.booking === 'reject') return json({ detail: '时段已不可申请' }, 409);
      return json(sample, keys.length > 1 ? 200 : 201);
    }
    return url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', name: '学员', role: 'STUDENT', level: '入门' }) :
      url.startsWith('/api/courses') ? json({ items: [{ id: 'course-1', title: '基础课', description: '', priceAmount: '150.00', currency: 'CAD' }], nextCursor: null }) :
      url.startsWith('/api/slots') ? json({ items: [{ ...sample, id: 'slot-1', status: 'OPEN', availableMountains: [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] }], nextCursor: null }) :
      json({ items: [], nextCursor: null });
  });
  vi.stubGlobal('fetch', fetchMock); return { writes, keys, fetchMock, failures };
}
function phoneDigits(input: HTMLElement) { return (input as HTMLInputElement).value.replace(/\D/g, ''); }
async function selectSlot() {
  await userEvent.click(await screen.findByRole('button', { name: /10:00.*12:00/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Blue Mountain' }));
}

it('explains why phone is required and blocks an empty or invalid new booking', async () => {
  const api = studentApi(); render(<App />); await selectSlot();
  const input = await screen.findByLabelText('联系电话（必填）');
  expect(screen.getByText('用于教练联系你、沟通并确认预约。')).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/请填写有效的联系电话/)).toBeTruthy();
  await userEvent.type(input, '123');
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(api.writes).toHaveLength(0);
});

it('saves a valid phone before applying and reuses the booking key after an unknown network result', async () => {
  const api = studentApi(null, { booking: 'network' }); render(<App />); await selectSlot();
  await userEvent.type(await screen.findByLabelText('联系电话（必填）'), '+1 (416) 555-0123');
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/预约提交结果暂时无法确认/)).toBeTruthy();
  expect(api.writes.map((write) => write.url)).toEqual(['/api/student/contact', '/api/bookings']);
  expect(phoneDigits(screen.getByLabelText('联系电话（必填）'))).toBe('4165550123');
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/申请已提交，待教练确认/)).toBeTruthy();
  expect(api.keys).toHaveLength(2); expect(api.keys[0]).toBe(api.keys[1]);
  expect(api.writes.filter((write) => write.url === '/api/student/contact')).toHaveLength(1);
});

it('prefills the saved phone and allows changes without creating a booking', async () => {
  const api = studentApi('+14165550123'); render(<App />);
  const input = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect(phoneDigits(input)).toBe('4165550123'));
  await userEvent.clear(input); await userEvent.type(input, '+86 138 0013 8000');
  await userEvent.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes).toHaveLength(1); expect(api.writes[0].url).toBe('/api/student/contact');
  expect(api.writes[0].body.phone).toBe('+8613800138000');
  await selectSlot(); await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/申请已提交，待教练确认/)).toBeTruthy();
  expect(api.writes).toHaveLength(2);
});

it('does not apply when contact save fails and preserves the draft for retry', async () => {
  const api = studentApi(null, { save: true }); render(<App />); await selectSlot();
  await userEvent.type(await screen.findByLabelText('联系电话（必填）'), '+14165550123');
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/电话保存失败/)).toBeTruthy(); expect(api.keys).toHaveLength(0);
  api.failures.save = false;
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/申请已提交，待教练确认/)).toBeTruthy();
});

it('keeps saved phone when a booking is rejected and explains the separate outcomes', async () => {
  const api = studentApi(null, { booking: 'reject' }); render(<App />); await selectSlot();
  await userEvent.type(await screen.findByLabelText('联系电话（必填）'), '+14165550123');
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/联系电话已保存.*时段已不可申请/)).toBeTruthy();
  expect(api.writes.map((write) => write.url)).toEqual(['/api/student/contact', '/api/bookings']);
  expect(phoneDigits(screen.getByLabelText('联系电话（必填）'))).toBe('4165550123');
});

it('allows browsing but requires a successful contact read before save or apply', async () => {
  const api = studentApi('+14165550123', { read: true }); render(<App />); await selectSlot();
  expect(await screen.findByText(/联系电话加载失败/)).toBeTruthy();
  expect((screen.getByRole('button', { name: '申请预约' }) as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByRole('button', { name: '保存电话' }) as HTMLButtonElement).disabled).toBe(true);
  api.failures.read = false;
  await userEvent.click(screen.getByRole('button', { name: '重试加载电话' }));
  await waitFor(() => expect(phoneDigits(screen.getByLabelText('联系电话（必填）'))).toBe('4165550123'));
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/申请已提交，待教练确认/)).toBeTruthy();
  expect(api.writes.map((write) => write.url)).toEqual(['/api/bookings']);
});

it.each(['+123456', '+1234567890123456', '+01234567', '+1/4165550123'])('rejects malformed phone %s without any writes', async (phone) => {
  const api = studentApi(); render(<App />);
  fireEvent.change(await screen.findByLabelText('联系电话（必填）'), { target: { value: phone } });
  await userEvent.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/请填写有效的联系电话/)).toBeTruthy(); expect(api.writes).toHaveLength(0);
});

it('shows phone only on coach cards, keeps it after confirm, and labels legacy missing contacts', async () => {
  let confirmed = false;
  vi.stubGlobal('fetch', vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/confirm') && options?.method === 'POST') { confirmed = true; return json({ ...sample, status: 'CONFIRMED' }); }
    if (url === '/api/auth/me') return json({ id: 'coach-1', name: '教练', role: 'COACH', level: null });
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' });
    if (url.startsWith('/api/coach/bookings')) return confirmed ? json({ detail: '刷新失败' }, 503) : json({ items: [
      { ...sample, studentPhone: '+14165550123' }, { ...sample, id: 'booking-2', studentName: '旧学员', status: 'REJECTED', studentPhone: null },
    ], nextCursor: null });
    return json({ items: [], nextCursor: null, days: [] });
  }));
  render(<App />);
  expect((await screen.findByRole('link', { name: '+14165550123' })).getAttribute('href')).toBe('tel:+14165550123');
  expect(screen.getByText('未提供电话')).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '确认' }));
  expect(await screen.findByText('已确认')).toBeTruthy();
  expect(screen.getByRole('link', { name: '+14165550123' })).toBeTruthy();
});

it('clears a saved phone and unsaved draft when logging out and switching accounts', async () => {
  let current = 'first', loggedIn = true;
  vi.stubGlobal('fetch', vi.fn(async (url: string, options?: RequestInit) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' });
    if (url === '/api/auth/logout') { loggedIn = false; return json({}); }
    if (url === '/api/auth/login' && options?.method === 'POST') { loggedIn = true; current = 'second'; }
    if (url === '/api/auth/me' || url === '/api/auth/login') return loggedIn
      ? json({ id: current, role: 'STUDENT', name: current, level: '入门' }) : json({}, 401);
    if (url === '/api/student/contact') return json({ phone: current === 'first' ? '+14165550123' : '+14165550124' });
    return json({ items: [], nextCursor: null });
  }));
  render(<App />);
  const firstInput = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect(phoneDigits(firstInput)).toBe('4165550123'));
  fireEvent.change(firstInput, { target: { value: '+8613800138000' } });
  await userEvent.click(screen.getByRole('button', { name: '退出' }));
  await userEvent.type(await screen.findByLabelText('邮箱'), 'second@example.test');
  await userEvent.type(screen.getByLabelText('密码'), 'private-test-password');
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  const secondInput = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect(phoneDigits(secondInput)).toBe('4165550124'));
  expect(document.body.textContent).not.toContain('+8613800138000');
  expect(document.body.textContent).not.toContain('+14165550123');
});

it('does not overwrite an unsaved phone when the booking lists refresh', async () => {
  const api = studentApi('+14165550123'); render(<App />);
  const input = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect(phoneDigits(input)).toBe('4165550123'));
  fireEvent.change(input, { target: { value: '+8613800138000' } });
  await userEvent.click(screen.getByRole('button', { name: '我的预约' }));
  await userEvent.click(screen.getByRole('button', { name: '刷新状态' }));
  await userEvent.click(screen.getByRole('button', { name: '约课' }));
  expect(phoneDigits(await screen.findByLabelText('联系电话（必填）'))).toBe('13800138000');
  expect(api.fetchMock.mock.calls.filter(([url]) => url === '/api/student/contact')).toHaveLength(1);
});


it('country selector defaults to Canada and saves a local number before applying', async () => {
  const api = studentApi(); render(<App />); await selectSlot();
  const country = await screen.findByRole('combobox', { name: '国家或地区' });
  expect((country as HTMLSelectElement).value).toBe('CA');
  expect(screen.getByRole('option', { name: '加拿大 +1' })).toBeTruthy();
  expect(screen.queryByText(/请包含国家区号/)).toBeNull();
  await userEvent.type(screen.getByLabelText('联系电话（必填）'), '4165550123');
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/申请已提交，待教练确认/)).toBeTruthy();
  expect(api.writes.map(write => write.url)).toEqual(['/api/student/contact', '/api/bookings']);
  expect(api.writes[0].body.phone).toBe('+14165550123');
});

it('country selector switches prefixes without losing the entered number', async () => {
  const api = studentApi(); render(<App />);
  const country = await screen.findByRole('combobox', { name: '国家或地区' });
  const input = screen.getByLabelText('联系电话（必填）');
  await waitFor(() => expect((input as HTMLInputElement).disabled).toBe(false));
  await userEvent.type(input, '13800138000');
  await userEvent.selectOptions(country, 'CN');
  expect(phoneDigits(screen.getByLabelText('联系电话（必填）'))).toBe('13800138000');
  await userEvent.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes[0].body.phone).toBe('+8613800138000');
});

it('country selector restores an existing Chinese number without rewriting it', async () => {
  const api = studentApi('+8613800138000'); render(<App />);
  const country = await screen.findByRole('combobox', { name: '国家或地区' });
  await waitFor(() => expect((country as HTMLSelectElement).value).toBe('CN'));
  await waitFor(() => expect(phoneDigits(screen.getByLabelText('联系电话（必填）'))).toBe('13800138000'));
  await userEvent.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes).toHaveLength(0);
});

it('country selector detects a pasted full international number without a duplicate prefix', async () => {
  const api = studentApi(); render(<App />);
  const user = userEvent.setup();
  const country = await screen.findByRole('combobox', { name: '国家或地区' });
  const input = screen.getByLabelText('联系电话（必填）');
  await waitFor(() => expect((input as HTMLInputElement).disabled).toBe(false));
  await user.click(input);
  await user.paste('+86 138 0013 8000');
  expect(document.activeElement).toBe(input);
  expect((country as HTMLSelectElement).value).toBe('CN');
  expect(phoneDigits(screen.getByLabelText('联系电话（必填）'))).toBe('13800138000');
  await user.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes[0].body.phone).toBe('+8613800138000');
});

it('country selector preserves an unrecognised legacy international value', async () => {
  const api = studentApi('+9991234567'); render(<App />);
  const input = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect((input as HTMLInputElement).value).toBe('+9991234567'));
  expect((screen.getByRole('combobox', { name: '国家或地区' }) as HTMLSelectElement).value).toBe('');
  await userEvent.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes).toHaveLength(0);
});

it('country selector keeps a pasted unrecognised calling code in international format', async () => {
  const api = studentApi(); render(<App />);
  const user = userEvent.setup();
  const input = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect((input as HTMLInputElement).disabled).toBe(false));
  await user.click(input); await user.paste('+9991234567');
  expect((screen.getByRole('combobox', { name: '国家或地区' }) as HTMLSelectElement).value).toBe('');
  expect((screen.getByLabelText('联系电话（必填）') as HTMLInputElement).value).toBe('+9991234567');
  await user.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes[0].body.phone).toBe('+9991234567');
});


it.each(['+1 (416) 555-0123', ' +1 (416) 555-0123 '])('country selector handles a pasted Canadian number %s and keeps editing focus', async phone => {
  const api = studentApi(); render(<App />);
  const user = userEvent.setup();
  const input = await screen.findByLabelText('联系电话（必填）');
  await user.click(input); await user.paste(phone);
  expect(document.activeElement).toBe(input);
  expect(phoneDigits(screen.getByLabelText('联系电话（必填）'))).toBe('4165550123');
  expect((screen.getByRole('combobox', { name: '国家或地区' }) as HTMLSelectElement).value).toBe('CA');
  await user.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes[0].body.phone).toBe('+14165550123');
});

it('country selector retains the existing basic length rule instead of requiring metadata-valid numbers', async () => {
  const api = studentApi('+1234567'); render(<App />);
  const input = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect(phoneDigits(input)).toBe('234567'));
  await userEvent.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/联系电话已保存/)).toBeTruthy();
  expect(api.writes).toHaveLength(0);
});

it.each(['416/5550123', '4165550123456789'])('country selector rejects invalid local input %s without saving filtered digits', async phone => {
  const api = studentApi(); render(<App />);
  const input = await screen.findByLabelText('联系电话（必填）');
  await waitFor(() => expect((input as HTMLInputElement).disabled).toBe(false));
  fireEvent.change(input, { target: { value: phone } });
  await userEvent.click(screen.getByRole('button', { name: '保存电话' }));
  expect(await screen.findByText(/请填写有效的联系电话/)).toBeTruthy();
  expect(api.writes).toHaveLength(0);
});
