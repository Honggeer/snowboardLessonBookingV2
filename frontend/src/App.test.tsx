import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });

const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;

it('shows the cancellation cutoff beside the application button before submission', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: '学员', level: '零基础' }) :
        json({ items: [], nextCursor: null }),
  )));
  render(<App />);
  const button = await screen.findByRole('button', { name: '申请预约' });
  const summary = button.closest('.booking-summary') as HTMLElement;
  expect(within(summary).getByText(/待确认申请可取消.*教练确认后.*至少 24 小时前取消/)).toBeTruthy();
});

it('opens a mailed booking link after login and fetches the exact student booking', async () => {
  window.history.replaceState(null, '', '/#/my-bookings/booking-target');
  let loggedIn = false;
  const target = { id: 'booking-target', slotId: 'slot-1', studentName: '学员', courseTitle: '邮件链接课程',
    priceAmount: '150.00', currency: 'CAD', location: 'Blue Mountain', zoneId: 'America/Toronto',
    localDate: '2026-10-20', startAt: '2026-10-20T14:00:00Z', endAt: '2026-10-20T16:00:00Z',
    status: 'CONFIRMED', decisionReason: null };
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/auth/login' && options?.method === 'POST') {
      loggedIn = true;
      return Promise.resolve(json({ id: 'student-1', role: 'STUDENT', name: '学员', level: '零基础' }));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? loggedIn
        ? json({ id: 'student-1', role: 'STUDENT', name: '学员', level: '零基础' }) : json({}, 401) :
        url === '/api/bookings/booking-target' ? json(target) :
          json({ items: [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.type(await screen.findByLabelText('邮箱'), 'student@example.test');
  await userEvent.type(screen.getByLabelText('密码'), 'test-password');
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/bookings/booking-target')).toBe(true));
  expect(await screen.findByText('邮件链接课程')).toBeTruthy();
  expect(screen.getByRole('button', { name: '我的预约' }).className).toContain('active');
});

it('submits mountain and availability when randomUUID is unavailable on a local network origin', async () => {
  const webCrypto = globalThis.crypto;
  vi.stubGlobal('crypto', { getRandomValues: webCrypto.getRandomValues.bind(webCrypto) });
  const writes: Array<{ url: string; key: string }> = [];
  let savedMountain = false;
  const mountain = { id: 'mountain-1', name: 'Blue Mountain', active: true };
  vi.stubGlobal('fetch', vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'POST') {
      writes.push({ url, key: (options.headers as Record<string, string>)['Idempotency-Key'] });
      if (url === '/api/coach/mountains') { savedMountain = true; return Promise.resolve(json(mountain, 201)); }
      if (url === '/api/coach/availability/replacements') return Promise.resolve(json({ slots: [{ id: 'slot-1' }], tails: [] }, 201));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? json({ zoneId: 'America/Toronto', days: [] }) :
          json({ items: url.startsWith('/api/coach/mountains') && savedMountain ? [mountain] : [], nextCursor: null }));
  }));
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  await userEvent.type(screen.getByLabelText('雪场名称'), mountain.name);
  await userEvent.click(screen.getByRole('button', { name: '新增雪场' }));
  await waitFor(() => expect(writes.some(({ url }) => url === '/api/coach/mountains')).toBe(true));
  await userEvent.click((await screen.findAllByRole('button', { name: /选择日期/ }))[0]);
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  await waitFor(() => expect(writes.some(({ url }) => url === '/api/coach/availability/replacements')).toBe(true));
  expect(writes.every(({ key }) => /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(key))).toBe(true);
});

