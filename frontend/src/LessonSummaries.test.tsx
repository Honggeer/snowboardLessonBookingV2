import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;
afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });
const lesson = { id: 'lesson-1', studentName: '学员', courseTitle: '换刃练习课', location: 'Blue Mountain', zoneId: 'America/Toronto',
  localDate: '2026-10-01', startAt: '2026-10-01T14:00:00Z', endAt: '2026-10-01T16:00:00Z', summaryAvailable: true, summaryState: 'NOT_STARTED' };
function mockAccount(role: 'STUDENT' | 'COACH') {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
    url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) :
      url === '/api/auth/me' ? json({ id: 'account-1', role, name: role === 'COACH' ? '教练' : '学员', level: null }) :
        url === '/api/student/contact' ? json({ phone: '+14165550123' }) :
          url === '/api/bookings/lesson-1' || url === '/api/coach/bookings/lesson-1' ? json(lesson) :
          url.includes('/completed') ? json({ items: [lesson], nextCursor: null }) :
            url.endsWith('/summary') ? json({ bookingId: lesson.id, state: 'PUBLISHED', practiceContent: '练习前刃换刃',
              improvementAreas: '加强重心移动', reinforcementExercises: '课后复习换刃', version: 1, publishedAt: '2026-10-01T17:00:00Z', updatedAt: '2026-10-01T17:00:00Z' }) :
              json({ items: [], nextCursor: null }),
  )));
}
it('opens completed lesson history and displays all three published sections', async () => {
  mockAccount('STUDENT');
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '我的预约' }));
  await userEvent.click(await screen.findByRole('button', { name: '已完成课程' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看总结' }));
  expect(await screen.findByRole('heading', { name: '本节练习内容' })).toBeTruthy();
  expect(screen.getByRole('heading', { name: '待加强' })).toBeTruthy();
  expect(screen.getByRole('heading', { name: '课后强化练习' })).toBeTruthy();
  expect(screen.getByText('课后复习换刃')).toBeTruthy();
});
it('provides a dedicated coach lesson summary entry', async () => {
  mockAccount('COACH');
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '课程总结' }));
  expect(await screen.findByText('换刃练习课')).toBeTruthy();
});

