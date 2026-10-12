import { useEffect, useRef, useState } from 'react';
import './lessonSummaries.css';

type Csrf = { token: string; headerName: string };
type Lesson = { id: string; studentName: string; courseTitle: string; location: string; zoneId: string;
  localDate: string; startAt: string; endAt: string; summaryAvailable?: boolean; summaryState?: string };
type Content = { practiceContent: string; improvementAreas: string; reinforcementExercises: string };
type Summary = Content & { bookingId: string; state: string; version: number; updatedAt: string | null; publishedAt: string | null };
type Page = { items: Lesson[]; nextCursor: string | null };
const fields = [
  ['practiceContent', '本节练习内容', '记录本节课练习的动作和要点'],
  ['improvementAreas', '待加强', '指出接下来需要加强的地方'],
  ['reinforcementExercises', '课后强化练习', '安排课后可以继续练习的内容'],
] as const;
class SummaryError extends Error {
  constructor(readonly status: number, message: string) { super(message); }
}
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { credentials: 'same-origin', cache: 'no-store', ...init });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new SummaryError(response.status, body.detail ?? '加载失败，请稍后重试。');
  return body as T;
}
function schedule(lesson: Lesson) {
  const time = (instant: string) => new Intl.DateTimeFormat('zh-CN', {
    timeZone: lesson.zoneId, hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(instant));
  return `${lesson.localDate} · ${time(lesson.startAt)}–${time(lesson.endAt)} · ${lesson.zoneId}`;
}
function contentOf(summary: Summary): Content {
  return { practiceContent: summary.practiceContent, improvementAreas: summary.improvementAreas,
    reinforcementExercises: summary.reinforcementExercises };
}
export default function LessonSummaries({ coach, targetId, refreshCsrf, onUnauthorized, onDirtyChange }:
  { coach: boolean; targetId?: string; refreshCsrf: () => Promise<Csrf>; onUnauthorized: () => void;
    onDirtyChange: (dirty: boolean) => void }) {
  const root = coach ? '/api/coach/bookings' : '/api/bookings';
  const [rows, setRows] = useState<Lesson[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(targetId ?? null);
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [content, setContent] = useState<Content | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);
  const generation = useRef(0);
  const mounted = useRef(false);
  const dirty = !!(coach && summary && content && fields.some(([key]) => content[key] !== summary[key]));
  useEffect(() => {
    onDirtyChange(dirty);
    const warn = (event: BeforeUnloadEvent) => { if (dirty) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', warn);
    return () => { onDirtyChange(false); window.removeEventListener('beforeunload', warn); };
  }, [dirty, onDirtyChange]);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; generation.current++; }; }, []);
  useEffect(() => { setSelected(targetId ?? null); }, [targetId]);
  function fail(reason: unknown) {
    if (reason instanceof SummaryError && reason.status === 401) { onUnauthorized(); return; }
    if (reason instanceof SummaryError && reason.status === 409) {
      setError('总结已在其他窗口更新。你的输入已保留，请重新加载并核对后再保存。');
    } else if (reason instanceof SummaryError && (reason.status === 403 || reason.status === 404)) {
      setError(coach ? '找不到这节课程，或当前账号无权编辑。' : '总结尚未发布，或当前账号无权查看。');
    } else setError(reason instanceof Error ? reason.message : '操作失败，请重试。');
  }
  useEffect(() => {
    const current = ++generation.current;
    setLoading(true); setError(''); setNotice(''); setLesson(null); setSummary(null); setContent(null);
    if (selected) {
      Promise.all([request<Lesson>(`${root}/${encodeURIComponent(selected)}`),
        request<Summary>(`${root}/${encodeURIComponent(selected)}/summary`)])
        .then(([nextLesson, nextSummary]) => {
          if (generation.current !== current) return;
          setLesson(nextLesson); setSummary(nextSummary); setContent(contentOf(nextSummary));
        }).catch((reason) => { if (generation.current === current) fail(reason); })
        .finally(() => { if (generation.current === current) setLoading(false); });
    } else {
      setRows([]); setCursor(null);
      request<Page>(`${root}/completed?limit=20`).then((page) => {
        if (!Array.isArray(page.items)) throw new Error('课程列表加载失败，请重试。');
        if (generation.current !== current) return;
        setRows(page.items); setCursor(page.nextCursor);
      }).catch((reason) => { if (generation.current === current) fail(reason); })
        .finally(() => { if (generation.current === current) setLoading(false); });
    }
    return () => { generation.current++; };
  }, [root, selected, reload]);
  function leave(action: () => void) {
    if (!busy && (!dirty || window.confirm('课程总结尚未保存，确定离开吗？'))) action();
  }
  async function more() {
    if (!cursor || loadingMore) return;
    const current = generation.current;
    setLoadingMore(true); setError('');
    try {
      const page = await request<Page>(`${root}/completed?limit=20&cursor=${encodeURIComponent(cursor)}`);
      if (!Array.isArray(page.items)) throw new Error('课程列表加载失败，请重试。');
      if (generation.current !== current) return;
      setRows((old) => [...old, ...page.items.filter((row) => !old.some((item) => item.id === row.id))]); setCursor(page.nextCursor);
    } catch (reason) { if (generation.current === current) fail(reason); }
    finally { if (mounted.current) setLoadingMore(false); }
  }
  async function save(publish: boolean) {
    if (!content || !summary || !selected || busy) return;
    if (fields.some(([key]) => Array.from(content[key].trim()).length > 3000)) {
      setError('每块内容最多 3000 个字符。'); return;
    }
    if ((publish || summary.state === 'PUBLISHED') && fields.some(([key]) => !content[key].trim())) {
      setError('发布前请填写全部三块内容。'); return;
    }
    const current = generation.current;
    setBusy(true); setError(''); setNotice('');
    try {
      const csrf = await refreshCsrf();
      if (generation.current !== current) return;
      const saved = await request<Summary>(`${root}/${encodeURIComponent(selected)}/summary${publish ? '/publish' : ''}`, {
        method: publish ? 'POST' : 'PATCH',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ ...content, expectedVersion: summary.version }),
      });
      if (generation.current !== current) return;
      setSummary(saved); setContent(contentOf(saved));
      setNotice(publish ? '总结已发布，将通过邮件通知学员查看。' : saved.state === 'PUBLISHED' ? '修改已保存，学员可以查看最新内容。' : '草稿已保存，仅你可见。');
    } catch (reason) { if (generation.current === current) fail(reason); }
    finally { if (mounted.current) setBusy(false); }
  }
  return <section className="booking-panel lesson-summaries" aria-label={coach ? '课程总结' : '已完成课程'}>
    <div className="panel-heading"><h2>{selected ? '课程总结' : coach ? '课程总结' : '已完成课程'}</h2>
      {selected ? <button type="button" disabled={busy} onClick={() => leave(() => setSelected(null))}>返回课程列表</button> :
        <button type="button" disabled={loading} onClick={() => setReload((value) => value + 1)}>刷新</button>}</div>
    {loading && <p role="status">正在加载课程…</p>}
    {error && <div className="booking-alert error" role="alert"><span>{error}</span>
      <button type="button" disabled={busy || loading} onClick={() => leave(() => setReload((value) => value + 1))}>重新加载</button></div>}
    {notice && <p className="booking-inline-status success" role="status">{notice}</p>}
    {!selected && !loading && !error && rows.length === 0 && <p className="booking-empty">还没有已完成课程。</p>}
    {!selected && <div className="completed-lesson-list">{rows.map((row) => <article className="completed-lesson" key={row.id}>
      <div><span className="lesson-summary-state">{coach ? row.summaryState === 'PUBLISHED' ? '已发布' : row.summaryState === 'DRAFT' ? '草稿' : '待填写' : row.summaryAvailable ? '总结已发布' : '总结待教练发布'}</span>
        <h3>{row.courseTitle}</h3>{coach && <p>学员：{row.studentName}</p>}
        <p>{schedule(row)}</p><p>{row.location}</p></div>
      {(coach || row.summaryAvailable) && <button type="button" onClick={() => setSelected(row.id)}>
        {coach ? row.summaryState === 'PUBLISHED' ? '编辑总结' : '填写总结' : '查看总结'}</button>}
    </article>)}</div>}
    {!selected && cursor && <button type="button" className="booking-load-more" disabled={loadingMore} onClick={more}>{loadingMore ? '正在加载…' : '加载更多课程'}</button>}
    {lesson && summary && content && <>
      <div className="lesson-summary-heading"><h3>{lesson.courseTitle}</h3>{coach && <p>学员：{lesson.studentName}</p>}
        <p>{schedule(lesson)}</p><p>{lesson.location}</p>
        <p className="booking-note">{summary.state === 'PUBLISHED' ? '已发布' : summary.state === 'DRAFT' ? '草稿 · 仅教练可见' : '待填写'}
          {summary.publishedAt && ` · 发布于 ${new Date(summary.publishedAt).toLocaleString('zh-CN')}`}</p></div>
      {coach ? <form onSubmit={(event) => { event.preventDefault(); void save(false); }}>
        {fields.map(([key, label, placeholder]) => <div className="lesson-summary-field" key={key}>
          <label htmlFor={`summary-${key}`}>{label}</label>
          <textarea id={`summary-${key}`} rows={5} value={content[key]} placeholder={placeholder} disabled={busy}
            aria-describedby={`summary-${key}-count`} onChange={(event) => setContent({ ...content, [key]: event.target.value })} />
          <span id={`summary-${key}-count`} className="summary-character-count">{Array.from(content[key]).length} / 3000</span>
        </div>)}
        <p className="booking-note">{summary.state === 'PUBLISHED' ? '保存后学员会看到最新内容，不会再次发送邮件。' : '草稿仅你可见。三块填写完成后发布，学员即可查看并收到邮件通知。'}</p>
        <div className="lesson-summary-actions"><button type="submit" disabled={busy}>{busy ? '保存中…' : summary.state === 'PUBLISHED' ? '保存修改' : '保存草稿'}</button>
          {summary.state !== 'PUBLISHED' && <button type="button" className="booking-primary" disabled={busy} onClick={() => save(true)}>发布总结并通知学员</button>}</div>
      </form> : <div className="lesson-summary-reading">{fields.map(([key, label]) => <section key={key}><h3>{label}</h3><p>{summary[key]}</p></section>)}</div>}
    </>}
  </section>;
}