it('shows mountain validation and API failures beside the add button', async () => {
  const fetchMock = vi.fn((url: string, options?: RequestInit) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? json({ zoneId: 'America/Toronto', days: [] }) :
          url === '/api/coach/mountains' && options?.method === 'POST'
            ? json({ detail: '雪场接口尚未更新' }, 404) : json({ items: [], nextCursor: null }),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  const add = screen.getByRole('button', { name: '新增雪场' });
  const form = add.closest('form') as HTMLElement;
  await userEvent.click(add);
  expect(within(form).getByRole('alert').textContent).toContain('请输入雪场名称');
  expect(fetchMock.mock.calls.some(([url, options]) => url === '/api/coach/mountains' && options?.method === 'POST')).toBe(false);
  await userEvent.type(screen.getByLabelText('雪场名称'), 'Blue Mountain');
  await userEvent.click(add);
  expect((await within(form).findByRole('alert')).textContent).toContain('雪场接口尚未更新');
  expect((screen.getByLabelText('雪场名称') as HTMLInputElement).value).toBe('Blue Mountain');
});

it('explains missing availability prerequisites beside its publish button', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? json({ zoneId: 'America/Toronto', days: [] }) :
          json({ items: [], nextCursor: null }),
  )));
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  const publish = screen.getByRole('button', { name: '整天覆盖并发布' });
  const form = publish.closest('form') as HTMLElement;
  await userEvent.click(publish);
  expect(within(form).getByRole('alert').textContent).toContain('请先新增一座雪场');
});

it('explains why a calendar date cannot be added and confirms a valid selection beside the calendar', async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? json({ zoneId: 'America/Toronto', days: [] }) :
          json({ items: url.startsWith('/api/coach/mountains')
            ? [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] : [], nextCursor: null }),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '00:00' } });
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Toronto',
    year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = (type: string) => parts.find((item) => item.type === type)?.value;
  const today = `${part('year')}-${part('month')}-${part('day')}`;
  if (!screen.queryByRole('button', { name: `查看日期 ${today}` })) {
    await userEvent.click(screen.getByRole('button', { name: '上一个月' }));
  }
  await userEvent.click(screen.getByRole('button', { name: `查看日期 ${today}` }));
  expect(screen.getByRole('alert').textContent).toMatch(/今天的开始时间.*已过/);
  expect(screen.getByText(/已选 0 天/)).toBeTruthy();

  if (screen.queryAllByRole('button', { name: /选择日期/ }).length === 0) {
    await userEvent.click(screen.getByRole('button', { name: '下一个月' }));
  }
  const nextDate = screen.getAllByRole('button', { name: /选择日期/ })[0];
  const selectedDate = nextDate.getAttribute('aria-label')?.split(' ')[1];
  await userEvent.click(nextDate);
  expect(screen.getByRole('status').textContent).toContain(`${selectedDate} 已添加`);
  expect(nextDate.getAttribute('aria-pressed')).toBe('true');
});

it('lets the coach remove a selected date after its start time has passed', async () => {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Toronto',
    year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = (type: string) => parts.find((item) => item.type === type)?.value;
  const today = `${part('year')}-${part('month')}-${part('day')}`;
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(`${today}T12:00:00Z`));
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? json({ zoneId: 'America/Toronto', days: [] }) :
          json({ items: url.startsWith('/api/coach/mountains')
            ? [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] : [], nextCursor: null }),
  )));
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  if (!screen.queryByRole('button', { name: `选择日期 ${today}` })) {
    await userEvent.click(screen.getByRole('button', { name: '上一个月' }));
  }
  await userEvent.click(screen.getByRole('button', { name: `选择日期 ${today}` }));
  vi.setSystemTime(new Date(`${today}T22:00:00Z`));
  fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '09:00' } });
  await userEvent.click(screen.getByRole('button', { name: `取消选择日期 ${today}` }));
  expect(screen.getByText(/已选 0 天/)).toBeTruthy();
});

