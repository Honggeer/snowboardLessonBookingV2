import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => vi.unstubAllGlobals());

it('shows loading, then the backend health status', async () => {
  let complete!: (value: Response) => void;
  const fetchMock = vi.fn(() => new Promise<Response>((resolve) => { complete = resolve; }));
  vi.stubGlobal('fetch', fetchMock);

  render(<App />);
  expect(screen.getByRole('status').textContent).toMatch(/连接中/);
  expect(fetchMock).toHaveBeenCalledWith('/api/actuator/health');

  complete({ ok: true, json: async () => ({ status: 'UP' }) } as Response);
  await waitFor(() => expect(screen.getByRole('status').textContent).toMatch(/运行正常/));
});

it('shows an error and retries after a failed request', async () => {
  const fetchMock = vi.fn()
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({ ok: true, json: async () => ({ status: 'UP' }) });
  vi.stubGlobal('fetch', fetchMock);

  render(<App />);
  expect((await screen.findByRole('alert')).textContent).toMatch(/无法连接/);
  await userEvent.click(screen.getByRole('button', { name: /重试/ }));
  expect((await screen.findByRole('status')).textContent).toMatch(/运行正常/);
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

it('treats an unhealthy backend response as an error', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'DOWN' }) }));
  render(<App />);
  expect((await screen.findByRole('alert')).textContent).toMatch(/无法连接/);
});

it('describes upcoming booking to visitors without internal project process', () => {
  vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
  render(<App />);
  expect(screen.getByText(/课程预约功能正在准备中/)).toBeTruthy();
  expect(screen.queryByText(/计划获批/)).toBeNull();
});
