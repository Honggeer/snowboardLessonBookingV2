import { useEffect, useState } from 'react';
import './style.css';

type Health = 'loading' | 'up' | 'error';

export default function App() {
  const [health, setHealth] = useState<Health>('loading');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    fetch('/api/actuator/health')
      .then(async (response) => {
        if (!response.ok || (await response.json()).status !== 'UP') {
          throw new Error('Backend unhealthy');
        }
        if (active) setHealth('up');
      })
      .catch(() => {
        if (active) setHealth('error');
      });
    return () => { active = false; };
  }, [attempt]);

  return (
    <main className="shell">
      <span className="eyebrow">SNOWBOARD LESSON BOOKING · V2</span>
      <h1>约课平台</h1>
      <p className="intro">课程预约功能正在准备中。请稍后再来查看。</p>
      <section className="status-card" aria-labelledby="health-title">
        <h2 id="health-title">服务状态</h2>
        {health === 'loading' && <p role="status">连接中…</p>}
        {health === 'up' && <p role="status" className="healthy">后端运行正常</p>}
        {health === 'error' && (
          <>
            <p role="alert">无法连接后端服务。</p>
            <button type="button" onClick={() => { setHealth('loading'); setAttempt((value) => value + 1); }}>
              重试连接
            </button>
          </>
        )}
      </section>
    </main>
  );
}