it('shows a saved calendar result beside the publishing form', async () => {
  let savedDate = '';
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/coach/availability/replacements' && options?.method === 'POST') {
      const body = JSON.parse(String(options.body)) as { days: Array<{ localDate: string }> };
      savedDate = body.days[0].localDate;
      return Promise.resolve(json({ slots: [{ id: 'new-slot', localDate: savedDate, status: 'OPEN',
        startAt: `${savedDate}T14:00:00Z`, endAt: `${savedDate}T16:00:00Z` }], tails: [] }, 201));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? json({ zoneId: 'America/Toronto', days: savedDate
          ? [{ localDate: savedDate, limitedMountain: null, lockedMountain: null, legacyReviewRequired: false,
            slots: [{ id: 'new-slot', localDate: savedDate, zoneId: 'America/Toronto', status: 'OPEN',
              startAt: `${savedDate}T14:00:00Z`, endAt: `${savedDate}T16:00:00Z` }] }] : [] }) :
          json({ items: url.startsWith('/api/coach/mountains')
            ? [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] : [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  const date = (await screen.findAllByRole('button', { name: /选择日期/ }))[0];
  await userEvent.click(date);
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  const publishForm = screen.getByRole('button', { name: '整天覆盖并发布' }).closest('form');
  expect(publishForm).not.toBeNull();
  expect(await within(publishForm as HTMLElement).findByText(/已发布 1 个可用时间/)).toBeTruthy();
  expect(await screen.findByText('1 时段')).toBeTruthy();
});

it('keeps calendar publish success visible when the month refresh fails', async () => {
  let saved = false;
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/coach/availability/replacements' && options?.method === 'POST') {
      saved = true;
      return Promise.resolve(json({ slots: [{ id: 'new-slot' }], tails: [] }, 201));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? (saved
          ? json({ detail: '月历读取失败' }, 503) : json({ zoneId: 'America/Toronto', days: [] })) :
          json({ items: url.startsWith('/api/coach/mountains')
            ? [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] : [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  await userEvent.click((await screen.findAllByRole('button', { name: /选择日期/ }))[0]);
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  expect(await screen.findByText(/已发布 1 个可用时间/)).toBeTruthy();
  expect(await screen.findByText('月历读取失败')).toBeTruthy();
  expect(await screen.findByText('刚发布')).toBeTruthy();
});

it('keeps a successful student application visible when list refresh fails', async () => {
  let mineReads = 0;
  const booking = { id: 'booking-1', slotId: 'slot-1', courseId: 'course-1', studentName: 'Geer',
    status: 'PENDING', decisionReason: null, courseTitle: '基础课', priceAmount: '150.00', currency: 'CAD',
    mountainId: 'mountain-1', location: 'Blue Mountain', zoneId: 'America/Toronto', localDate: '2026-10-10',
    startAt: '2026-10-10T14:00:00Z', endAt: '2026-10-10T16:00:00Z' };
  const fetchMock = vi.fn((url: string, options?: RequestInit) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: 'Geer', level: '入门' }) :
        url === '/api/bookings' && options?.method === 'POST' ? json(booking, 201) :
          url.startsWith('/api/bookings/mine') ? (++mineReads === 1 ? json({ items: [], nextCursor: null })
            : json({ detail: '预约列表暂时无法刷新' }, 503)) :
            url.startsWith('/api/courses') ? json({ items: [{ id: 'course-1', title: '基础课', description: '',
              priceAmount: '150.00', currency: 'CAD' }], nextCursor: null }) :
              url.startsWith('/api/slots') ? json({ items: [{ id: 'slot-1', zoneId: 'America/Toronto',
                localDate: '2026-10-10', startAt: booking.startAt, endAt: booking.endAt, status: 'OPEN',
                availableMountains: [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] }], nextCursor: null }) : json({}, 404),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: /10:00.*12:00/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Blue Mountain' }));
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  expect(await screen.findByText(/申请已提交，待教练确认/)).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '我的预约' }));
  expect(await screen.findByText('基础课')).toBeTruthy();
});

it('lets the coach edit and archive a course and requires a rejection reason', async () => {
  let course = { id: 'course-1', title: '基础课', description: '', priceAmount: '150.00', currency: 'CAD', active: true };
  let rejected = false;
  const writes: Array<{ url: string; body: Record<string, unknown> }> = [];
  const booking = { id: 'booking-1', slotId: 'slot-1', studentName: '学员甲', courseTitle: '基础课',
    priceAmount: '150.00', status: 'PENDING', localDate: '2026-10-10', zoneId: 'America/Toronto',
    startAt: '2026-10-10T14:00:00Z', endAt: '2026-10-10T16:00:00Z', location: 'Blue Mountain' };
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'PATCH' || options?.method === 'POST') {
      const body = JSON.parse(String(options.body)) as Record<string, unknown>;
      writes.push({ url, body });
      if (url === '/api/coach/courses/course-1' && options.method === 'PATCH') {
        course = { ...course, title: String(body.title), description: String(body.description), priceAmount: String(body.priceAmount) };
        return Promise.resolve(json(course));
      }
      if (url === '/api/coach/courses/course-1/archive') {
        course = { ...course, active: false }; return Promise.resolve(json(course));
      }
      if (url === '/api/coach/bookings/booking-1/reject') {
        rejected = true; return Promise.resolve(json({ ...booking, status: 'REJECTED', decisionReason: body.reason }));
      }
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '教练', level: null }) :
        url.startsWith('/api/coach/courses') ? json({ items: [course], nextCursor: null }) :
          url.startsWith('/api/coach/bookings') ? json({ items: [{ ...booking,
            status: rejected ? 'REJECTED' : 'PENDING', decisionReason: rejected ? '天气不合适' : null }], nextCursor: null }) :
            json({ items: [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.click(await screen.findByRole('button', { name: '编辑' }));
  const editTitle = screen.getByLabelText('课程名称', { selector: '.course-edit-form input' });
  await userEvent.clear(editTitle); await userEvent.type(editTitle, '进阶课');
  await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
  await waitFor(() => expect(writes.some((write) => write.url === '/api/coach/courses/course-1'
    && write.body.title === '进阶课')).toBe(true));
  await userEvent.click(screen.getByRole('button', { name: '删除课程' }));
  expect(screen.getByText(/已有预约不受影响/)).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '确认删除' }));
  expect(await screen.findByText('已下架')).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '预约申请' }));
  await userEvent.click(await screen.findByRole('button', { name: '拒绝' }));
  expect(screen.getByRole('button', { name: '确认拒绝' }).hasAttribute('disabled')).toBe(true);
  await userEvent.type(screen.getByLabelText('拒绝原因'), '天气不合适');
  await userEvent.click(screen.getByRole('button', { name: '确认拒绝' }));
  await waitFor(() => expect(writes.some((write) => write.url === '/api/coach/bookings/booking-1/reject'
    && write.body.reason === '天气不合适')).toBe(true));
});

