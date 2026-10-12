import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });

const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;
const mountain = { id: 'mountain-1', name: 'Blue Mountain', active: true };
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Toronto',
  year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const tomorrow = new Date(`${today}T00:00:00Z`);
tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
const target = new Date(Date.UTC(tomorrow.getUTCFullYear(), tomorrow.getUTCMonth() + 1, 1));
const month = target.toISOString().slice(0, 7);
const date = (day: number) => `${month}-${String(day).padStart(2, '0')}`;
const bookedDescription = '绿色：有已确认课程';
const openDescription = '黄色：有可用时段';
function slot(day: number, status: string, index = 0) {
  return { id: `${day}-${status}-${index}`, localDate: date(day), zoneId: 'America/Toronto', status,
    startAt: `${date(day)}T${index ? '18' : '15'}:00:00Z`,
    endAt: `${date(day)}T${index ? '20' : '17'}:00:00Z`, availableMountains: [mountain] };
}
const baseDays = [
  { day: 13, statuses: ['BOOKED'] }, { day: 14, statuses: ['OPEN'] },
  { day: 15, statuses: ['OPEN', 'BOOKED'] }, { day: 16, statuses: ['OPEN'] },
  { day: 17, statuses: ['CLOSED'] },
];
function setup() {
  let saved = false;
  let refreshFails = false;
  const writes: unknown[] = [];
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/auth/csrf') return Promise.resolve(json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }));
    if (url === '/api/auth/me') return Promise.resolve(json({ id: 'coach-1', role: 'COACH', name: 'GEER', level: null }));
    if (url.startsWith('/api/coach/mountains')) return Promise.resolve(json({ items: [mountain], nextCursor: null }));
    if (url.startsWith('/api/coach/bookings')) return Promise.resolve(json({ items: [{ ...slot(16, 'OPEN'),
      id: 'pending-1', slotId: '16-OPEN-0', studentName: '学员', status: 'PENDING', decisionReason: null,
      courseTitle: '单板课', priceAmount: '150.00', currency: 'CAD', location: mountain.name }], nextCursor: 'more-bookings' }));
    if (url === '/api/coach/availability/replacements' && options?.method === 'POST') {
      writes.push(JSON.parse(String(options.body)));
      saved = true;
      return Promise.resolve(json({ slots: [slot(20, 'OPEN')], tails: [] }, 201));
    }
    if (url.startsWith('/api/coach/availability/month')) {
      if (saved && refreshFails) return Promise.resolve(json({ detail: '月历读取失败' }, 503));
      const params = new URL(url, 'http://localhost').searchParams;
      const requestedMonth = `${params.get('year')}-${params.get('month')?.padStart(2, '0')}`;
      const days = requestedMonth === month ? [...baseDays, ...(saved ? [{ day: 20, statuses: ['OPEN'] }] : [])]
        .map(({ day, statuses }) => ({ localDate: date(day), limitedMountain: null, lockedMountain: null,
          legacyReviewRequired: false, slots: statuses.map((status, index) => slot(day, status, index)) })) : [];
      return Promise.resolve(json({ zoneId: 'America/Toronto', days }));
    }
    return Promise.resolve(json({ items: [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, writes, failRefresh: (value: boolean) => { refreshFails = value; } };
}
async function openCalendar() {
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  await userEvent.click(screen.getByRole('button', { name: '下一个月' }));
  // The pre-existing booked text proves the month fixture loaded, independently of the new feedback.
  await waitFor(() => expect(screen.getAllByText('已预约')).toHaveLength(2));
}
function dayButton(day: number, selected = false, description?: string) {
  return screen.getByRole('button', { name: `${selected ? '取消选择日期' : '选择日期'} ${date(day)}`, description });
}

it('describes confirmed, available and mixed days from the month independently of booking pagination', async () => {
  const server = setup();
  await openCalendar();
  expect(dayButton(13, false, bookedDescription)).toBeTruthy();
  expect(dayButton(15, false, bookedDescription)).toBeTruthy();
  expect(dayButton(14, false, openDescription)).toBeTruthy();
  expect(dayButton(16, false, openDescription)).toBeTruthy();
  expect(dayButton(17).getAttribute('aria-describedby')).toBeNull();
  expect(dayButton(18).getAttribute('aria-describedby')).toBeNull();
  expect(screen.getByText(bookedDescription)).toBeTruthy();
  expect(screen.getByText(openDescription)).toBeTruthy();
  expect(server.fetchMock.mock.calls.some(([url]) => url.includes('cursor=more-bookings'))).toBe(false);
});

it('keeps status descriptions and selection when moving between months and deselecting', async () => {
  setup();
  await openCalendar();
  await userEvent.click(dayButton(13));
  await userEvent.click(dayButton(14));
  await userEvent.click(screen.getByRole('button', { name: '下一个月' }));
  await userEvent.click(screen.getByRole('button', { name: '上一个月' }));
  expect(dayButton(13, true, bookedDescription).getAttribute('aria-pressed')).toBe('true');
  expect(dayButton(14, true, openDescription).getAttribute('aria-pressed')).toBe('true');
  await userEvent.click(dayButton(13, true));
  await userEvent.click(dayButton(14, true));
  expect(dayButton(13, false, bookedDescription).getAttribute('aria-pressed')).toBe('false');
  expect(dayButton(14, false, openDescription).getAttribute('aria-pressed')).toBe('false');
});

it('describes a newly published day as available after the month refresh', async () => {
  const server = setup();
  await openCalendar();
  await userEvent.click(dayButton(20));
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  await screen.findByText(/已发布 1 个可用时间/);
  expect(dayButton(20, false, openDescription).getAttribute('aria-pressed')).toBe('false');
  expect(server.writes).toEqual([{ days: [{ localDate: date(20), startTime: '10:00', endTime: '12:00', mountainId: null }] }]);
});

it('retains known status on a failed refresh and only describes the new day after retry succeeds', async () => {
  const server = setup();
  await openCalendar();
  server.failRefresh(true);
  await userEvent.click(dayButton(20));
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  await screen.findByText(/月历读取失败/);
  expect(screen.getByText(/已发布 1 个可用时间/)).toBeTruthy();
  expect(dayButton(13, false, bookedDescription)).toBeTruthy();
  expect(dayButton(14, false, openDescription)).toBeTruthy();
  expect(dayButton(20).getAttribute('aria-describedby')).toBeNull();
  expect(within(dayButton(20)).getByText('刚发布')).toBeTruthy();
  server.failRefresh(false);
  await userEvent.click(screen.getByRole('button', { name: '重试' }));
  await waitFor(() => expect(dayButton(20, false, openDescription)).toBeTruthy());
  expect(screen.queryByText(/月历读取失败/)).toBeNull();
  expect(server.writes).toHaveLength(1);
});
