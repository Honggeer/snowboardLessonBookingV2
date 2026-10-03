import { useEffect, useRef, useState } from 'react';
import { CoachPresentation } from './AboutGeerPage';
import { ProfileApiError, profileRequest, requestKey, type CoachProfile, type Csrf, type MediaLink, type MediaPurpose, type Upload } from './coachProfileApi';
import './about-geer.css';

const fields = [
  ['displayName', '称呼', 40], ['tagline', '一句话介绍', 160], ['bio', '个人简介', 3000], ['philosophy', '教学理念', 1000],
  ['specialties', '擅长方向', 200], ['languages', '授课语言', 80], ['region', '服务区域', 160], ['casiLevel', 'CASI 认证说明', 80],
  ['xhsAccount', '小红书账号', 80], ['xhsUrl', '小红书主页链接', 2048], ['douyinAccount', '抖音账号', 80],
  ['douyinUrl', '抖音主页链接', 2048], ['wechatId', '微信号', 64],
] as const;
const slots: Array<{ purpose: MediaPurpose; field: string; label: string }> = [
  { purpose: 'HERO', field: 'heroId', label: '人物照片' }, { purpose: 'CERTIFICATE', field: 'certificateId', label: 'CASI 证书' },
  { purpose: 'WECHAT_QR', field: 'wechatQrId', label: '微信二维码' }, { purpose: 'VIDEO_POSTER', field: 'posterId', label: '视频封面' },
  { purpose: 'HIGHLIGHT_VIDEO', field: 'videoId', label: '高光滑行视频' },
];
type Pending = { file: File; key: string; assetId?: string; uploaded?: boolean };
type Progress = { purpose: MediaPurpose; label: string; value: number; status: string };
export default function CoachProfileEditor({ refreshCsrf, onUnauthorized, onBack }:
  { refreshCsrf: () => Promise<Csrf>; onUnauthorized: () => void; onBack: () => void }) {
  const [profile, setProfile] = useState<CoachProfile | null>(null), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(true), [dirty, setDirty] = useState(false);
  const [preview, setPreview] = useState(false), [previewed, setPreviewed] = useState(false), [progress, setProgress] = useState<Progress | null>(null);
  const [pendingPurpose, setPendingPurpose] = useState<MediaPurpose | null>(null);
  const pending = useRef<Partial<Record<MediaPurpose, Pending>>>({}), publishKey = useRef<{ version: number; key: string } | null>(null);
  const mounted = useRef(false), xhr = useRef<XMLHttpRequest | null>(null), timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    mounted.current = true; void load();
    return () => { mounted.current = false; xhr.current?.abort(); if (timer.current) clearTimeout(timer.current); pending.current = {}; };
  // Private state is scoped to this mounted coach workspace.
  }, []);
  function fail(e: unknown) {
    if (!mounted.current) return;
    if (e instanceof ProfileApiError && e.status === 401) onUnauthorized();
    else setError(e instanceof Error ? e.message : '操作未完成，请重试。');
  }
  async function load() {
    setLoading(true); setError('');
    try { const p = await profileRequest<CoachProfile>('/api/coach/profile/draft'); if (mounted.current) { setProfile(p); setDirty(false); setPreviewed(false); } }
    catch (e) { fail(e); } finally { if (mounted.current) setLoading(false); }
  }
  async function write<T>(path: string, body: object, method: 'POST' | 'PUT', key?: string) {
    const csrf = await refreshCsrf();
    return profileRequest<T>(path, { method, headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token,
      ...(key ? { 'Idempotency-Key': key } : {}) }, body: JSON.stringify(body) });
  }
  function change(field: string, value: string) {
    setProfile((p) => p ? { ...p, content: { ...p.content, [field]: value } } : p);
    setDirty(true); setPreviewed(false); setNotice('');
  }
  async function save() {
    if (!profile || busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const saved = await write<CoachProfile>('/api/coach/profile/draft', { expectedVersion: profile.version, content: profile.content }, 'PUT');
      if (!mounted.current) return;
      setProfile({ ...saved, media: profile.media }); setDirty(false); setPreviewed(false); publishKey.current = null;
      setNotice('草稿已保存，公开页面保持当前发布版本。');
    } catch (e) { fail(e); } finally { if (mounted.current) setBusy(false); }
  }
  async function publish() {
    if (!profile || busy || dirty || !previewed) return;
    setBusy(true); setError(''); setNotice('');
    if (publishKey.current?.version !== profile.version) publishKey.current = { version: profile.version, key: requestKey() };
    try {
      const result = await write<{ version: number }>('/api/coach/profile/publish', { draftVersion: profile.version }, 'POST', publishKey.current.key);
      if (mounted.current) { setProfile({ ...profile, published: true, publishedVersion: result.version }); setNotice('已发布，访客现在可以看到这一版主页。'); }
    } catch (e) { fail(e); } finally { if (mounted.current) setBusy(false); }
  }
  function directPut(url: string, file: File, headers: Record<string, string>, purpose: MediaPurpose, label: string) {
    return new Promise<void>((resolve, reject) => {
      const request = new XMLHttpRequest(); xhr.current = request; request.open('PUT', url); request.timeout = 120000;
      Object.entries(headers).forEach(([key, value]) => request.setRequestHeader(key, value));
      request.upload.onprogress = (e) => { if (mounted.current && e.lengthComputable) setProgress({ purpose, label, value: Math.round(e.loaded / e.total * 100), status: '正在上传' }); };
      request.onload = () => { xhr.current = null; if (request.status >= 200 && request.status < 300) resolve(); else reject(new Error('文件上传未完成，请重试。')); };
      request.onerror = request.ontimeout = () => { xhr.current = null; reject(new Error('文件上传连接失败，请重试。')); };
      request.onabort = () => reject(new Error('上传已停止。')); request.send(file);
    });
  }
  async function finishUpload(u: Upload, slot: typeof slots[number]) {
    if (u.status === 'READY' && u.preview) {
      const link: MediaLink = u.preview;
      setProfile((p) => p ? { ...p, content: { ...p.content, [slot.field]: u.id }, media: { ...p.media, [slot.purpose]: link } } : p);
      setDirty(true); setPreviewed(false); setNotice(`${slot.label}已验证，请保存草稿后预览发布。`);
      delete pending.current[slot.purpose]; setPendingPurpose(null); setProgress(null); return true;
    }
    if (['REJECTED', 'FAILED', 'EXPIRED', 'DELETING', 'DELETED'].includes(u.status)) {
      delete pending.current[slot.purpose]; setPendingPurpose(null); setProgress(null);
      throw new Error(u.status === 'REJECTED' ? '文件格式或内容不符合要求，请重新选择文件。' : '这次上传未完成，请重新选择文件。');
    }
    return false;
  }
  async function checkUpload(id: string, slot: typeof slots[number]) {
    for (let count = 0; count < 60 && mounted.current; count++) {
      const u = await profileRequest<Upload>(`/api/coach/media/uploads/${encodeURIComponent(id)}`);
      if (!mounted.current) return;
      if (await finishUpload(u, slot)) return;
      setProgress({ purpose: slot.purpose, label: slot.label, value: 100, status: '正在验证文件' });
      await new Promise<void>((resolve) => { timer.current = setTimeout(resolve, 2000); });
    }
    if (mounted.current) setNotice('文件仍在后台验证，可以稍后点击“查询上传状态”。');
  }
  async function upload(slot: typeof slots[number], file?: File) {
    if (busy) return;
    const current = file ? { file, key: requestKey() } : pending.current[slot.purpose];
    if (!current) return;
    const max = slot.purpose === 'HIGHLIGHT_VIDEO' ? 100 * 1024 * 1024 : 8 * 1024 * 1024;
    const type = current.file.type || (/\.mp4$/i.test(current.file.name) ? 'video/mp4' : /\.png$/i.test(current.file.name) ? 'image/png' : /\.jpe?g$/i.test(current.file.name) ? 'image/jpeg' : '');
    if (current.file.size > max || current.file.size <= 0) { setError(`${slot.label}超过大小上限或文件为空。`); return; }
    pending.current[slot.purpose] = current; setPendingPurpose(slot.purpose); setBusy(true); setError(''); setNotice('');
    try {
      if (!current.uploaded) {
        const ticket = await write<Upload>('/api/coach/media/uploads', { purpose: slot.purpose, contentType: type, size: current.file.size }, 'POST', current.key);
        if (!mounted.current) return;
        current.assetId = ticket.id;
        if (ticket.status === 'UPLOADING') {
          if (!ticket.uploadUrl) throw new Error('上传授权已过期，请重新选择文件。');
          setProgress({ purpose: slot.purpose, label: slot.label, value: 0, status: '正在上传' });
          await directPut(ticket.uploadUrl, current.file, ticket.headers, slot.purpose, slot.label);
        }
        current.uploaded = true;
      }
      if (!mounted.current || !current.assetId) return;
      const u = await write<Upload>(`/api/coach/media/uploads/${encodeURIComponent(current.assetId)}/complete`, {}, 'POST');
      if (!mounted.current) return;
      if (!await finishUpload(u, slot)) await checkUpload(current.assetId, slot);
    } catch (e) { fail(e); } finally { if (mounted.current) setBusy(false); }
  }
  return <main className="geer-editor"><header className="geer-header"><div className="geer-shell geer-header-inner"><span className="geer-wordmark">GEER</span><strong>个人主页</strong><button type="button" className="geer-login" onClick={onBack}>返回工作区</button></div></header>
    <div className="geer-shell geer-editor-content"><div className="geer-editor-heading"><div><p className="geer-eyebrow">YOUR STORY</p><h1>让大家认识你</h1><p>编辑真实资料，预览后再发布到“关于 GEER”。</p></div><a href="/about-geer" target="_blank" rel="noopener noreferrer">查看公开页面 ↗</a></div>
      {error && <div className="geer-editor-alert" role="alert">{error}<button type="button" disabled={busy} onClick={() => void load()}>重新载入草稿</button></div>}
      {notice && <p className="geer-editor-notice" role="status">{notice}</p>}
      {loading && <p role="status">正在加载草稿…</p>}
      {profile && <>
        <div className="geer-editor-fields"><section className="geer-card"><h2>个人资料与联系方式</h2><div className="geer-field-grid">{fields.map(([field, label, max]) => <label key={field} className={field === 'bio' || field === 'philosophy' ? 'wide' : ''} htmlFor={'profile-' + field}>{label}
          {field === 'bio' || field === 'philosophy' ? <textarea id={'profile-' + field} rows={4} value={profile.content[field] ?? ''} maxLength={max} disabled={busy} onChange={(e) => change(field, e.target.value)} />
            : <input id={'profile-' + field} type={field.endsWith('Url') ? 'url' : 'text'} value={profile.content[field] ?? ''} maxLength={max} disabled={busy} onChange={(e) => change(field, e.target.value)} />}
        </label>)}</div><p className="geer-editor-hint">证书、社交账号和教学经历请填写真实内容。未配置的可选内容在公开页隐藏。</p></section>
        <section className="geer-card"><h2>图片与高光视频</h2><p className="geer-editor-hint">图片：JPG/PNG，8 MiB 内，最高 4096 × 4096。视频：MP4/H.264，100 MiB 内，最长 120 秒，最高 1080p；音轨可用 AAC。</p><div className="geer-upload-grid">{slots.map((slot) => <div className="geer-upload-slot" key={slot.purpose}><div className="geer-upload-heading"><strong>{slot.label}</strong>{profile.content[slot.field] && <button type="button" disabled={busy} onClick={() => {
          change(slot.field, ''); setProfile((p) => { if (!p) return p; const media = { ...p.media }; delete media[slot.purpose]; return { ...p, media }; });
        }}>移除</button>}</div>
        {profile.media[slot.purpose] && slot.purpose !== 'HIGHLIGHT_VIDEO' && <img src={profile.media[slot.purpose]?.url} alt={slot.label + '预览'} />}
        {profile.content[slot.field] && <span className="geer-upload-ready">已验证，可用于草稿</span>}
        <label className="geer-file-label">{profile.content[slot.field] ? '替换文件' : '选择文件'}<input type="file" aria-label={`上传${slot.label}`} accept={slot.purpose === 'HIGHLIGHT_VIDEO' ? 'video/mp4,.mp4' : 'image/png,image/jpeg,.png,.jpg,.jpeg'} disabled={busy || (!!pendingPurpose && pendingPurpose !== slot.purpose)} onChange={(e) => { const selected = e.target.files?.[0]; e.target.value = ''; if (selected) void upload(slot, selected); }} /></label>
        {pendingPurpose === slot.purpose && <button type="button" disabled={busy} onClick={() => void upload(slot)}>{pending.current[slot.purpose]?.uploaded ? '查询上传状态' : '重试上传'}</button>}
        </div>)}</div>{progress && <div className="geer-upload-progress" role="status"><span>{progress.label} · {progress.status} {progress.value}%</span><progress max="100" value={progress.value} /></div>}</section></div>
        <div className="geer-editor-toolbar"><div><strong>{dirty ? '有尚未保存的修改' : `草稿版本 ${profile.version}`}</strong><span>{profile.publishedVersion ? `当前已发布版本 ${profile.publishedVersion}` : '尚未发布'}</span></div><button type="button" className="geer-button outline" disabled={busy} onClick={save}>保存草稿</button><button type="button" className="geer-button outline" disabled={busy} onClick={() => { setPreview((p) => !p); if (!dirty) setPreviewed(true); }}>{preview ? '收起预览' : '预览页面'}</button><button type="button" className="geer-button" disabled={busy || dirty || !previewed} onClick={publish}>发布主页</button></div>
        {!previewed && <p className="geer-editor-hint">保存最新草稿并预览后，即可发布。发布的资料和联系方式无需登录也可查看。</p>}
      </>}
    </div>{profile && preview && <div className="geer-about-page geer-editor-preview"><CoachPresentation key={profile.version} profile={profile} preview onBook={() => setNotice('这是草稿预览；公开页的按钮会进入现有约课流程。')} /></div>}
  </main>;
}