it('lets the student cancel a pending application from the booking card', async () => {
  let cancelled = false;
  const booking = { id: 'booking-1', slotId: 'slot-1', studentName: 'Geer', status: 'PENDING',
    decisionReason: null, courseTitle: '基础课', priceAmount: '150.00', currency: 'CAD', location: 'Blue Mountain',
    zoneId: 'America/Toronto', localDate: '2026-10-10', startAt: '2026-10-10T14:00:00Z', endAt: '2026-10-10T16:00:00Z' };
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/bookings/booking-1/cancel' && options?.method === 'POST') {
      cancelled = true; return Promise.resolve(json({ ...booking, status: 'CANCELLED_BY_STUDENT' }));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: 'Geer', level: '入门' }) :
        url.startsWith('/api/bookings/mine') ? json({ items: [{ ...booking,
          status: cancelled ? 'CANCELLED_BY_STUDENT' : 'PENDING' }], nextCursor: null }) :
          json({ items: [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '取消预约' }));
  await userEvent.click(screen.getByRole('button', { name: '确认取消' }));
  expect(await screen.findByText('学员已取消')).toBeTruthy();
  expect(fetchMock.mock.calls.some(([url]) => url === '/api/bookings/booking-1/cancel')).toBe(true);
});

it('separates coach tabs and selects multiple calendar dates for one replacement', async () => {
  const writes: Array<{ url: string; body: { days: Array<{ localDate: string }> } }> = [];
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'POST') {
      writes.push({ url, body: JSON.parse(String(options.body)) as { days: Array<{ localDate: string }> } });
      return Promise.resolve(json({ slots: [], tails: [] }, 201));
    }
    return Promise.resolve(url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '张教练', level: null }) :
        url.startsWith('/api/coach/availability/month') ? json({ zoneId: 'America/Toronto', days: [] }) :
          json({ items: url.startsWith('/api/coach/mountains')
            ? [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] : [], nextCursor: null }));
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  expect(screen.getByRole('button', { name: '创建课程' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: '工作区' })).toBeNull();
  const selectable = await screen.findAllByRole('button', { name: /选择日期/ });
  await userEvent.click(selectable[0]);
  if (selectable.length > 1) await userEvent.click(selectable[1]);
  else {
    await userEvent.click(screen.getByRole('button', { name: '下一个月' }));
    await userEvent.click((await screen.findAllByRole('button', { name: /选择日期/ }))[0]);
  }
  await userEvent.click(screen.getByRole('button', { name: '上一个月' }));
  await userEvent.click(screen.getByRole('button', { name: '下一个月' }));
  expect(screen.getAllByRole('button', { name: /选择日期/, pressed: true }).length).toBeGreaterThan(0);
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  await waitFor(() => expect(writes.some(({ url }) => url === '/api/coach/availability/replacements')).toBe(true));
  expect(writes.find(({ url }) => url === '/api/coach/availability/replacements')?.body.days).toHaveLength(2);
});

