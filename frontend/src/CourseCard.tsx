import { useEffect, useRef, useState } from 'react';
import type { MediaLink } from './coachProfileApi';

export type Course = { id: string; title: string; description: string; priceAmount: string; currency: string; active?: boolean;
  coverAssetId?: string | null; coverPositionX?: number; coverPositionY?: number; cover?: MediaLink | null };
export function CourseSummary({ course, selected = false }: {
  course: Pick<Course, 'title' | 'description' | 'priceAmount'>; selected?: boolean;
}) {
  return <span className="course-info">
    <span className="course-copy"><strong>{course.title}</strong><small>{course.description || '两小时一对一教学'}</small><em>2 小时 · 一对一</em></span>
    <span className="course-price"><b>CAD {course.priceAmount}</b><span className={`course-selected${selected ? ' is-selected' : ''}`} aria-hidden="true">✓ 已选</span></span>
  </span>;
}
export default function CourseCard({ course, selected, onSelect, refreshCover }: {
  course: Course; selected: boolean; onSelect: () => void; refreshCover: () => Promise<MediaLink | null>;
}) {
  const [revealed, setRevealed] = useState(false), [failed, setFailed] = useState(false);
  const [replacement, setReplacement] = useState<MediaLink | null>(null), [imageVersion, setImageVersion] = useState(0);
  const attempted = useRef(false), mounted = useRef(true);
  const [direct, setDirect] = useState(() => typeof matchMedia === 'function'
    && matchMedia('(hover: none), (pointer: coarse), (max-width: 600px)').matches);
  useEffect(() => {
    mounted.current = true;
    if (typeof matchMedia !== 'function') return () => { mounted.current = false; };
    const query = matchMedia('(hover: none), (pointer: coarse), (max-width: 600px)');
    const update = () => setDirect(query.matches);
    query.addEventListener('change', update);
    return () => { mounted.current = false; query.removeEventListener('change', update); };
  }, []);
  const link = replacement ?? course.cover;
  async function imageError() {
    if (attempted.current) { setFailed(true); return; }
    attempted.current = true;
    try {
      const fresh = await refreshCover();
      if (!mounted.current) return;
      if (!fresh) setFailed(true);
      else { setReplacement(fresh); setImageVersion(1); }
    } catch { if (mounted.current) setFailed(true); }
  }
  return <button type="button"
    className={`course-option ${selected ? 'selected' : ''} ${link ? 'has-cover' : 'no-cover'}`}
    aria-pressed={selected} onClick={onSelect} onMouseEnter={() => setRevealed(true)} onFocus={() => setRevealed(true)}>
    {link && <span className={`course-photo ${failed ? 'failed' : ''}`} aria-hidden="true">
      {!failed && (selected || revealed || direct) && <img key={imageVersion} src={link.url} alt="" loading="lazy" decoding="async" onError={() => void imageError()}
        style={{ top: `${course.coverPositionY ?? 50}%`, transform: `translateY(-${course.coverPositionY ?? 50}%)` }} />}
    </span>}
    <CourseSummary course={course} selected={selected} />
  </button>;
}
