import { useEffect, useId, useRef, useState, type PointerEvent } from 'react';
import { ProfileApiError, profileRequest, requestKey, type Csrf, type MediaLink, type Upload } from './coachProfileApi';
import { CourseSummary, type Course } from './CourseCard';

export type CoverDraft = { coverAssetId: string | null; cover: MediaLink | null; coverPositionX: number; coverPositionY: number };
export const emptyCover = (): CoverDraft => ({ coverAssetId: null, cover: null, coverPositionX: 50, coverPositionY: 50 });
type Pending = { file: File; key: string; id?: string; uploaded?: boolean };
const clamp = (value: number) => Math.round(Math.min(100, Math.max(0, value)) * 100) / 100;

export default function CourseCoverEditor({ value, onChange, disabled, refreshCsrf, onUnauthorized, onPendingChange, course }: {
  value: CoverDraft; onChange: (value: CoverDraft) => void; disabled: boolean; refreshCsrf: () => Promise<Csrf>;
  onUnauthorized: () => void; onPendingChange: (pending: boolean) => void;
  course?: Pick<Course, 'title' | 'description' | 'priceAmount'>;
}) {
  const id = useId(), [busy, setBusy] = useState(false), [error, setError] = useState(''), [status, setStatus] = useState('');
  const [progress, setProgress] = useState(0), [retry, setRetry] = useState(false);
  const [previewUnavailable, setPreviewUnavailable] = useState<string | null>(null);
  const pending = useRef<Pending | null>(null), mounted = useRef(false), xhr = useRef<XMLHttpRequest | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null), cancelWait = useRef<(() => void) | null>(null);
  const controller = useRef<AbortController | null>(null), dimensions = useRef({ width: value.cover?.width ?? 0, height: value.cover?.height ?? 0 });
  const drag = useRef<{ pointer: number; startY: number; y: number; travel: number } | null>(null);
  useEffect(() => {
    mounted.current = true; controller.current = new AbortController();
    return () => { mounted.current = false; controller.current?.abort(); xhr.current?.abort();
      if (timer.current) clearTimeout(timer.current); cancelWait.current?.(); pending.current = null; onPendingChange(false); };
  }, [onPendingChange]);
  function fail(reason: unknown) {
    if (!mounted.current) return;
    if (reason instanceof ProfileApiError && reason.status === 401) onUnauthorized();
    else setError(reason instanceof Error ? reason.message : '上传未完成，请重试。');
  }
  async function write(path: string, body: object, key?: string) {
    const csrf = await refreshCsrf();
    if (!mounted.current) throw new Error('上传已停止。');
    return profileRequest<Upload>(path, { method: 'POST', signal: controller.current?.signal,
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token, ...(key ? { 'Idempotency-Key': key } : {}) }, body: JSON.stringify(body) });
  }
  function put(url: string, file: File, headers: Record<string, string>) {
    return new Promise<void>((resolve, reject) => {
      const request = new XMLHttpRequest(); xhr.current = request; request.open('PUT', url); request.timeout = 120000;
      Object.entries(headers).forEach(([key, header]) => request.setRequestHeader(key, header));
      request.upload.onprogress = (event) => { if (mounted.current && event.lengthComputable) setProgress(Math.round(event.loaded / event.total * 100)); };
      request.onload = () => { xhr.current = null; if (request.status >= 200 && request.status < 300) resolve(); else reject(new Error('文件上传未完成，请重试。')); };
      request.onerror = request.ontimeout = () => { xhr.current = null; reject(new Error('文件上传连接失败，请重试。')); };
      request.onabort = () => reject(new Error('上传已停止。')); request.send(file);
    });
  }
  function finish(upload: Upload) {
    if (upload.status === 'READY' && upload.preview) {
      dimensions.current = { width: upload.preview.width ?? 0, height: upload.preview.height ?? 0 };
      onChange({ coverAssetId: upload.id, cover: upload.preview, coverPositionX: 50, coverPositionY: 50 });
      pending.current = null; onPendingChange(false); setRetry(false); setStatus('封面已验证，保存课程后生效。'); return true;
    }
    if (['REJECTED', 'FAILED', 'EXPIRED', 'DELETING', 'DELETED'].includes(upload.status)) {
      pending.current = null; onPendingChange(false); setRetry(false); setStatus('');
      throw new Error(upload.status === 'REJECTED' ? '图片内容或尺寸不符合要求，请重新选择 JPG/PNG（宽高不超过 4096）。' : '这次上传已失效，请重新选择图片。');
    }
    return false;
  }
  async function check(assetId: string) {
    for (let count = 0; count < 60 && mounted.current; count++) {
      const upload = await profileRequest<Upload>(`/api/coach/media/uploads/${encodeURIComponent(assetId)}`, { signal: controller.current?.signal });
      if (!mounted.current || finish(upload)) return;
      setStatus('正在验证图片…'); setProgress(100);
      await new Promise<void>((resolve) => { cancelWait.current = resolve; timer.current = setTimeout(resolve, 2000); });
    }
    if (mounted.current) setStatus('图片仍在验证，稍后可查询上传状态。');
  }
  async function upload(file?: File) {
    if (busy || disabled) return;
    const current: Pending | null = file ? { file, key: requestKey() } : pending.current;
    if (!current) return;
    const type = current.file.type || (/\.png$/i.test(current.file.name) ? 'image/png' : /\.jpe?g$/i.test(current.file.name) ? 'image/jpeg' : '');
    if (current.file.size > 8 * 1024 * 1024) { setError('图片不能超过 8 MiB，请压缩后再上传。'); return; }
    if (!current.file.size || !['image/png', 'image/jpeg'].includes(type)) { setError('请选择非空的 JPG/PNG 图片。'); return; }
    pending.current = current; onPendingChange(true); setBusy(true); setRetry(true); setError(''); setProgress(0);
    try {
      if (!current.uploaded) {
        setStatus('正在准备上传…');
        const ticket = await write('/api/coach/media/uploads', { purpose: 'COURSE_COVER', contentType: type, size: current.file.size }, current.key);
        if (!mounted.current) return;
        current.id = ticket.id;
        if (finish(ticket)) return;
        if (ticket.status === 'UPLOADING') {
          if (!ticket.uploadUrl) throw new Error('上传授权已过期，请重新选择图片。');
          setStatus('正在上传图片…'); await put(ticket.uploadUrl, current.file, ticket.headers);
        }
        current.uploaded = true;
      }
      if (!mounted.current || !current.id) return;
      const result = await write(`/api/coach/media/uploads/${encodeURIComponent(current.id)}/complete`, {});
      if (!mounted.current || finish(result)) return;
      await check(current.id);
    } catch (reason) { fail(reason); } finally { if (mounted.current) setBusy(false); }
  }
  async function refreshPreview() {
    if (!value.coverAssetId || busy || disabled) return;
    setBusy(true); setError('');
    try {
      const upload = await profileRequest<Upload>(`/api/coach/media/uploads/${encodeURIComponent(value.coverAssetId)}`, { signal: controller.current?.signal });
      if (!mounted.current) return;
      if (upload.status !== 'READY' || !upload.preview) throw new Error('封面暂不可预览，可保留当前封面或替换图片。');
      onChange({ ...value, cover: upload.preview }); setPreviewUnavailable(null); setStatus('封面预览已刷新。');
    } catch (reason) { fail(reason); } finally { if (mounted.current) setBusy(false); }
  }
  function startDrag(event: PointerEvent<HTMLDivElement>) {
    if (disabled || busy || !value.cover || previewUnavailable === value.coverAssetId || (event.pointerType === 'mouse' && event.button !== 0)) return;
    const bounds = event.currentTarget.getBoundingClientRect(), image = dimensions.current;
    if (!image.width || !image.height) return;
    const renderedHeight = image.height * bounds.width / image.width;
    const travel = bounds.height - renderedHeight;
    if (Math.abs(travel) <= .5) return;
    drag.current = { pointer: event.pointerId, startY: event.clientY, y: value.coverPositionY, travel };
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
  }
  function moveDrag(event: PointerEvent<HTMLDivElement>) {
    const current = drag.current; if (disabled || busy || !current || current.pointer !== event.pointerId) return;
    onChange({ ...value, coverPositionY: clamp(current.y + (event.clientY - current.startY) * 100 / current.travel) });
  }
  return <fieldset className="course-cover-editor" disabled={disabled}><legend>课程封面 <span>可选</span></legend>
    <p className="cover-hint">JPG/PNG · 最大 8 MiB · 宽高不超过 4096 · 按右半边宽度等比显示</p>
    <div className="cover-upload-actions"><label className="cover-file-label" htmlFor={`${id}-file`}>{value.coverAssetId ? '替换图片' : '选择图片'}
      <input id={`${id}-file`} type="file" aria-label="上传课程封面" accept="image/png,image/jpeg,.png,.jpg,.jpeg" disabled={disabled || busy || retry}
        onChange={(event) => { const file = event.target.files?.[0]; event.target.value = ''; if (file) void upload(file); }} /></label>
      {value.coverAssetId && <button type="button" disabled={busy || retry} onClick={() => { onChange(emptyCover()); setError(''); setStatus('移除封面将在保存课程后生效。'); }}>移除封面</button>}
      {retry && <><button type="button" disabled={busy} onClick={() => void upload()}>{pending.current?.uploaded ? '查询上传状态' : '重试上传'}</button>
        <button type="button" disabled={busy} onClick={() => { pending.current = null; onPendingChange(false); setRetry(false); setStatus(''); setError(''); }}>放弃这次上传</button></>}
    </div>
    {status && <p className="cover-status" role="status">{status}</p>}{busy && <progress max="100" value={progress} aria-label="封面上传进度" />}
    {error && <p className="cover-error" role="alert">{error}</p>}
    {value.cover && <><p className="cover-drag-hint">上下拖动右侧图片调整构图 · 两种预览共用垂直位置</p><div className="cover-previews">
      {(['desktop', 'mobile'] as const).map((mode) => <div className={`cover-preview-wrap ${mode}`} key={mode}><span>{mode === 'desktop' ? '桌面卡片' : '手机卡片'}</span>
        <div className={`cover-preview ${mode}`}>
          <div className="cover-preview-photo" aria-label={mode === 'desktop' ? '桌面构图预览' : '手机构图预览'}
            onPointerDown={startDrag} onPointerMove={moveDrag} onPointerUp={() => { drag.current = null; }} onPointerCancel={() => { drag.current = null; }}>
            {previewUnavailable !== value.coverAssetId && <img src={value.cover!.url} alt="课程封面预览" draggable={false} style={{ top: `${value.coverPositionY}%`, transform: `translateY(-${value.coverPositionY}%)` }}
              onError={() => setPreviewUnavailable(value.coverAssetId)}
              onLoad={(event) => { dimensions.current = { width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight }; }} />}
          </div>
          <CourseSummary course={{ title: course?.title || '课程名称', description: course?.description || '两小时一对一教学', priceAmount: course?.priceAmount || '150' }} />
        </div></div>)}
    </div><div className="cover-position-controls">
      <label htmlFor={`${id}-y`}>垂直位置 <span className="cover-position-value" aria-hidden="true">{value.coverPositionY}%</span><input id={`${id}-y`} aria-label="垂直位置" aria-valuetext={`${value.coverPositionY}%`} type="range" min="0" max="100" step="0.01" value={value.coverPositionY} disabled={busy}
        onChange={(event) => onChange({ ...value, coverPositionY: Number(event.target.value) })} /></label>
      <button type="button" disabled={busy} onClick={() => onChange({ ...value, coverPositionX: 50, coverPositionY: 50 })}>重置居中</button>
    </div><p className="cover-hint">只上下调整，原图完整保留；图片较扁时会露出浅色底。</p></>}
    {value.coverAssetId && !value.cover && <p className="cover-hint">已保存的封面暂不可预览。可保留、替换或移除后保存。</p>}
    {value.coverAssetId && (!value.cover || previewUnavailable === value.coverAssetId) && <div className="cover-upload-actions">
      <p className="cover-error" role="status">封面预览加载失败，当前封面和构图仍会保留。</p>
      <button type="button" disabled={busy} onClick={() => void refreshPreview()}>刷新封面预览</button>
    </div>}
  </fieldset>;
}