it('lets the coach configure a mountain and publish a day range without a course', async () => {
  const writes: Array<{ url: string; body: Record<string, unknown> }> = [];
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'POST') {
      writes.push({ url, body: JSON.parse(String(options.body)) as Record<string, unknown> });
      return Promise.resolve(json(url === '/api/coach/mountains'
        ? { id: 'mountain-1', name: 'Blue Mountain', active: true }
        : { slots: [], tails: [] }, 201));
    }
    return Promise.resolve(
      url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
        url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '张教练', level: null }) :
          url.startsWith('/api/coach/mountains') ? json({ items: writes.some((write) => write.url === '/api/coach/mountains')
            ? [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] : [], nextCursor: null }) :
            json({ items: [], nextCursor: null }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  await userEvent.type(await screen.findByLabelText('雪场名称'), 'Blue Mountain');
  await userEvent.click(screen.getByRole('button', { name: '新增雪场' }));
  expect(await screen.findByText('Blue Mountain 已加入雪场列表。')).toBeTruthy();
  await screen.findByDisplayValue('Blue Mountain');
  const dateButton = (await screen.findAllByRole('button', { name: /选择日期/ }))[0];
  const chosenDate = dateButton.getAttribute('aria-label')?.split(' ')[1];
  await userEvent.click(dateButton);
  fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '16:00' } });
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  await waitFor(() => expect(writes.some((write) => write.url === '/api/coach/availability/replacements')).toBe(true));
  const batch = writes.find((write) => write.url === '/api/coach/availability/replacements')?.body;
  expect(batch).toMatchObject({ days: [{ localDate: chosenDate, startTime: '10:00', endTime: '16:00' }] });
});

it('submits the selected course, time, and mountain together', async () => {
  let application: Record<string, unknown> | null = null;
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/bookings' && options?.method === 'POST') {
      application = JSON.parse(String(options.body)) as Record<string, unknown>;
      return Promise.resolve(json({ id: 'booking-1', status: 'PENDING' }, 201));
    }
    return Promise.resolve(
      url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
        url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: 'Geer', level: '入门' }) :
          url.startsWith('/api/courses') ? json({ items: [{ id: 'course-1', title: '单板基础课', description: '', priceAmount: '150.00', currency: 'CAD' }], nextCursor: null }) :
            url.startsWith('/api/slots') ? json({ items: [{ id: 'slot-1', zoneId: 'America/Toronto', localDate: '2026-10-10',
              startAt: '2026-10-10T14:00:00Z', endAt: '2026-10-10T16:00:00Z', status: 'OPEN',
              availableMountains: [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] }], nextCursor: null }) :
              url.startsWith('/api/bookings/mine') ? json({ items: [], nextCursor: null }) : json({}, 404),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: /10:00.*12:00/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Blue Mountain' }));
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  await waitFor(() => expect(application).toMatchObject({ courseId: 'course-1', slotId: 'slot-1', mountainId: 'mountain-1' }));
});

it('lands a restored student session on real course and slot browsing', async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: 'Geer', level: '入门' }) :
        url.startsWith('/api/courses') ? json({ items: [{ id: 'course-1', title: '单板基础课', description: '从零开始', priceAmount: '150.00', currency: 'CAD' }], nextCursor: null }) :
          url.startsWith('/api/slots') ? json({ items: [{ id: 'slot-1', courseId: 'course-1', location: 'Blue Mountain', zoneId: 'America/Toronto', localDate: '2026-10-03', startAt: '2026-10-03T14:00:00Z', endAt: '2026-10-03T16:00:00Z' }], nextCursor: null }) :
            url.startsWith('/api/bookings/mine') ? json({ items: [], nextCursor: null }) : json({}, 404),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  expect(await screen.findByRole('heading', { name: /预约单板课/ })).toBeTruthy();
  expect(await screen.findByText('单板基础课')).toBeTruthy();
  expect(fetchMock.mock.calls.some(([url]) => url.startsWith('/api/courses'))).toBe(true);
  expect(fetchMock.mock.calls.some(([url]) => url.startsWith('/api/slots'))).toBe(true);
});

