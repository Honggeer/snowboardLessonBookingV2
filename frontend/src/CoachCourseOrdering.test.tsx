import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });

const course = (id: string, title: string, active?: boolean) => ({ id, title, active, description: '', priceAmount: '150.00', currency: 'CAD' });
const json = (body: unknown) => ({ ok: true, status: 200, json: async () => body }) as Response;
const titles = () => screen.queryAllByRole('article').map(row => row.querySelector(':scope > div > strong')?.textContent);

it('hides archived courses after initial loading and loading more, preserving published course order', async () => {
  const first = [course('old-1', '下架一', false), course('live-1', '发布一', true), course('old-2', '下架二', false),
    course('legacy', '旧版发布课程'), course('live-2', '发布二', true)];
  const next = [course('old-3', '下架三', false), course('live-3', '发布三', true)];
  const fetchMock = vi.fn((url: string) => Promise.resolve(json(
    url === '/api/auth/me' ? { id: 'coach', name: 'GEER', role: 'COACH', level: null } :
      url === '/api/auth/csrf' ? { token: 'csrf', headerName: 'X-CSRF-TOKEN' } :
        url.startsWith('/api/coach/courses') ? (url.includes('cursor=next')
          ? { items: next, nextCursor: null } : { items: first, nextCursor: 'next' }) : { items: [], nextCursor: null },
  )));
  vi.stubGlobal('fetch', fetchMock); render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await screen.findByText('旧版发布课程');
  expect(titles()).toEqual(['发布一', '旧版发布课程', '发布二']);
  expect(first.map(item => item.title)).toEqual(['下架一', '发布一', '下架二', '旧版发布课程', '发布二']);
  await userEvent.click(screen.getByRole('button', { name: '加载更多课程' }));
  await screen.findByText('发布三');
  expect(titles()).toEqual(['发布一', '旧版发布课程', '发布二', '发布三']);
  expect(fetchMock.mock.calls.some(([url]) => url.includes('cursor=next'))).toBe(true);
});

it('removes courses only after successful archiving and shows an empty state after the last published course', async () => {
  const items = [course('live-1', '发布一', true), course('live-2', '发布二', true), course('old-1', '下架一', false)];
  const fetchMock = vi.fn((url: string, options?: RequestInit) => Promise.resolve(json(
    url === '/api/auth/me' ? { id: 'coach', name: 'GEER', role: 'COACH', level: null } :
      url === '/api/auth/csrf' ? { token: 'csrf', headerName: 'X-CSRF-TOKEN' } :
        url === '/api/coach/courses/live-1/archive' && options?.method === 'POST' ? { ...items[0], active: false } :
          url === '/api/coach/courses/live-2/archive' && options?.method === 'POST' ? { ...items[1], active: false } :
          url.startsWith('/api/coach/courses') ? { items, nextCursor: null } : { items: [], nextCursor: null },
  )));
  vi.stubGlobal('fetch', fetchMock); render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await screen.findByText('发布一');
  const row = screen.getAllByRole('article')[0];
  await userEvent.click(within(row).getByRole('button', { name: '删除课程' }));
  await userEvent.click(within(row).getByRole('button', { name: '确认删除' }));
  await screen.findByText('课程已从学员选课列表移除；已有预约不受影响。');
  await waitFor(() => expect(titles()).toEqual(['发布二']));
  const remaining = screen.getByRole('article');
  await userEvent.click(within(remaining).getByRole('button', { name: '删除课程' }));
  await userEvent.click(within(remaining).getByRole('button', { name: '确认删除' }));
  await screen.findByText('尚无已发布课程。');
  expect(titles()).toEqual([]);
  expect(items.map(item => item.active)).toEqual([true, true, false]);
  expect(fetchMock.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(false);
});

it('keeps loading more available when a page contains only archived courses', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(json(
    url === '/api/auth/me' ? { id: 'coach', name: 'GEER', role: 'COACH', level: null } :
      url === '/api/auth/csrf' ? { token: 'csrf', headerName: 'X-CSRF-TOKEN' } :
        url.startsWith('/api/coach/courses') ? (url.includes('cursor=next')
          ? { items: [course('old-2', '下架二', false), course('live-1', '发布一', true)], nextCursor: null }
          : { items: [course('old-1', '下架一', false)], nextCursor: 'next' }) : { items: [], nextCursor: null },
  ))));
  render(<App />);
  await userEvent.click(await screen.findByRole('button', { name: '创建课程' }));
  await screen.findByText('当前暂无可显示课程，请加载更多。');
  expect(titles()).toEqual([]);
  await userEvent.click(screen.getByRole('button', { name: '加载更多课程' }));
  await screen.findByText('发布一');
  expect(titles()).toEqual(['发布一']);
  expect(screen.queryByText('当前暂无可显示课程，请加载更多。')).toBeNull();
  expect(screen.queryByRole('button', { name: '加载更多课程' })).toBeNull();
});