import LessonSummaries from './LessonSummaries';
import { act, waitFor } from '@testing-library/react';
const summary = { bookingId: lesson.id, state: 'NOT_STARTED', practiceContent: '', improvementAreas: '', reinforcementExercises: '', version: 0, updatedAt: null, publishedAt: null };
function componentFetch(options: { conflict?: boolean; forbidden?: number; moreFailure?: boolean } = {}) {
  let saved = { ...summary };
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (options.forbidden) return json({ detail: '不可查看' }, options.forbidden);
    if (init?.method) {
      if (options.conflict) return json({}, 409);
      const body = JSON.parse(init.body as string);
      saved = { ...saved, ...body, state: url.endsWith('/publish') || saved.state === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT', version: saved.version + 1 };
      return json(saved);
    }
    if (url.includes('cursor=')) return options.moreFailure ? json({ detail: '分页请求失败' }, 503) : json({ items: [{ ...lesson, id: 'lesson-2', courseTitle: '第二节课' }], nextCursor: null });
    if (url.includes('/completed')) return json({ items: [lesson], nextCursor: 'lesson-1' });
    if (url.endsWith('/summary')) return json(saved);
    return json(lesson);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
const componentProps = { coach: true, refreshCsrf: async () => ({ token: 'csrf-value', headerName: 'X-CSRF-TOKEN' }), onUnauthorized: vi.fn(), onDirtyChange: vi.fn() };
it('saves a private partial draft, requires all three fields and publishes using its current version and csrf', async () => {
  const fetchMock = componentFetch();render(<LessonSummaries {...componentProps} />);
  await userEvent.click(await screen.findByRole('button', { name: '填写总结' }));
  await userEvent.type(await screen.findByLabelText('本节练习内容'), '换刃');
  await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));
  expect(await screen.findByText('草稿已保存，仅你可见。')).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '发布总结并通知学员' }));
  expect(await screen.findByText('发布前请填写全部三块内容。')).toBeTruthy();
  await userEvent.type(screen.getByLabelText('待加强'), '重心');
  await userEvent.type(screen.getByLabelText('课后强化练习'), '每日复习');
  await userEvent.click(screen.getByRole('button', { name: '发布总结并通知学员' }));
  expect(await screen.findByText('总结已发布，将通过邮件通知学员查看。')).toBeTruthy();
  const publishCall = fetchMock.mock.calls.find(([url]) => url.endsWith('/publish'))!;
  expect(JSON.parse(publishCall[1]!.body as string)).toEqual({ practiceContent: '换刃', improvementAreas: '重心', reinforcementExercises: '每日复习', expectedVersion: 1 });
  expect(publishCall[1]!.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-value' });
  expect(screen.getByRole('button', { name: '保存修改' })).toBeTruthy();
  await userEvent.type(screen.getByLabelText('待加强'), '，继续加强');
  await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
  expect(await screen.findByText('修改已保存，学员可以查看最新内容。')).toBeTruthy();
  expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/publish'))).toHaveLength(1);
});
it('keeps unsaved input on conflict and prompts before leaving', async () => {
  componentFetch({ conflict: true });const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
  render(<LessonSummaries {...componentProps} targetId={lesson.id} />);
  await userEvent.type(await screen.findByLabelText('本节练习内容'), '保留这段输入');
  await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));
  expect(await screen.findByText(/总结已在其他窗口更新/)).toBeTruthy();
  expect((screen.getByLabelText('本节练习内容') as HTMLTextAreaElement).value).toBe('保留这段输入');
  await userEvent.click(screen.getByRole('button', { name: '返回课程列表' }));
  expect(confirm).toHaveBeenCalled();expect(screen.getByLabelText('本节练习内容')).toBeTruthy();confirm.mockRestore();
});
it('retains earlier rows on pagination failure and supports loading another page', async () => {
  const options = { moreFailure: true };componentFetch(options);
  render(<LessonSummaries {...componentProps} />);
  await userEvent.click(await screen.findByRole('button', { name: '加载更多课程' }));
  expect(await screen.findByText('分页请求失败')).toBeTruthy();expect(screen.getByText('换刃练习课')).toBeTruthy();
  options.moreFailure = false;await userEvent.click(screen.getByRole('button', { name: '加载更多课程' }));
  expect(await screen.findByText('第二节课')).toBeTruthy();expect(screen.getByText('换刃练习课')).toBeTruthy();
});
it('only an expired session invokes reauthentication; forbidden access preserves the session', async () => {
  const unauthorized = vi.fn();componentFetch({ forbidden: 403 });
  const view = render(<LessonSummaries {...componentProps} targetId={lesson.id} onUnauthorized={unauthorized} />);
  expect(await screen.findByText('找不到这节课程，或当前账号无权编辑。')).toBeTruthy();expect(unauthorized).not.toHaveBeenCalled();
  view.unmount();componentFetch({ forbidden: 401 });
  render(<LessonSummaries {...componentProps} targetId={lesson.id} onUnauthorized={unauthorized} />);
  await waitFor(() => expect(unauthorized).toHaveBeenCalled());
});
it('drops a late response after switching to another account component', async () => {
  let complete!: (response: Response) => void;
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>((resolve) => { complete = resolve; })));
  const old = render(<LessonSummaries {...componentProps} />);old.unmount();
  componentFetch();render(<LessonSummaries {...componentProps} />);
  await screen.findByText('换刃练习课');
  await act(async () => { complete(json({ items: [{ ...lesson, courseTitle: '前一个账号的课程' }], nextCursor: null })); });
  expect(screen.queryByText('前一个账号的课程')).toBeNull();
});
it('opens a summary email link directly after account discovery and denies a role mismatch', async () => {
  window.history.replaceState(null, '', '/#/completed-lessons/lesson-1');mockAccount('STUDENT');
  const view = render(<App />);expect(await screen.findByText('课后复习换刃')).toBeTruthy();view.unmount();
  mockAccount('COACH');render(<App />);
  expect(await screen.findByText('此预约链接需要使用对应角色的账号登录。')).toBeTruthy();
});
it('keeps unpublished completed lessons visible without exposing an editor to the student', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json({ items: [{ ...lesson, summaryAvailable: false }], nextCursor: null })));
  render(<LessonSummaries {...componentProps} coach={false} />);
  expect(await screen.findByText('总结待教练发布')).toBeTruthy();expect(screen.queryByRole('button', { name: '查看总结' })).toBeNull();
});
it('guards unsaved coach content when navigating tabs or browser history', async () => {
  window.history.replaceState(null, '', '/#/coach-lesson-summaries/lesson-1');mockAccount('COACH');
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);render(<App />);
  const field = await screen.findByLabelText('本节练习内容');await userEvent.type(field, '未保存的内容');
  await userEvent.click(screen.getByRole('button', { name: '创建课程' }));
  expect(confirm).toHaveBeenCalled();expect((field as HTMLTextAreaElement).value).toContain('未保存的内容');
  act(() => { window.history.replaceState(null, '', '/about-geer');window.dispatchEvent(new PopStateEvent('popstate')); });
  expect(window.location.pathname).toBe('/');expect(screen.getByLabelText('本节练习内容')).toBeTruthy();
  confirm.mockReturnValue(true);await userEvent.click(screen.getByRole('button', { name: '创建课程' }));
  expect(await screen.findByRole('heading', { name: '发布课程' })).toBeTruthy();expect(screen.queryByLabelText('本节练习内容')).toBeNull();
  confirm.mockRestore();
});