it('lands a restored coach session on the publishing and applications work area', async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '张教练', level: null }) :
        url.startsWith('/api/coach/courses') || url.startsWith('/api/coach/slots') || url.startsWith('/api/coach/bookings')
          ? json({ items: [], nextCursor: null }) : json({}, 404),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  expect(await screen.findByRole('heading', { name: /教练工作区/ })).toBeTruthy();
  expect(screen.getByRole('button', { name: '创建课程' })).toBeTruthy();
  expect(screen.getByRole('button', { name: '管理可用时间' })).toBeTruthy();
  expect(screen.getByRole('button', { name: '预约申请' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: '申请预约' })).toBeNull();
});

it('submits a selected slot once and explains that the application is pending', async () => {
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/bookings') expect(options?.method).toBe('POST');
    return Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: 'Geer', level: '入门' }) :
        url.startsWith('/api/courses') ? json({ items: [{ id: 'course-1', title: '单板基础课', description: '从零开始', priceAmount: '150.00', currency: 'CAD' }], nextCursor: null }) :
          url.startsWith('/api/slots') ? json({ items: [{ id: 'slot-1', zoneId: 'America/Toronto', localDate: '2026-10-03', startAt: '2026-10-03T14:00:00Z', endAt: '2026-10-03T16:00:00Z', status: 'OPEN', availableMountains: [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] }], nextCursor: null }) :
            url === '/api/bookings' ? json({ id: 'booking-1', status: 'PENDING', slotId: 'slot-1' }, 201) :
              url.startsWith('/api/bookings/mine') ? json({ items: [], nextCursor: null }) : json({}, 404),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: /10:00.*12:00/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Blue Mountain' }));
  await userEvent.click(screen.getByRole('button', { name: '申请预约' }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/bookings')).toBe(true));
  const request = fetchMock.mock.calls.find(([url]) => url === '/api/bookings')?.[1];
  expect((request?.headers as Record<string, string>)['Idempotency-Key']).toBeTruthy();
  expect(request?.body).toBe(JSON.stringify({ courseId: 'course-1', slotId: 'slot-1', mountainId: 'mountain-1' }));
  expect((await screen.findAllByText(/待教练确认/)).length).toBeGreaterThan(0);
});

it('continues browsing when a course list has another page', async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: 'Geer', level: '入门' }) :
        url.startsWith('/api/courses') ? json(url.includes('cursor=next-course')
          ? { items: [{ id: 'course-2', title: '进阶单板课', description: '', priceAmount: '180.00', currency: 'CAD' }], nextCursor: null }
          : { items: [{ id: 'course-1', title: '单板基础课', description: '', priceAmount: '150.00', currency: 'CAD' }], nextCursor: 'next-course' }) :
          url.startsWith('/api/slots') || url.startsWith('/api/bookings/mine')
            ? json({ items: [], nextCursor: null }) : json({}, 404),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '加载更多课程' }));
  expect(await screen.findByText('进阶单板课')).toBeTruthy();
  expect(fetchMock.mock.calls.some(([url]) => url.includes('cursor=next-course'))).toBe(true);
});

it('retries a timed out course publish with the same request key', async () => {
  const keys: string[] = [];
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/coach/courses' && options?.method === 'POST') {
      keys.push((options.headers as Record<string, string>)['Idempotency-Key']);
      return keys.length === 1 ? Promise.reject(new Error('网络暂时中断')) : Promise.resolve(json({ id: 'course-1' }, 201));
    }
    return Promise.resolve(
      url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
        url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '张教练', level: null }) :
          json({ items: [], nextCursor: null }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await userEvent.type(await screen.findByLabelText('课程名称'), '基础课');
  await userEvent.type(screen.getByLabelText('课程价格（CAD）'), '150');
  await userEvent.click(screen.getByRole('button', { name: '发布课程' }));
  expect(await screen.findByText('网络暂时中断')).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '发布课程' }));
  await waitFor(() => expect(keys).toHaveLength(2));
  expect(keys[1]).toBe(keys[0]);
});

