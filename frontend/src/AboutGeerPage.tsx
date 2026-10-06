import { useEffect, useRef, useState, type ReactNode } from 'react';
import { profileRequest, socialLinkTarget, type CoachProfile } from './coachProfileApi';
import './about-geer.css';
import GeerLogo from './GeerLogo';

function Dialog({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    panel.current?.querySelector<HTMLElement>('button')?.focus();
    return () => before?.focus();
  }, []);
  return <div className="geer-modal-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
    <div className="geer-modal" role="dialog" aria-modal="true" aria-label={title} ref={panel}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose();
        if (e.key === 'Tab') {
          const focusable = Array.from(panel.current?.querySelectorAll<HTMLElement>('button, a, input, [tabindex="0"]') ?? []);
          const first = focusable[0], last = focusable.at(-1);
          if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
          else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
        }
      }}>
      <div className="geer-modal-heading"><h2>{title}</h2><button type="button" aria-label="关闭" onClick={onClose}>×</button></div>
      {children}
    </div>
  </div>;
}
function Photo({ src, alt, className }: { src: string; alt: string; className?: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <div className={'geer-image-fallback ' + (className ?? '')} role="img" aria-label={alt + '（图片暂不可用）'}>图片暂不可用</div>
    : <img src={src} alt={alt} className={className} onError={() => setFailed(true)} loading={className === 'geer-hero-photo' ? 'eager' : 'lazy'} />;
}
export function CoachPresentation({ profile, preview = false, onBook, onRefresh }:
  { profile: CoachProfile; preview?: boolean; onBook: () => void; onRefresh?: () => void }) {
  const c = profile.content ?? {}, m = profile.media ?? {};
  const [dialog, setDialog] = useState<'certificate' | 'wechat' | null>(null);
  const [playing, setPlaying] = useState(false), [videoError, setVideoError] = useState(false);
  const video = useRef<HTMLVideoElement>(null);
  const xhs = socialLinkTarget(c.xhsUrl), douyin = socialLinkTarget(c.douyinUrl);
  const showXhs = !!(c.xhsAccount?.trim() || c.xhsUrl?.trim()), showDouyin = !!(c.douyinAccount?.trim() || c.douyinUrl?.trim());
  const contacts = !!(showXhs || showDouyin || m.WECHAT_QR), certificate = !!(m.CERTIFICATE && c.casiLevel);
  return <>
    {preview && <p className="geer-preview-banner" role="status">草稿预览 · 只有发布后，访客才能看到这些修改</p>}
    <section className="geer-hero">
      {(m.HERO || preview) && <Photo key={m.HERO?.url ?? 'placeholder'} className="geer-hero-photo"
        src={m.HERO?.url ?? '/images/geer-about-photo.png'} alt="GEER 在雪道上的滑行" />}
      <div className="geer-hero-wedge" />
      <div className="geer-shell geer-hero-content">
        <p className="geer-eyebrow">ABOUT GEER</p>
        <h1>你好，我是 {c.displayName || 'GEER'}。</h1>
        <h2>单板滑雪教练</h2>
        <p className="geer-tagline">{c.tagline || (preview ? '填写你的一句话介绍。' : '')}</p>
        <div className="geer-hero-actions"><button className="geer-button" type="button" onClick={onBook}>预约课程 <span aria-hidden="true">→</span></button>
          {(contacts || preview) && <a className="geer-button outline" href="#geer-contact">联系我</a>}</div>
      </div>
    </section>
    <div className="geer-shell geer-page-content">
      {(m.HIGHLIGHT_VIDEO && m.VIDEO_POSTER || preview) && <section className="geer-film" aria-label="高光滑行">
        {!playing && <Photo key={m.VIDEO_POSTER?.url ?? 'poster'} className="geer-film-poster" src={m.VIDEO_POSTER?.url ?? '/images/geer-about-photo.png'} alt="高光滑行视频封面" />}
        <div className="geer-film-title"><p>HIGHLIGHT FILM</p><h2>高光滑行</h2></div>
        {m.HIGHLIGHT_VIDEO && m.VIDEO_POSTER ? playing ? <video ref={video} className="geer-video" controls playsInline preload="none" autoPlay
          src={m.HIGHLIGHT_VIDEO.url} poster={m.VIDEO_POSTER.url} onError={() => setVideoError(true)} aria-label="GEER 高光滑行视频" />
          : <button className="geer-play" type="button" aria-label="播放高光滑行视频" onClick={() => { setVideoError(false); setPlaying(true); }}><span aria-hidden="true">▶</span></button>
          : <p className="geer-film-placeholder">上传一个高光视频和封面，让大家看到你的滑行。</p>}
        {videoError && <div className="geer-video-error" role="alert"><p>视频暂时无法播放，请重试。</p><button type="button" onClick={() => {
          setVideoError(false); setPlaying(false); if (onRefresh) onRefresh(); else video.current?.load();
        }}>重新加载视频</button></div>}
      </section>}
      {(c.bio || c.philosophy || c.specialties || c.languages || c.region || certificate || preview) && <div className={'geer-profile-grid ' + (certificate || preview ? '' : 'single')}>
        <section className="geer-card geer-teaching"><h2>关于我的教学</h2>
          <p className="geer-paragraph">{c.bio || (preview ? '填写你的滑雪经历、教学理念与擅长方向。' : '')}</p>
          {c.philosophy && <p className="geer-paragraph">{c.philosophy}</p>}
          {c.specialties && <p className="geer-paragraph"><strong>擅长方向</strong> · {c.specialties}</p>}
          {(c.languages || preview) && <div className="geer-detail"><span aria-hidden="true">◎</span><strong>授课语言</strong><span>·</span><span>{c.languages || '待填写'}</span></div>}
          {(c.region || preview) && <div className="geer-detail"><span aria-hidden="true">⌖</span><strong>服务区域</strong><span>·</span><span>{c.region || '待填写'}</span></div>}
        </section>
        {(certificate || preview) && <section className="geer-card"><h2>CASI 资质与认证</h2><div className="geer-certificate-row">
          {m.CERTIFICATE ? <button className="geer-certificate-image" type="button" aria-label="放大 CASI 证书" onClick={() => setDialog('certificate')}><Photo key={m.CERTIFICATE.url} src={m.CERTIFICATE.url} alt="GEER 的 CASI 证书" /></button>
            : <div className="geer-certificate-placeholder"><strong>CASI</strong><span>证书照片待上传</span></div>}
          <div><p><strong>认证等级</strong> · {c.casiLevel || '待填写'}</p>{m.CERTIFICATE && <button className="geer-text-button" type="button" onClick={() => setDialog('certificate')}>查看证书 ↗</button>}</div>
        </div></section>}
      </div>}
      {(contacts || preview) && <section className="geer-contact" id="geer-contact"><h2>在这里找到我</h2><div className="geer-social-grid">
        {(showXhs || preview) && <div className="geer-social-card"><span className="geer-social-icon xhs" aria-hidden="true">小红书</span><div><strong>小红书</strong>{c.xhsAccount && <span>{c.xhsAccount}</span>}{c.xhsUrl && !xhs && <span>{c.xhsUrl}</span>}{!showXhs && preview && <span>联系方式可选</span>}</div>{xhs && <a href={xhs} target="_blank" rel="noopener noreferrer" aria-label="小红书 · 查看主页">查看主页 ↗</a>}</div>}
        {(showDouyin || preview) && <div className="geer-social-card"><span className="geer-social-icon douyin" aria-hidden="true">♪</span><div><strong>抖音</strong>{c.douyinAccount && <span>{c.douyinAccount}</span>}{c.douyinUrl && !douyin && <span>{c.douyinUrl}</span>}{!showDouyin && preview && <span>联系方式可选</span>}</div>{douyin && <a href={douyin} target="_blank" rel="noopener noreferrer" aria-label="抖音 · 查看主页">查看主页 ↗</a>}</div>}
        {(m.WECHAT_QR || preview) && <div className="geer-social-card geer-wechat-card"><span className="geer-social-icon wechat" aria-hidden="true">●●</span><div><strong>微信</strong>{!m.WECHAT_QR && preview && <span>二维码待上传</span>}</div>{m.WECHAT_QR && <button type="button" className="geer-qr-thumbnail" aria-label="放大微信二维码" onClick={() => setDialog('wechat')}><Photo key={m.WECHAT_QR.url} src={m.WECHAT_QR.url} alt="GEER 微信二维码" /></button>}</div>}
      </div></section>}
      <section className="geer-book-banner"><h2>一起开启下一次滑行</h2><button className="geer-button" type="button" onClick={onBook}>预约课程 <span aria-hidden="true">→</span></button></section>
      <footer className="geer-footer"><span className="geer-wordmark"><GeerLogo /></span><span>MORE THAN A RIDE</span><span className="geer-footer-mountains" aria-hidden="true">／╲／╲／╲</span></footer>
    </div>
    {dialog === 'certificate' && m.CERTIFICATE && <Dialog title="CASI 证书" onClose={() => setDialog(null)}><Photo key={m.CERTIFICATE.url} className="geer-dialog-image" src={m.CERTIFICATE.url} alt="CASI 证书大图" /><p>{c.casiLevel}</p></Dialog>}
    {dialog === 'wechat' && m.WECHAT_QR && <Dialog title="联系 GEER" onClose={() => setDialog(null)}><Photo key={m.WECHAT_QR.url} className="geer-qr-image" src={m.WECHAT_QR.url} alt="GEER 微信二维码" /></Dialog>}
  </>;
}
export default function AboutGeerPage({ onBook, onHome, loggedIn }:
  { onBook: () => void; onHome: () => void; loggedIn: boolean }) {
  const [profile, setProfile] = useState<CoachProfile | null>(null), [error, setError] = useState(''), [reload, setReload] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    profileRequest<CoachProfile>('/api/coach-profile', { signal: abort.signal }).then((p) => { if (!abort.signal.aborted) { setProfile(p); setError(''); } })
      .catch((e) => { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : '主页加载失败'); });
    return () => abort.abort();
  }, [reload]);
  return <main className="geer-about-page"><header className="geer-header"><div className="geer-shell geer-header-inner">
    <button className="geer-wordmark" type="button" aria-label="GEER 首页" onClick={onHome}><GeerLogo /></button><nav aria-label="主导航"><span className="active">关于 GEER</span><button type="button" onClick={onBook}>预约课程</button></nav>
    <button className="geer-login" type="button" onClick={onHome}>{loggedIn ? '返回工作区' : '登录'}</button>
  </div></header>
  {error && <div className="geer-shell geer-page-state" role="alert"><p>{error}</p><button className="geer-button" type="button" onClick={() => setReload((n) => n + 1)}>重新加载</button></div>}
  {!profile && !error && <div className="geer-page-state" role="status">正在加载关于 GEER…</div>}
  {profile?.published ? <CoachPresentation key={profile.version + ':' + reload} profile={profile} onBook={onBook} onRefresh={() => setReload((n) => n + 1)} />
    : profile && !error && <div className="geer-page-state"><p className="geer-eyebrow">ABOUT GEER</p><h1>关于 GEER</h1><p>教练正在准备个人主页，敬请期待。</p><button className="geer-button" type="button" onClick={onBook}>预约课程 →</button></div>}
  </main>;
}