it('lets the coach publish a course and independent availability through separate tabs', async () => {
  let coursePublished = false;
  let mountainPublished = false;
  const writes: Array<{ url: string; body: string; key: string }> = [];
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'POST' && url.startsWith('/api/coach/')) {
      writes.push({ url, body: String(options.body), key: (options.headers as Record<string, string>)['Idempotency-Key'] });
      if (url === '/api/coach/courses') coursePublished = true;
      if (url === '/api/coach/mountains') mountainPublished = true;
      return Promise.resolve(json(url === '/api/coach/availability/replacements'
        ? { slots: [{ id: 'slot-1' }], tails: [] } : { id: url.endsWith('/mountains') ? 'mountain-1' : 'course-1' }, 201));
    }
    return Promise.resolve(
      url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
        url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '张教练', level: null }) :
          url.startsWith('/api/coach/courses') ? json({ items: coursePublished ? [{ id: 'course-1', title: '基础课', description: '', priceAmount: '150.00', currency: 'CAD' }] : [], nextCursor: null }) :
            url.startsWith('/api/coach/mountains') ? json({ items: mountainPublished ? [{ id: 'mountain-1', name: 'Blue Mountain', active: true }] : [], nextCursor: null }) :
            json({ items: [], nextCursor: null }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '管理可用时间' }));
  await userEvent.type(await screen.findByLabelText('雪场名称'), 'Blue Mountain');
  await userEvent.click(screen.getByRole('button', { name: '新增雪场' }));
  await screen.findByDisplayValue('Blue Mountain');
  await userEvent.click(screen.getByRole('button', { name: '创建课程' }));
  await userEvent.type(await screen.findByLabelText('课程名称'), '基础课');
  await userEvent.type(screen.getByLabelText('课程价格（CAD）'), '150');
  await userEvent.click(screen.getByRole('button', { name: '发布课程' }));
  await userEvent.click(screen.getByRole('button', { name: '管理可用时间' }));
  const dateButton = (await screen.findAllByRole('button', { name: /选择日期/ }))[0];
  const chosenDate = dateButton.getAttribute('aria-label')?.split(' ')[1];
  await userEvent.click(dateButton);
  await userEvent.click(screen.getByRole('button', { name: '整天覆盖并发布' }));
  await waitFor(() => expect(writes).toHaveLength(3));
  expect(JSON.parse(writes[1].body)).toMatchObject({ title: '基础课', priceAmount: '150', currency: 'CAD' });
  expect(JSON.parse(writes[2].body)).toMatchObject({ days: [{ localDate: chosenDate, startTime: '10:00', endTime: '12:00' }] });
  expect(writes.every((write) => Boolean(write.key))).toBe(true);
});

it('keeps the session on 403 and clears student data on 401', async () => {
  let status = 403;
  const fetchMock = vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'student-1', role: 'STUDENT', name: 'Geer', level: '入门' }) :
        url.startsWith('/api/courses') ? json({ detail: status === 403 ? '没有访问权限' : '请先登录' }, status) :
          json({ items: [], nextCursor: null }),
  ));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  expect(await screen.findByText('没有访问权限')).toBeTruthy();
  expect(screen.getByRole('heading', { name: '预约单板课' })).toBeTruthy();
  status = 401;
  await userEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByRole('heading', { name: '欢迎回来' })).toBeTruthy();
  expect(screen.queryByText('Geer')).toBeNull();
});

it('sends the coach confirmation and refreshes the application status', async () => {
  let confirmed = false;
  const application = { id: 'booking-1', slotId: 'slot-1', studentName: '学员甲', status: 'PENDING',
    decisionReason: null, courseTitle: '基础课', priceAmount: '150.00', currency: 'CAD',
    location: 'Blue Mountain', zoneId: 'America/Toronto', localDate: '2026-10-10',
    startAt: '2026-10-10T14:00:00Z', endAt: '2026-10-10T16:00:00Z' };
  const fetchMock = vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/coach/bookings/booking-1/confirm' && options?.method === 'POST') {
      confirmed = true;
      return Promise.resolve(json({ ...application, status: 'CONFIRMED' }));
    }
    return Promise.resolve(
      url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
        url === '/api/auth/me' ? json({ id: 'coach-1', role: 'COACH', name: '张教练', level: null }) :
          url.startsWith('/api/coach/bookings') ? json({ items: [{ ...application, status: confirmed ? 'CONFIRMED' : 'PENDING' }], nextCursor: null }) :
            json({ items: [], nextCursor: null }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '确认' }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/coach/bookings/booking-1/confirm')).toBe(true));
  expect(await screen.findByText('已确认')).toBeTruthy();
});

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

it('opens a neutral email-code password recovery flow from login', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({}, 401) : json({ message: '如果账号可找回，请查收验证码' }),
  )));
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '忘记密码' }));
  await userEvent.type(screen.getByLabelText('邮箱'), 'geer@example.com');
  await userEvent.click(screen.getByRole('button', { name: '发送验证码' }));
  expect(await screen.findByLabelText('邮箱验证码')).toBeTruthy();
});

it('verifies the code, confirms a new password, then returns to login', async () => {
  let csrfRequests = 0;
  const fetchMock = vi.fn((url: string) => {
    if (url === '/api/auth/csrf') {
      csrfRequests += 1;
      if (csrfRequests === 3) return Promise.reject(new Error('Temporary network error'));
      return Promise.resolve(json({ token: 'csrf-' + csrfRequests, headerName: 'X-CSRF-TOKEN' }));
    }
    if (url === '/api/auth/login') return Promise.resolve(json({ id: '1', role: 'STUDENT', name: 'Geer', level: '入门' }));
    return Promise.resolve(url === '/api/auth/me' ? json({}, 401) : json({ message: 'ok' }));
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '忘记密码' }));
  await userEvent.type(screen.getByLabelText('邮箱'), 'geer@example.com');
  await userEvent.click(screen.getByRole('button', { name: '发送验证码' }));
  const codeInput = await screen.findByLabelText('邮箱验证码') as HTMLInputElement;
  expect(codeInput.inputMode).toBe('numeric');
  await userEvent.type(codeInput, '01234567');
  await userEvent.click(screen.getByRole('button', { name: '验证邮箱' }));
  const newPassword = await screen.findByLabelText('新密码');
  await userEvent.type(newPassword, 'new pass 456');
  await userEvent.type(screen.getByLabelText('确认新密码'), 'different');
  await userEvent.click(screen.getByRole('button', { name: '确认新密码' }));
  expect(fetchMock.mock.calls.some(([path]) => path === '/api/auth/password-recovery/complete')).toBe(false);
  await userEvent.clear(screen.getByLabelText('确认新密码'));
  await userEvent.type(screen.getByLabelText('确认新密码'), 'new pass 456');
  await userEvent.click(screen.getByRole('button', { name: '确认新密码' }));
  expect(await screen.findByText(/密码已更新/)).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
  expect(fetchMock).toHaveBeenCalledWith('/api/auth/password-recovery/verify', expect.objectContaining({
    body: JSON.stringify({ email: 'geer@example.com', code: '01234567' }),
  }));
  expect(fetchMock).toHaveBeenCalledWith('/api/auth/password-recovery/complete', expect.objectContaining({
    body: JSON.stringify({ newPassword: 'new pass 456', confirmPassword: 'new pass 456' }),
  }));
  await userEvent.click(screen.getByRole('button', { name: '返回登录' }));
  expect(screen.getByRole('heading', { name: '欢迎回来' })).toBeTruthy();
  expect((screen.getByLabelText('密码') as HTMLInputElement).value).toBe('');
  const loginButton = screen.getByRole('button', { name: /^登录/ }) as HTMLButtonElement;
  expect(loginButton.disabled).toBe(false);
  await userEvent.type(screen.getByLabelText('密码'), 'new pass 456');
  await userEvent.click(loginButton);
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', '无法建立安全连接，请稍后重试。');
  await userEvent.click(loginButton);
  expect(await screen.findByText(/欢迎回来，Geer/)).toBeTruthy();
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
