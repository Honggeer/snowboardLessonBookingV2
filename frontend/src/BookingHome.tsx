import { useEffect, useState, type FormEvent } from 'react';
import './booking.css';
import CourseCard, { type Course } from './CourseCard';
import CourseCoverEditor, { emptyCover, type CoverDraft } from './CourseCoverEditor';
import type { MediaLink } from './coachProfileApi';
import GeerLogo from './GeerLogo';
import ContactPhoneInput from './ContactPhoneInput';

type Account = { id: string; role: 'STUDENT' | 'COACH'; name: string; level: string | null };
export type BookingDeepLink = { role: Account['role']; id: string };
type Csrf = { token: string; headerName: string };
type Page<T> = { items: T[]; nextCursor: string | null };
type Mountain = { id: string; name: string; active: boolean };
type Slot = { id: string; zoneId: string; localDate: string; startAt: string; endAt: string;
  status: string; availableMountains: Mountain[] };
type Booking = { id: string; slotId: string; studentName: string; status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED_BY_STUDENT';
  studentPhone?: string | null; decisionReason: string | null; courseTitle: string; priceAmount: string; currency: string;
  location: string; zoneId: string; localDate: string; startAt: string; endAt: string };
type StudentContact = { phone: string | null };
function normalizedPhone(input: string): string | null {
  if (input.length > 64) return null;
  const phone = input.replace(/[ ()-]/g, '');
  return /^\+[1-9][0-9]{6,14}$/.test(phone) ? phone : null;
}
const phoneValidationMessage = '请填写有效的联系电话。';
type Tab = 'book' | 'mine' | 'courses' | 'availability' | 'applications';
type DayInput = { localDate: string; startTime: string; endTime: string; mountainId: string };
type MonthDay = { localDate: string; limitedMountain: Mountain | null; lockedMountain: Mountain | null;
  legacyReviewRequired: boolean; slots: Slot[] };
type MonthSchedule = { zoneId: string; days: MonthDay[] };

class BookingApiError extends Error {
  constructor(readonly status: number, message: string) { super(message); }
}

async function responseBody(response: Response): Promise<unknown> {
  return response.json().catch(() => ({}));
}
async function get<T>(path: string): Promise<T> {
  const response = await fetch(path, { credentials: 'same-origin' });
  const body = await responseBody(response);
  if (!response.ok) throw new BookingApiError(response.status, (body as { detail?: string }).detail ?? '加载失败，请重试。');
  return body as T;
}
async function getPage<T>(path: string): Promise<Page<T>> {
  const page = await get<Page<T>>(path);
  if (!page || !Array.isArray(page.items)) throw new BookingApiError(502, '服务返回的列表格式异常，请重试。');
  return page;
}
function localTime(point: string, zoneId: string) {
  return new Intl.DateTimeFormat('zh-CN', { timeZone: zoneId, hour: '2-digit', minute: '2-digit', hour12: false })
    .format(new Date(point));
}
function slotTime(slot: Slot | Booking) { return `${localTime(slot.startAt, slot.zoneId)} – ${localTime(slot.endAt, slot.zoneId)}`; }
function readableDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return `${month}月${day}日 ${new Intl.DateTimeFormat('zh-CN', { weekday: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, day)))}`;
}
function dateOffset(from: string, days: number) {
  const date = new Date(`${from}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
function torontoDate() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Toronto', year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(new Date());
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
}
function torontoTime() {
  return new Intl.DateTimeFormat('en-GB', { timeZone: 'America/Toronto', hour: '2-digit',
    minute: '2-digit', hourCycle: 'h23' }).format(new Date());
}
const startingDate = torontoDate();
function shiftMonth(month: string, offset: number) {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0, 7);
}
function monthDates(month: string) {
  const [year, number] = month.split('-').map(Number);
  const count = new Date(Date.UTC(year, number, 0)).getUTCDate();
  return Array.from({ length: count }, (_, index) => `${month}-${String(index + 1).padStart(2, '0')}`);
}
function timeMinutes(value: string) { const [hours, minutes] = value.split(':').map(Number); return hours * 60 + minutes; }
function formatMinutes(value: number) { return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`; }
function newIdempotencyKey() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
function previewDays(days: DayInput[]) {
  let count = 0;
  const tails: string[] = [];
  for (const day of days) {
    const start = timeMinutes(day.startTime); const end = timeMinutes(day.endTime);
    if (!day.localDate || !Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue;
    const blocks = Math.floor((end - start) / 120);
    count += blocks;
    if (end - start - blocks * 120 > 0) tails.push(`${day.localDate} ${formatMinutes(start + blocks * 120)}–${day.endTime}`);
  }
  return { count, tails };
}
function statusLabel(status: Booking['status']) {
  return status === 'PENDING' ? '待教练确认' : status === 'CONFIRMED' ? '已确认'
    : status === 'CANCELLED_BY_STUDENT' ? '学员已取消' : '已拒绝';
}

export default function BookingHome({ account, csrf, refreshCsrf, onLogout, onUnauthorized, authMessage, deepLink, onAbout, onProfile }:
  { account: Account; csrf: Csrf | null; refreshCsrf: () => Promise<Csrf>; onLogout: () => Promise<void>;
    onUnauthorized: () => void; authMessage: string; deepLink: BookingDeepLink | null; onAbout: () => void; onProfile: () => void }) {
  const [tab, setTab] = useState<Tab>(deepLink?.role === account.role
    ? account.role === 'COACH' ? 'applications' : 'mine'
    : account.role === 'COACH' ? 'applications' : 'book');
  const [courses, setCourses] = useState<Course[]>([]);
  const [mountains, setMountains] = useState<Mountain[]>([]);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [targetBooking, setTargetBooking] = useState<Booking | null>(null);
  const [targetError, setTargetError] = useState('');
  const [courseCursor, setCourseCursor] = useState<string | null>(null);
  const [slotCursor, setSlotCursor] = useState<string | null>(null);
  const [bookingCursor, setBookingCursor] = useState<string | null>(null);
  const [mountainCursor, setMountainCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);
  const [selectedMountainId, setSelectedMountainId] = useState<string | null>(null);
  const [windowStart, setWindowStart] = useState(startingDate);
  const [loading, setLoading] = useState(true);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);
  const [courseTitle, setCourseTitle] = useState('');
  const [courseDescription, setCourseDescription] = useState('');
  const [coursePrice, setCoursePrice] = useState('');
  const [courseCover, setCourseCover] = useState<CoverDraft>(emptyCover);
  const [editCover, setEditCover] = useState<CoverDraft>(emptyCover);
  const [coverPending, setCoverPending] = useState(false);
  const [editCoverPending, setEditCoverPending] = useState(false);
  const [calendarMonth, setCalendarMonth] = useState(dateOffset(startingDate, 1).slice(0, 7));
  const [calendarDays, setCalendarDays] = useState<MonthDay[]>([]);
  const [calendarSelection, setCalendarSelection] = useState<string[]>([]);
  const [calendarDetail, setCalendarDetail] = useState<string | null>(null);
  const [calendarAction, setCalendarAction] = useState<{ message: string; error: boolean } | null>(null);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarError, setCalendarError] = useState('');
  const [slotStartTime, setSlotStartTime] = useState('10:00');
  const [slotEndTime, setSlotEndTime] = useState('12:00');
  const [slotMountainId, setSlotMountainId] = useState('');
  const [mountainName, setMountainName] = useState('');
  const [mountainFeedback, setMountainFeedback] = useState('');
  const [mountainWriteError, setMountainWriteError] = useState('');
  const [mountainEdits, setMountainEdits] = useState<Record<string, string>>({});
  const [submissionKey, setSubmissionKey] = useState<string | null>(null);
  const [courseKey, setCourseKey] = useState<string | null>(null);
  const [slotKey, setSlotKey] = useState<string | null>(null);
  const [mountainKey, setMountainKey] = useState<string | null>(null);
  const [mountainManagerOpen, setMountainManagerOpen] = useState(false);
  const [calendarFeedback, setCalendarFeedback] = useState('');
  const [calendarWriteError, setCalendarWriteError] = useState('');
  const [publishedDates, setPublishedDates] = useState<string[]>([]);
  const [bookingFeedback, setBookingFeedback] = useState('');
  const [bookingWriteError, setBookingWriteError] = useState('');
  const [editingCourseId, setEditingCourseId] = useState<string | null>(null);
  const [courseEdit, setCourseEdit] = useState({ title: '', description: '', priceAmount: '' });
  const [archiveCourseId, setArchiveCourseId] = useState<string | null>(null);
  const [courseFeedback, setCourseFeedback] = useState('');
  const [courseActionError, setCourseActionError] = useState('');
  const [rejectBookingId, setRejectBookingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectError, setRejectError] = useState('');
  const [cancelBookingId, setCancelBookingId] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelFeedback, setCancelFeedback] = useState('');
  const [cancelError, setCancelError] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [savedPhone, setSavedPhone] = useState<string | null>(null);
  const [contactReady, setContactReady] = useState(false);
  const [contactLoading, setContactLoading] = useState(true);
  const [contactReadError, setContactReadError] = useState('');
  const [contactReload, setContactReload] = useState(0);
  const [contactError, setContactError] = useState('');
  const [contactNotice, setContactNotice] = useState('');

  function fail(reason: unknown) {
    if (reason instanceof BookingApiError && reason.status === 401) { onUnauthorized(); return; }
    setError(reason instanceof Error ? reason.message : '操作未完成，请重试。');
  }

  async function write<T>(method: 'POST' | 'PATCH', path: string, body: object, key?: string): Promise<T> {
    const currentCsrf = csrf ?? await refreshCsrf();
    const headers: Record<string, string> = { 'Content-Type': 'application/json', [currentCsrf.headerName]: currentCsrf.token };
    if (key) headers['Idempotency-Key'] = key;
    const response = await fetch(path, { method, credentials: 'same-origin', headers, body: JSON.stringify(body) });
    const result = await responseBody(response);
    if (!response.ok) throw new BookingApiError(response.status,
      (result as { detail?: string }).detail ?? '操作未完成，请重试。');
    return result as T;
  }
  function post<T>(path: string, body: object, key?: string) { return write<T>('POST', path, body, key); }

  useEffect(() => {
    if (account.role !== 'STUDENT') return;
    let active = true;
    setContactReady(false); setContactLoading(true); setContactReadError('');
    setContactPhone(''); setSavedPhone(null); setContactError(''); setContactNotice('');
    get<StudentContact>('/api/student/contact').then((contact) => {
      if (!active) return;
      if (!contact || (contact.phone !== null && (typeof contact.phone !== 'string' || !normalizedPhone(contact.phone))))
        throw new BookingApiError(502, '联系电话加载结果异常，请重试。');
      setContactPhone(contact.phone ?? ''); setSavedPhone(contact.phone); setContactReady(true);
    }).catch((reason) => {
      if (!active) return;
      if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
      else setContactReadError(reason instanceof BookingApiError ? reason.message : '联系电话加载失败，请重试。');
    }).finally(() => { if (active) setContactLoading(false); });
    return () => { active = false; };
  }, [account.id, account.role, contactReload]);

  useEffect(() => {
    if (!deepLink) { setTargetBooking(null); setTargetError(''); return; }
    if (deepLink.role !== account.role) {
      setTargetBooking(null);
      setTargetError('此预约链接需要使用对应角色的账号登录。');
      return;
    }
    let active = true;
    setTab(account.role === 'COACH' ? 'applications' : 'mine');
    setTargetBooking(null);
    setTargetError('');
    const path = account.role === 'COACH' ? '/api/coach/bookings/' : '/api/bookings/';
    get<Booking>(path + encodeURIComponent(deepLink.id)).then((booking) => {
      if (active) setTargetBooking(booking);
    }).catch((reason) => {
      if (!active) return;
      if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
      else if (reason instanceof BookingApiError && (reason.status === 403 || reason.status === 404))
        setTargetError('找不到这笔预约，或当前账号无权查看。');
      else setTargetError(reason instanceof Error ? reason.message : '预约加载失败，请重试。');
    });
    return () => { active = false; };
  }, [account.id, account.role, deepLink?.id, deepLink?.role, reload]);

  useEffect(() => {
    if (!targetBooking) return;
    const timer = window.setTimeout(() => {
      document.getElementById('booking-' + targetBooking.id)?.scrollIntoView?.({ block: 'center' });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [targetBooking, tab]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    const coach = account.role === 'COACH';
    const requests: [Promise<Page<Course>>, Promise<Page<Booking>>, Promise<Page<Mountain> | undefined>] = [
      getPage<Course>(coach ? '/api/coach/courses?limit=50' : '/api/courses?limit=50'),
      getPage<Booking>(coach ? '/api/coach/bookings?limit=50' : '/api/bookings/mine?limit=50'),
      coach ? getPage<Mountain>('/api/coach/mountains?limit=50') : Promise.resolve(undefined),
    ];
    Promise.all(requests).then(([coursePage, bookingPage, mountainPage]) => {
      if (!active) return;
      setCourses(coursePage.items);
      setCourseCursor(coursePage.nextCursor);
      setBookings(bookingPage.items);
      setBookingCursor(bookingPage.nextCursor);
      if (coach && mountainPage) { setMountains(mountainPage.items); setMountainCursor(mountainPage.nextCursor); }
      if (!coach) setSelectedCourseId((current) => current && coursePage.items.some((item) => item.id === current)
        ? current : coursePage.items[0]?.id ?? null);
      setError('');
    }).catch((reason) => { if (active) fail(reason); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [account.id, account.role, reload]);

  useEffect(() => {
    if (account.role !== 'COACH') return;
    let active = true;
    setCalendarLoading(true); setCalendarError('');
    const [year, month] = calendarMonth.split('-').map(Number);
    get<MonthSchedule>(`/api/coach/availability/month?year=${year}&month=${month}`)
      .then((schedule) => { if (active) setCalendarDays(Array.isArray(schedule.days) ? schedule.days : []); })
      .catch((reason) => {
        if (!active) return;
        if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
        else setCalendarError(reason instanceof Error ? reason.message : '月历加载失败，请重试。');
      }).finally(() => { if (active) setCalendarLoading(false); });
    return () => { active = false; };
  }, [account.id, account.role, calendarMonth, reload]);

  useEffect(() => {
    if (account.role !== 'STUDENT' || !selectedCourseId) { setSlots([]); return; }
    let active = true;
    setSlotsLoading(true);
    const params = new URLSearchParams({ from: windowStart,
      to: dateOffset(windowStart, 30), limit: '50' });
    getPage<Slot>(`/api/slots?${params}`).then((page) => {
      if (!active) return;
      setSlots(page.items);
      setSlotCursor(page.nextCursor);
      setSelectedDate((current) => current && page.items.some((item) => item.localDate === current)
        ? current : page.items[0]?.localDate ?? null);
      setSelectedSlotId(null);
      setSelectedMountainId(null);
      setError('');
    }).catch((reason) => { if (active) fail(reason); })
      .finally(() => { if (active) setSlotsLoading(false); });
    return () => { active = false; };
  }, [account.id, account.role, selectedCourseId, windowStart, reload]);

  async function loadMore(kind: 'courses' | 'slots' | 'bookings' | 'mountains') {
    const cursor = kind === 'courses' ? courseCursor : kind === 'slots' ? slotCursor
      : kind === 'mountains' ? mountainCursor : bookingCursor;
    if (!cursor || loadingMore) return;
    setLoadingMore(true); setError('');
    try {
      const params = new URLSearchParams({ limit: '50', cursor });
      if (kind === 'courses') {
        const page = await getPage<Course>(`${account.role === 'COACH' ? '/api/coach/courses' : '/api/courses'}?${params}`);
        setCourses((current) => [...current, ...page.items]); setCourseCursor(page.nextCursor);
      } else if (kind === 'slots') {
        if (account.role !== 'STUDENT' || !selectedCourseId) return;
        params.set('from', windowStart);
        params.set('to', dateOffset(windowStart, 30));
        const page = await getPage<Slot>(`/api/slots?${params}`);
        setSlots((current) => [...current, ...page.items]); setSlotCursor(page.nextCursor);
        setSelectedDate((current) => current ?? page.items[0]?.localDate ?? null);
      } else if (kind === 'mountains') {
        const page = await getPage<Mountain>(`/api/coach/mountains?${params}`);
        setMountains((current) => [...current, ...page.items]); setMountainCursor(page.nextCursor);
      } else {
        const page = await getPage<Booking>(`${account.role === 'COACH' ? '/api/coach/bookings' : '/api/bookings/mine'}?${params}`);
        setBookings((current) => [...current, ...page.items]); setBookingCursor(page.nextCursor);
      }
    } catch (reason) { fail(reason); }
    finally { setLoadingMore(false); }
  }

  async function persistPhone(phone: string) {
    if (phone !== savedPhone) {
      const result = await write<StudentContact>('PATCH', '/api/student/contact', { phone });
      if (!result || typeof result.phone !== 'string' || normalizedPhone(result.phone) !== result.phone)
        throw new BookingApiError(502, '电话保存结果异常，请重试。');
      setSavedPhone(result.phone); setContactPhone(result.phone);
    } else setContactPhone(phone);
  }

  async function saveContact() {
    if (busy || !contactReady || contactLoading) return;
    const phone = normalizedPhone(contactPhone);
    if (!phone) { setContactError(phoneValidationMessage); setContactNotice(''); return; }
    setBusy(true); setContactError(''); setContactNotice('');
    try {
      await persistPhone(phone);
      setContactNotice('联系电话已保存，下次预约会自动填入。');
    } catch (reason) {
      setContactError(reason instanceof BookingApiError ? reason.message : '电话保存结果暂时无法确认，请重试。');
      if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
    } finally { setBusy(false); }
  }

  async function submitBooking() {
    if (!selectedCourseId || !selectedSlotId || !selectedMountainId || busy || !contactReady || contactLoading) return;
    const phone = normalizedPhone(contactPhone);
    if (!phone) { setContactError(phoneValidationMessage); setContactNotice(''); return; }
    setBusy(true); setError(''); setNotice(''); setContactError(''); setContactNotice('');
    setBookingWriteError(''); setBookingFeedback('提交中…');
    let applying = false;
    try {
      const key = submissionKey ?? newIdempotencyKey();
      setSubmissionKey(key);
      await persistPhone(phone);
      applying = true;
      const booking = await post<Booking>('/api/bookings',
        { courseId: selectedCourseId, slotId: selectedSlotId, mountainId: selectedMountainId }, key);
      const saved: Booking = { ...booking, slotId: booking.slotId ?? selectedSlotId,
        courseTitle: booking.courseTitle ?? selectedCourse?.title ?? '', priceAmount: booking.priceAmount ?? selectedCourse?.priceAmount ?? '',
        currency: booking.currency ?? 'CAD', location: booking.location ?? selectedMountain?.name ?? '',
        zoneId: booking.zoneId ?? selectedSlot?.zoneId ?? 'America/Toronto', localDate: booking.localDate ?? selectedSlot?.localDate ?? '',
        startAt: booking.startAt ?? selectedSlot?.startAt ?? '', endAt: booking.endAt ?? selectedSlot?.endAt ?? '' };
      setBookings((current) => [saved, ...current.filter((item) => item.id !== saved.id)]);
      setBookingFeedback(booking.status === 'PENDING'
        ? `申请已提交，待教练确认。预约编号：${booking.id}` : `申请状态：${statusLabel(booking.status)}。预约编号：${booking.id}`);
      setSubmissionKey(null);
      try {
        const mine = await getPage<Booking>('/api/bookings/mine?limit=50');
        setBookings([saved, ...mine.items.filter((item) => item.id !== saved.id)]);
        setBookingCursor(mine.nextCursor);
      } catch (reason) {
        if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
        else setBookingWriteError('申请已保存，但预约列表暂时无法刷新；可到“我的预约”稍后重试。');
      }
    } catch (reason) {
      setBookingFeedback('');
      if (!applying) setContactError(reason instanceof BookingApiError ? reason.message : '电话保存结果暂时无法确认，请重试。');
      else if (reason instanceof BookingApiError && reason.status >= 400 && reason.status < 500)
        setBookingWriteError(`联系电话已保存，预约申请未成功：${reason.message}`);
      else setBookingWriteError('联系电话已保存。预约提交结果暂时无法确认，请重试；重试不会重复创建预约。');
      if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
    }
    finally { setBusy(false); }
  }

  async function refreshCourseCover(id: string): Promise<MediaLink | null> {
    let cursor: string | null = null;
    const visited = new Set<string>();
    try {
      for (let count = 0; count < Math.max(1, Math.ceil(courses.length / 50)); count++) {
        const page: Page<Course> = await getPage<Course>(`/api/courses?limit=50${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`);
        const course = page.items.find((item) => item.id === id);
        if (course) return course.cover ?? null;
        cursor = page.nextCursor;
        if (!cursor || visited.has(cursor)) break;
        visited.add(cursor);
      }
    } catch (reason) { if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized(); }
    return null;
  }

  async function publishCourse(event: FormEvent) {
    event.preventDefault(); if (busy || coverPending) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const key = courseKey ?? newIdempotencyKey(); setCourseKey(key);
      await post<Course>('/api/coach/courses', { title: courseTitle, description: courseDescription,
        priceAmount: coursePrice, currency: 'CAD', coverAssetId: courseCover.coverAssetId,
        coverPositionX: courseCover.coverPositionX, coverPositionY: courseCover.coverPositionY }, key);
      setCourseKey(null);
      setCourseTitle(''); setCourseDescription(''); setCoursePrice(''); setCourseCover(emptyCover());
      setNotice('课程已发布。'); setReload((value) => value + 1);
    } catch (reason) { fail(reason); }
    finally { setBusy(false); }
  }

  async function publishSlot(event: FormEvent) {
    event.preventDefault(); if (busy) return;
    const days = calendarSelection.map((localDate) => ({ localDate, startTime: slotStartTime,
      endTime: slotEndTime, mountainId: slotMountainId }));
    const preview = previewDays(days);
    const today = torontoDate();
    const now = torontoTime();
    const invalidDate = days.some((day) => day.localDate < today
      || (day.localDate === today && timeMinutes(day.startTime) <= timeMinutes(now)));
    const validationError = mountains.every((mountain) => !mountain.active) ? '请先新增一座雪场。'
      : !days.length ? '请先在月历中选择可用日期。'
        : !slotStartTime || !slotEndTime || timeMinutes(slotEndTime) <= timeMinutes(slotStartTime)
          ? '请设置有效的开始和结束时间。'
          : preview.count === 0 ? '时间范围至少要包含一个完整的两小时时段。'
            : days.length > 31 || preview.count > 100 ? '每批最多选择 31 天、发布 100 个时段。'
              : invalidDate ? '所选日期的开始时间已过，请调整日期或时间。' : '';
    if (validationError) { setCalendarFeedback(''); setCalendarWriteError(validationError); return; }
    setBusy(true); setError(''); setNotice(''); setCalendarWriteError(''); setCalendarFeedback('正在发布可用时间…');
    try {
      const key = slotKey ?? newIdempotencyKey(); setSlotKey(key);
      const batch = await post<{ slots: Slot[]; tails: unknown[] }>('/api/coach/availability/replacements',
        { days: days.map((day) => ({ ...day, mountainId: day.mountainId || null })) }, key);
      setSlotKey(null);
      setCalendarSelection([]);
      setCalendarAction(null);
      setPublishedDates(days.map((day) => day.localDate));
      setCalendarMonth(days[0].localDate.slice(0, 7));
      setCalendarDetail(days[0].localDate);
      setCalendarFeedback(`已发布 ${batch.slots.length} 个可用时间：${days.map((day) => day.localDate).join('、')}。`);
      setReload((value) => value + 1);
    } catch (reason) {
      setCalendarFeedback('');
      setCalendarWriteError(reason instanceof Error ? reason.message : '发布失败，请重试。');
      if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
    }
    finally { setBusy(false); }
  }

  async function publishMountain(event: FormEvent) {
    event.preventDefault(); if (busy) return;
    const name = mountainName.trim();
    if (!name) { setMountainFeedback(''); setMountainWriteError('请输入雪场名称。'); return; }
    setBusy(true); setError(''); setNotice(''); setMountainWriteError(''); setMountainFeedback('正在新增雪场…');
    try {
      const key = mountainKey ?? newIdempotencyKey(); setMountainKey(key);
      const mountain = await post<Mountain>('/api/coach/mountains', { name }, key);
      setMountains((current) => [mountain, ...current.filter((item) => item.id !== mountain.id)]);
      setMountainName(''); setMountainKey(null); setMountainFeedback(`${mountain.name} 已加入雪场列表。`);
      setReload((value) => value + 1);
      setMountainManagerOpen(true);
    } catch (reason) {
      setMountainFeedback('');
      setMountainWriteError(reason instanceof Error ? reason.message : '新增雪场失败，请重试。');
      if (reason instanceof BookingApiError && reason.status === 401) onUnauthorized();
    }
    finally { setBusy(false); }
  }

  async function changeMountain(mountain: Mountain, action: 'rename' | 'deactivate') {
    if (busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      if (action === 'rename') await write<Mountain>('PATCH', `/api/coach/mountains/${mountain.id}`,
        { name: mountainEdits[mountain.id] ?? mountain.name });
      else await post<Mountain>(`/api/coach/mountains/${mountain.id}/deactivate`, {});
      setNotice(action === 'rename' ? '雪场名称已更新。' : '雪场已停用。');
      setReload((value) => value + 1);
    } catch (reason) { fail(reason); }
    finally { setBusy(false); }
  }

  async function decide(booking: Booking, action: 'confirm' | 'reject', reason = '') {
    if (busy) return;
    setBusy(true); setError(''); setNotice(''); setRejectError('');
    try {
      const result = await post<Booking>(`/api/coach/bookings/${booking.id}/${action}`,
        action === 'reject' ? { reason } : {});
      setBookings((current) => current.map((item) => item.id === result.id ? { ...item, ...result } : item));
      setTargetBooking((current) => current?.id === result.id ? { ...current, ...result } : current);
      setRejectBookingId(null); setRejectReason('');
      setNotice(action === 'confirm' ? '已确认该申请；当天锁定这座雪场，其他雪场的待确认申请已拒绝。' : '已拒绝该申请。');
      setReload((value) => value + 1);
    } catch (reason) {
      fail(reason);
      if (action === 'reject') setRejectError(reason instanceof Error ? reason.message : '拒绝失败，请重试。');
    }
    finally { setBusy(false); }
  }

  async function updateCourse(event: FormEvent) {
    event.preventDefault(); if (!editingCourseId || busy || editCoverPending) return;
    setBusy(true); setError(''); setCourseActionError(''); setCourseFeedback('正在保存课程…');
    try {
      const course = await write<Course>('PATCH', `/api/coach/courses/${editingCourseId}`,
        { ...courseEdit, currency: 'CAD', coverAssetId: editCover.coverAssetId,
          coverPositionX: editCover.coverPositionX, coverPositionY: editCover.coverPositionY });
      setCourses((current) => current.map((item) => item.id === course.id ? course : item));
      setEditingCourseId(null); setCourseFeedback('课程已更新，已有预约保持原课程与价格。');
    } catch (reason) {
      fail(reason); setCourseFeedback('');
      setCourseActionError(reason instanceof Error ? reason.message : '保存失败，请重试。');
    }
    finally { setBusy(false); }
  }

  async function archiveCourse(courseId: string) {
    if (busy) return;
    setBusy(true); setError(''); setCourseActionError(''); setCourseFeedback('正在移除课程…');
    try {
      const course = await post<Course>(`/api/coach/courses/${courseId}/archive`, {});
      setCourses((current) => current.map((item) => item.id === course.id ? course : item));
      setArchiveCourseId(null); setCourseFeedback('课程已从学员选课列表移除；已有预约不受影响。');
    } catch (reason) {
      fail(reason); setCourseFeedback('');
      setCourseActionError(reason instanceof Error ? reason.message : '删除失败，请重试。');
    }
    finally { setBusy(false); }
  }

  async function cancelMine(booking: Booking) {
    if (busy) return;
    setBusy(true); setError(''); setCancelError(''); setCancelFeedback('正在取消预约…');
    try {
      const result = await post<Booking>(`/api/bookings/${booking.id}/cancel`,
        { reason: booking.status === 'CONFIRMED' ? cancelReason : null });
      setBookings((current) => current.map((item) => item.id === result.id ? { ...item, ...result } : item));
      setTargetBooking((current) => current?.id === result.id ? { ...current, ...result } : current);
      setCancelBookingId(null); setCancelReason('');
      setCancelFeedback('预约已取消。若该时段仍可约，可以重新提交申请。');
      setSelectedSlotId(null); setSelectedMountainId(null);
      setReload((value) => value + 1);
    } catch (reason) {
      fail(reason); setCancelFeedback('');
      setCancelError(reason instanceof Error ? reason.message : '取消失败，请重试。');
    }
    finally { setBusy(false); }
  }

  const selectedCourse = courses.find((course) => course.id === selectedCourseId);
  const visibleCoachCourses = courses.filter((course) => course.active !== false);
  const selectedSlot = slots.find((slot) => slot.id === selectedSlotId);
  const selectedMountain = selectedSlot?.availableMountains?.find((mountain) => mountain.id === selectedMountainId);
  const dates = Array.from(new Set(slots.map((slot) => slot.localDate))).sort();
  const visibleSlots = slots.filter((slot) => slot.localDate === selectedDate);
  const alreadyApplied = selectedSlotId && bookings.some((booking) => booking.slotId === selectedSlotId
    && booking.status !== 'CANCELLED_BY_STUDENT');
  const visibleBookings = targetBooking
    ? [targetBooking, ...bookings.filter((booking) => booking.id !== targetBooking.id)] : bookings;
  const pending = visibleBookings.filter((booking) => booking.status === 'PENDING');
  const draftDays = calendarSelection.map((localDate) => ({ localDate, startTime: slotStartTime,
    endTime: slotEndTime, mountainId: slotMountainId }));
  const preview = previewDays(draftDays);
  const today = torontoDate();
  const earliestSelectable = timeMinutes(slotStartTime) > timeMinutes(torontoTime()) ? today : dateOffset(today, 1);
  const hasPastSelection = calendarSelection.some((date) => date < earliestSelectable);
  const calendarDates = monthDates(calendarMonth);
  const [calendarYear, calendarNumber] = calendarMonth.split('-').map(Number);
  const firstWeekday = (new Date(Date.UTC(calendarYear, calendarNumber - 1, 1)).getUTCDay() + 6) % 7;
  const detail = calendarDays.find((day) => day.localDate === calendarDetail);

  return <main className="booking-app">
    <header className="booking-header">
      <div className="booking-header-inner">
        <GeerLogo className="booking-logo" />
        <nav aria-label="主导航">
          {account.role === 'STUDENT' ? <>
            <button type="button" className={tab === 'book' ? 'active' : ''} onClick={() => setTab('book')}>约课</button>
            <button type="button" onClick={onAbout}>关于 GEER</button>
            <button type="button" className={tab === 'mine' ? 'active' : ''} onClick={() => setTab('mine')}>我的预约</button>
          </> : <>
            <button type="button" className={tab === 'courses' ? 'active' : ''} onClick={() => setTab('courses')}>创建课程</button>
            <button type="button" className={tab === 'availability' ? 'active' : ''} onClick={() => setTab('availability')}>管理可用时间</button>
            <button type="button" className={tab === 'applications' ? 'active' : ''} onClick={() => setTab('applications')}>预约申请</button>
            <button type="button" onClick={onProfile}>个人主页</button>
          </>}
        </nav>
        <div className="booking-account"><span>{account.name}</span><button type="button" onClick={onLogout}>退出</button></div>
      </div>
    </header>

    <section className="booking-hero">
      <div className="booking-hero-inner">
        <p className="booking-eyebrow">欢迎回来，{account.name}</p>
        <h1>{account.role === 'STUDENT' ? '预约单板课' : '教练工作区'}</h1>
        <p>{account.role === 'STUDENT' ? '两小时 · 一对一 · 线下付款' : '管理课程、雪场和可用时间，处理预约申请'}</p>
      </div>
    </section>

    <div className="booking-content">
      {(error || authMessage) && <div className="booking-alert error" role="alert">
        <span>{error || authMessage}</span><button type="button" onClick={() => { setError(''); setReload((value) => value + 1); }}>重试</button>
      </div>}
      {notice && <div className="booking-alert success" role="status">{notice}</div>}
      {targetError && <div className="booking-alert error" role="alert">{targetError}</div>}
      {loading && <p className="booking-loading" role="status">正在加载约课信息…</p>}

      {account.role === 'STUDENT' && tab === 'book' && <>
        <div className="booking-grid">
          <section className="booking-panel booking-picker" aria-label="选择课程和时段">
            <div className="panel-heading"><h2>选择课程</h2><span>一对一 · 固定 2 小时</span></div>
            {!loading && courses.length === 0 && <p className="booking-empty">教练还没有发布课程，请稍后再来看看。</p>}
            <div className="course-options">{courses.map((course) => <CourseCard key={`${course.id}:${course.coverAssetId ?? ''}`}
              course={course} selected={selectedCourseId === course.id} refreshCover={() => refreshCourseCover(course.id)}
              onSelect={() => { setSelectedCourseId(course.id); setSelectedDate(null); setSelectedSlotId(null);
                setSelectedMountainId(null); setNotice(''); }} />)}</div>
            {courseCursor && <button type="button" className="booking-load-more" disabled={loadingMore}
              onClick={() => loadMore('courses')}>加载更多课程</button>}

            {selectedCourse && <>
              <div className="panel-heading booking-date-heading"><h2>选择日期</h2>
                <div className="booking-window"><button type="button" onClick={() => setWindowStart(dateOffset(windowStart, -30))}>上一个月</button>
                  <button type="button" onClick={() => setWindowStart(dateOffset(windowStart, 30))}>下一个月</button></div>
              </div>
              {slotsLoading ? <p className="booking-empty">正在加载时段…</p> : dates.length === 0 ?
                <p className="booking-empty">这段时间暂无可申请的时段，可查看下一个月。</p> :
                <div className="date-options">{dates.map((date) => <button key={date} type="button"
                  className={selectedDate === date ? 'selected' : ''}
                  onClick={() => { setSelectedDate(date); setSelectedSlotId(null); setSelectedMountainId(null); }}>{readableDate(date)}</button>)}</div>}
              <div className="panel-heading"><h2>选择时段</h2><span>当地时间</span></div>
              {selectedDate && visibleSlots.length === 0 && <p className="booking-empty">当天暂无可申请时段。</p>}
              <div className="slot-options">{visibleSlots.map((slot) => <button key={slot.id} type="button"
                className={selectedSlotId === slot.id ? 'selected' : ''}
                onClick={() => { setSelectedSlotId(slot.id); setSelectedMountainId(null);
                  setSubmissionKey(null); setNotice(''); }}>
                <strong>{slotTime(slot)}</strong><small>{slot.zoneId} · 可选 {(slot.availableMountains ?? []).length} 座雪场</small>
              </button>)}</div>
              {slotCursor && <button type="button" className="booking-load-more" disabled={loadingMore}
                onClick={() => loadMore('slots')}>加载更多时段</button>}
              {selectedSlot && <div className="mountain-choice"><h3>选择雪场</h3>
                {(selectedSlot.availableMountains ?? []).length === 0 ?
                  <p className="booking-empty">当天暂无可选雪场，请刷新后重试。</p> :
                  <div className="mountain-options">{selectedSlot.availableMountains.map((mountain) => <button
                    key={mountain.id} type="button" className={selectedMountainId === mountain.id ? 'selected' : ''}
                    onClick={() => { setSelectedMountainId(mountain.id); setSubmissionKey(null); setNotice(''); }}>
                    {mountain.name}</button>)}</div>}
              </div>}
            </>}
          </section>
          <aside className="booking-panel booking-summary">
            <h2>申请信息</h2>
            <dl><div><dt>课程</dt><dd>{selectedCourse ? `课程：${selectedCourse.title}` : '请选择课程'}</dd></div>
              <div><dt>日期</dt><dd>{selectedDate ? readableDate(selectedDate) : '请选择日期'}</dd></div>
              <div><dt>时间</dt><dd>{selectedSlot ? slotTime(selectedSlot) : '请选择时段'}</dd></div>
              <div><dt>雪场</dt><dd>{selectedMountain ? selectedMountain.name : '请选择雪场'}</dd></div>
              <div><dt>价格</dt><dd>{selectedCourse ? `CAD ${selectedCourse.priceAmount}` : '—'}</dd></div></dl>
            <div className="student-contact">
              <label htmlFor="student-contact-phone">联系电话（必填）</label>
              <p id="student-contact-purpose" className="student-contact-help">用于教练联系你、沟通并确认预约。</p>
              <ContactPhoneInput value={contactPhone} disabled={busy || contactLoading || !contactReady}
                invalid={Boolean(contactError)}
                describedBy={`student-contact-purpose student-contact-format${contactError ? ' student-contact-error' : ''}`}
                onChange={(phone) => { setContactPhone(phone); setContactError(''); setContactNotice(''); }} />
              <p id="student-contact-format" className="student-contact-help">选择国家或地区后填写电话号码。保存后，下次预约会自动填入。</p>
              {contactLoading && <p className="student-contact-help" role="status">正在加载联系电话…</p>}
              {contactReadError && <div className="student-contact-read-error">
                <p className="booking-inline-status error" role="alert">{contactReadError}</p>
                <button type="button" disabled={busy || contactLoading} onClick={() => setContactReload((value) => value + 1)}>重试加载电话</button>
              </div>}
              <button type="button" className="student-contact-save" disabled={busy || contactLoading || !contactReady}
                onClick={saveContact}>保存电话</button>
              {contactError && <p id="student-contact-error" className="booking-inline-status error" role="alert">{contactError}</p>}
              {contactNotice && <p className="booking-inline-status success" role="status">{contactNotice}</p>}
            </div>
            <button type="button" className="booking-primary" disabled={!selectedSlot || !selectedMountain || busy || !contactReady || contactLoading || Boolean(alreadyApplied)} onClick={submitBooking}>
              {busy ? '提交中…' : alreadyApplied ? '已申请该时段' : '申请预约'}</button>
            <p className="booking-cancel-policy">待确认申请可取消；教练确认后，须在课程开始至少 24 小时前取消。</p>
            {bookingFeedback && <p className="booking-inline-status success" role="status">{bookingFeedback}</p>}
            {bookingWriteError && <p className="booking-inline-status error" role="alert">{bookingWriteError}</p>}
            <p className="booking-note">申请后由教练确认；待确认不占用名额。课程费用线下支付。</p>
          </aside>
        </div>
        <section className="booking-panel booking-recent"><div className="panel-heading"><h2>我的预约</h2>
          <button type="button" onClick={() => setTab('mine')}>查看全部</button></div>
          {cancelFeedback && <p className="booking-inline-status success" role="status">{cancelFeedback}</p>}
          {bookings.length === 0 ? <p className="booking-empty">还没有预约申请。</p> :
            <BookingList items={bookings.slice(0, 2)} busy={busy} cancelBookingId={cancelBookingId} cancelReason={cancelReason} cancelError={cancelError}
              onReasonChange={setCancelReason} onCancelStart={(booking) => { setCancelBookingId(booking.id); setCancelReason(''); setCancelError(''); }}
              onCancelClose={() => setCancelBookingId(null)} onCancel={cancelMine} />}</section>
      </>}

      {account.role === 'STUDENT' && tab === 'mine' && <section className="booking-panel">
        <div className="panel-heading"><h2>我的预约</h2><button type="button" onClick={() => setReload((value) => value + 1)}>刷新状态</button></div>
        {cancelFeedback && <p className="booking-inline-status success" role="status">{cancelFeedback}</p>}
        {visibleBookings.length === 0 ? <p className="booking-empty">还没有预约申请。回到“约课”选择课程和时段。</p> :
          <BookingList items={visibleBookings} busy={busy} cancelBookingId={cancelBookingId} cancelReason={cancelReason} cancelError={cancelError}
            highlightId={targetBooking?.id}
            onReasonChange={setCancelReason} onCancelStart={(booking) => { setCancelBookingId(booking.id); setCancelReason(''); setCancelError(''); }}
            onCancelClose={() => setCancelBookingId(null)} onCancel={cancelMine} />}
        {bookingCursor && <button type="button" className="booking-load-more" disabled={loadingMore}
          onClick={() => loadMore('bookings')}>加载更多预约</button>}
      </section>}

      {account.role === 'COACH' && <div className="coach-layout">
        {tab === 'applications' && <section className="booking-panel coach-applications">
          <div className="panel-heading"><h2>预约申请 <span className="count">{pending.length} 待确认</span></h2>
            <button type="button" onClick={() => setReload((value) => value + 1)}>刷新</button></div>
          <p className="booking-note">确认第一笔申请后，当天只在该雪场授课；其他雪场的待确认申请会自动拒绝。同一时段仅确认一人。</p>
          {visibleBookings.length === 0 ? <p className="booking-empty">目前没有预约申请。设置雪场、发布课程和可用时间后，学员即可提交申请。</p> :
            <div className="coach-booking-list">{visibleBookings.map((booking) => <article
              id={'booking-' + booking.id} className={'coach-booking' + (targetBooking?.id === booking.id ? ' booking-target' : '')}
              key={booking.id}>
              <div><span className={`booking-status ${booking.status.toLowerCase()}`}>{statusLabel(booking.status)}</span>
                <h3>{booking.studentName || '学员'} · {booking.courseTitle}</h3>
                <p className="coach-booking-contact">联系电话：{booking.studentPhone
                  ? <a href={`tel:${booking.studentPhone}`}>{booking.studentPhone}</a>
                  : <span>未提供电话</span>}</p>
                <p>{readableDate(booking.localDate)} · {slotTime(booking)} · {booking.location}</p>
                <p>CAD {booking.priceAmount} · {booking.zoneId}</p></div>
              {booking.status === 'PENDING' && <div className="coach-actions">
                <button type="button" disabled={busy} onClick={() => decide(booking, 'confirm')}>确认</button>
                <button type="button" disabled={busy} onClick={() => { setRejectBookingId(booking.id); setRejectReason(''); setRejectError(''); }}>拒绝</button>
              </div>}
              {rejectBookingId === booking.id && <form className="booking-decision-form" onSubmit={(event) => {
                event.preventDefault(); if (rejectReason.trim()) void decide(booking, 'reject', rejectReason.trim());
              }}>
                <label htmlFor={`reject-${booking.id}`}>拒绝原因</label>
                <textarea id={`reject-${booking.id}`} required maxLength={200} value={rejectReason}
                  onChange={(event) => setRejectReason(event.target.value)} />
                {rejectError && <p className="booking-inline-status error" role="alert">{rejectError}</p>}
                <div><button type="submit" disabled={busy || !rejectReason.trim()}>确认拒绝</button>
                  <button type="button" onClick={() => setRejectBookingId(null)}>返回</button></div>
              </form>}
            </article>)}</div>}
          {bookingCursor && <button type="button" className="booking-load-more" disabled={loadingMore}
            onClick={() => loadMore('bookings')}>加载更多申请</button>}
        </section>}
        {(tab === 'courses' || tab === 'availability') && <aside className={`coach-publish ${tab}`}>
          {tab === 'availability' && <section className="booking-panel coach-mountain-panel">
            <div className="panel-heading"><h2>雪场 <span>{mountains.filter((mountain) => mountain.active).length} 座使用中</span></h2>
              <button type="button" aria-expanded={mountainManagerOpen} onClick={() => setMountainManagerOpen((value) => !value)}>
                {mountainManagerOpen ? '收起管理' : '管理雪场'}</button></div>
            <p className="coach-mountain-summary">{mountains.filter((mountain) => mountain.active).map((mountain) => mountain.name).join(' · ') || '尚未设置雪场'}</p>
            <form onSubmit={publishMountain} noValidate>
              <label htmlFor="mountain-name">雪场名称</label><input id="mountain-name" required maxLength={200}
                value={mountainName} onChange={(event) => { setMountainName(event.target.value); setMountainKey(null);
                  setMountainFeedback(''); setMountainWriteError(''); }} />
              <button type="submit" className="booking-primary" disabled={busy}>新增雪场</button>
              {mountainFeedback && <p className="booking-inline-status success coach-mountain-feedback" role="status">{mountainFeedback}</p>}
              {mountainWriteError && <p className="booking-inline-status error coach-mountain-feedback" role="alert">{mountainWriteError}</p>}
            </form>
            {mountains.length === 0 && <p className="booking-empty">先设置至少一座雪场，再发布可用时间。</p>}
            {mountainManagerOpen && <div className="coach-mountains">{mountains.map((mountain) => <div key={mountain.id} className="coach-mountain-row">
              <input aria-label={`修改${mountain.name}的名称`} value={mountainEdits[mountain.id] ?? mountain.name}
                onChange={(event) => setMountainEdits((current) => ({ ...current, [mountain.id]: event.target.value }))} />
              <span>{mountain.active ? '使用中' : '已停用'}</span>
              <button type="button" disabled={busy} onClick={() => changeMountain(mountain, 'rename')}>改名</button>
              {mountain.active && <button type="button" disabled={busy}
                onClick={() => changeMountain(mountain, 'deactivate')}>停用</button>}
            </div>)}</div>}
            {mountainManagerOpen && mountainCursor && <button type="button" className="booking-load-more" disabled={loadingMore}
              onClick={() => loadMore('mountains')}>加载更多雪场</button>}
            {mountainManagerOpen && <p className="booking-note">有待确认申请的雪场暂不能停用；改名不会修改已有预约的地点记录。</p>}
          </section>}
          {tab === 'courses' && <section className="booking-panel"><h2>发布课程</h2>
            <form onSubmit={publishCourse}>
              <label htmlFor="course-title">课程名称</label><input id="course-title" required maxLength={100} value={courseTitle} onChange={(event) => { setCourseTitle(event.target.value); setCourseKey(null); }} />
              <label htmlFor="course-description">课程介绍</label><textarea id="course-description" maxLength={1000} value={courseDescription} onChange={(event) => { setCourseDescription(event.target.value); setCourseKey(null); }} />
              <label htmlFor="course-price">课程价格（CAD）</label><input id="course-price" required type="number" min="0" max="99999999.99" step="0.01" value={coursePrice} onChange={(event) => { setCoursePrice(event.target.value); setCourseKey(null); }} />
              <CourseCoverEditor value={courseCover} course={{ title: courseTitle, description: courseDescription, priceAmount: coursePrice }} onChange={(value) => {
                setCourseCover(value);
                if (value.coverAssetId !== courseCover.coverAssetId || value.coverPositionX !== courseCover.coverPositionX
                    || value.coverPositionY !== courseCover.coverPositionY) setCourseKey(null);
              }}
                disabled={busy} refreshCsrf={refreshCsrf} onUnauthorized={onUnauthorized} onPendingChange={setCoverPending} />
              <button type="submit" className="booking-primary" disabled={busy || coverPending}>发布课程</button>
            </form>
          </section>}
          {tab === 'availability' && <section className="booking-panel"><h2>批量发布可用时间</h2>
            <form onSubmit={publishSlot} noValidate>
              <p className="booking-note">在月历点选多个日期，再统一设置时间。系统连续拆成两小时；当天雪场留空表示活动雪场均可申请。</p>
              <div className="coach-calendar" aria-label="教练可用时间月历">
                <div className="coach-calendar-heading">
                  <button type="button" onClick={() => { setCalendarMonth((current) => shiftMonth(current, -1)); setCalendarAction(null); }}>上一个月</button>
                  <strong>{calendarYear}年{calendarNumber}月</strong>
                  <button type="button" onClick={() => { setCalendarMonth((current) => shiftMonth(current, 1)); setCalendarAction(null); }}>下一个月</button>
                </div>
                {calendarAction && <p className={`booking-inline-status ${calendarAction.error ? 'error' : 'success'} coach-calendar-action`}
                  role={calendarAction.error ? 'alert' : 'status'}>{calendarAction.message}</p>}
                <div className="coach-calendar-grid">
                  {['一', '二', '三', '四', '五', '六', '日'].map((name) => <span className="coach-calendar-weekday" key={name}>{name}</span>)}
                  {Array.from({ length: firstWeekday }, (_, index) => <span key={`empty-${index}`} aria-hidden="true" />)}
                  {calendarDates.map((date) => {
                    const day = calendarDays.find((item) => item.localDate === date);
                    const selectable = date >= earliestSelectable;
                    const selected = calendarSelection.includes(date);
                    return <button key={date} type="button" className={`coach-calendar-day${selected ? ' selected' : ''}${selected || selectable ? '' : ' unavailable'}${publishedDates.includes(date) ? ' just-published' : ''}`}
                      aria-label={`${selected ? '取消选择日期' : selectable ? '选择日期' : '查看日期'} ${date}`}
                      aria-pressed={selected} onClick={() => {
                        setCalendarDetail(date);
                        if (selected) {
                          setCalendarSelection((current) => current.filter((item) => item !== date));
                          setCalendarAction({ message: `${date} 已移出选择。`, error: false });
                          setSlotKey(null); setError(''); setCalendarWriteError('');
                          return;
                        }
                        const currentToday = torontoDate();
                        if (date < currentToday) {
                          setCalendarAction({ message: `${date} 已过去，不能添加到排班。请选择未来日期。`, error: true });
                          return;
                        }
                        if (date === currentToday && timeMinutes(slotStartTime) <= timeMinutes(torontoTime())) {
                          setCalendarAction({ message: `今天的开始时间 ${slotStartTime} 已过，请调整开始时间或选择未来日期。`, error: true });
                          return;
                        }
                        if (calendarSelection.length >= 31) {
                          setCalendarAction({ message: '每批最多选择 31 个日期。', error: true });
                          return;
                        }
                        setCalendarSelection((current) => [...current, date].sort());
                        setCalendarAction({ message: `${date} 已添加，当前选中 ${calendarSelection.length + 1} 天。`, error: false });
                        setSlotKey(null); setError(''); setCalendarWriteError('');
                      }}>
                      <strong>{Number(date.slice(-2))}</strong>
                      <small>{day?.legacyReviewRequired ? '待映射' : day?.lockedMountain ? '已锁山' : day?.slots.some((slot) => slot.status === 'BOOKED') ? '已预约' : day?.slots.length ? `${day.slots.length} 时段` : publishedDates.includes(date) ? '刚发布' : selectable ? '可添加' : '仅查看'}</small>
                    </button>;
                  })}
                </div>
                {calendarLoading && <p className="booking-loading">正在加载月历…</p>}
                {calendarError && <p className="booking-alert error">{calendarError}<button type="button" onClick={() => setReload((value) => value + 1)}>重试</button></p>}
                {!calendarLoading && !calendarError && calendarDays.length === 0 && <p className="booking-note">本月还没有发布可用时间。</p>}
                {calendarDetail && <div className="coach-calendar-detail"><strong>{readableDate(calendarDetail)}</strong>
                  <p>{detail?.legacyReviewRequired ? '旧地点待映射，暂不能覆盖。' : detail?.lockedMountain
                    ? `当天已锁定 ${detail.lockedMountain.name}` : detail?.limitedMountain
                      ? `当天限 ${detail.limitedMountain.name}` : '当天雪场不限'}</p>
                  {detail?.slots.length ? detail.slots.map((slot) => <p key={slot.id}>{slotTime(slot)} · {slot.status === 'BOOKED' ? '已确认' : '可申请'}</p>)
                    : <p>{publishedDates.includes(calendarDetail) ? '已提交，正在同步日历。' : '当天暂无时段。'}</p>}</div>}
              </div>
              <p className="booking-preview">已选 {calendarSelection.length} 天：{calendarSelection.join('、') || '请在月历中点选日期'}</p>
              <div className="form-row"><div><label htmlFor="slot-start">开始时间</label><input id="slot-start" required type="time"
                value={slotStartTime} onChange={(event) => { setSlotStartTime(event.target.value); setSlotKey(null);
                  setCalendarAction(null); setCalendarWriteError(''); }} /></div>
                <div><label htmlFor="slot-end">结束时间</label><input id="slot-end" required type="time"
                  value={slotEndTime} onChange={(event) => { setSlotEndTime(event.target.value); setSlotKey(null);
                    setCalendarWriteError(''); }} /></div></div>
              <label htmlFor="slot-mountain">当天雪场限制</label><select id="slot-mountain" value={slotMountainId}
                onChange={(event) => { setSlotMountainId(event.target.value); setSlotKey(null); setCalendarWriteError(''); }}>
                <option value="">不限雪场</option>
                {mountains.filter((mountain) => mountain.active).map((mountain) =>
                  <option key={mountain.id} value={mountain.id}>{mountain.name}</option>)}
              </select>
              <p className="booking-preview" aria-live="polite">预计生成 {preview.count} 个两小时可用时间。
                {preview.tails.length > 0 && <> 未满两小时的尾段不发布：{preview.tails.join('、')}。</>}</p>
              {preview.count > 100 && <p className="booking-alert error">每批最多发布 100 个时段，请减少日期或时间范围。</p>}
              {hasPastSelection && <p className="booking-alert error">所选日期的开始时间已过，请调整时间或取消该日期。</p>}
              <p className="booking-note">提交会撤回所选日期原有的未确认时段，保留已确认预约；有待确认申请的日期会阻止整批覆盖。</p>
              <button type="submit" className="booking-primary" disabled={busy}>
                {busy ? '正在发布…' : '整天覆盖并发布'}</button>
              {calendarFeedback && <p className="booking-inline-status success" role="status">{calendarFeedback}</p>}
              {calendarWriteError && <p className="booking-inline-status error" role="alert">{calendarWriteError}</p>}
            </form>
          </section>}
          {tab === 'courses' && <section className="booking-panel"><h2>已发布课程</h2>
            {courseFeedback && <p className="booking-inline-status success" role="status">{courseFeedback}</p>}
            {courseActionError && <p className="booking-inline-status error" role="alert">{courseActionError}</p>}
            {visibleCoachCourses.length === 0 ? <p className="booking-empty">
              {courseCursor ? '当前暂无可显示课程，请加载更多。' : '尚无已发布课程。'}
            </p> : visibleCoachCourses.map((course) =>
              <article className="coach-course-row" key={course.id}>
                <div><strong>{course.title}</strong> · CAD {course.priceAmount}
                  {course.active === false && <span className="course-archived">已下架</span>}
                  {course.description && <p>{course.description}</p>}</div>
                {course.active !== false && <div className="coach-course-actions">
                  <button type="button" disabled={busy} onClick={() => { setEditingCourseId(course.id);
                    setCourseEdit({ title: course.title, description: course.description, priceAmount: String(course.priceAmount) });
                    setEditCover({ coverAssetId: course.coverAssetId ?? null, cover: course.cover ?? null,
                      coverPositionX: course.coverPositionX ?? 50, coverPositionY: course.coverPositionY ?? 50 }); setEditCoverPending(false);
                    setArchiveCourseId(null); }}>编辑</button>
                  <button type="button" disabled={busy} onClick={() => { setArchiveCourseId(course.id); setEditingCourseId(null); }}>删除课程</button>
                </div>}
                {editingCourseId === course.id && <form className="course-edit-form" onSubmit={updateCourse}>
                  <label htmlFor={`edit-title-${course.id}`}>课程名称</label>
                  <input id={`edit-title-${course.id}`} required maxLength={100} value={courseEdit.title}
                    onChange={(event) => setCourseEdit((current) => ({ ...current, title: event.target.value }))} />
                  <label htmlFor={`edit-description-${course.id}`}>课程介绍</label>
                  <textarea id={`edit-description-${course.id}`} maxLength={1000} value={courseEdit.description}
                    onChange={(event) => setCourseEdit((current) => ({ ...current, description: event.target.value }))} />
                  <label htmlFor={`edit-price-${course.id}`}>课程价格（CAD）</label>
                  <input id={`edit-price-${course.id}`} type="number" required min="0" step="0.01" value={courseEdit.priceAmount}
                    onChange={(event) => setCourseEdit((current) => ({ ...current, priceAmount: event.target.value }))} />
                  <CourseCoverEditor key={course.id} value={editCover} course={courseEdit} onChange={setEditCover}
                    disabled={busy} refreshCsrf={refreshCsrf} onUnauthorized={onUnauthorized} onPendingChange={setEditCoverPending} />
                  <div className="coach-course-actions"><button type="submit" disabled={busy || editCoverPending}>保存修改</button>
                    <button type="button" onClick={() => { setEditingCourseId(null); setEditCoverPending(false); }}>取消编辑</button></div>
                </form>}
                {archiveCourseId === course.id && <div className="course-archive-confirm" role="group" aria-label={`删除${course.title}`}>
                  <p>删除后学员将无法选择这门课程，已有预约不受影响。</p>
                  <div className="coach-course-actions"><button type="button" disabled={busy} onClick={() => archiveCourse(course.id)}>确认删除</button>
                    <button type="button" onClick={() => setArchiveCourseId(null)}>返回</button></div>
                </div>}
              </article>)}
            {courseCursor && <button type="button" className="booking-load-more" disabled={loadingMore}
              onClick={() => loadMore('courses')}>加载更多课程</button>}</section>}
        </aside>}
      </div>}
    </div>
  </main>;
}

function BookingList({ items, busy, cancelBookingId, cancelReason, cancelError, onReasonChange, onCancelStart, onCancelClose, onCancel, highlightId }:
  { items: Booking[]; busy: boolean; cancelBookingId: string | null; cancelReason: string; cancelError: string;
    onReasonChange: (value: string) => void; onCancelStart: (booking: Booking) => void;
    onCancelClose: () => void; onCancel: (booking: Booking) => Promise<void>; highlightId?: string }) {
  return <div className="my-booking-list">{items.map((booking) => <article key={booking.id}
    id={'booking-' + booking.id} className={'my-booking' + (highlightId === booking.id ? ' booking-target' : '')}>
    <div><strong>{booking.courseTitle}</strong><p>{readableDate(booking.localDate)} · {slotTime(booking)} · {booking.location}</p>
      <small>CAD {booking.priceAmount} · {booking.zoneId}</small>
      {booking.decisionReason && <p className="booking-reason">{booking.decisionReason}</p>}
      {(booking.status === 'PENDING' || booking.status === 'CONFIRMED') &&
        (booking.status === 'PENDING' || new Date(booking.startAt).getTime() - Date.now() >= 24 * 60 * 60 * 1000
          ? <button type="button" className="booking-cancel-trigger" disabled={busy} onClick={() => onCancelStart(booking)}>取消预约</button>
          : <p className="booking-cutoff">距开课不足 24 小时，无法取消已确认预约。</p>)}
      {cancelBookingId === booking.id && <form className="booking-cancel-form" onSubmit={(event) => {
        event.preventDefault(); void onCancel(booking);
      }}>
        <p>确定取消这笔{booking.status === 'CONFIRMED' ? '已确认预约' : '待确认申请'}吗？</p>
        {booking.status === 'CONFIRMED' && <><label htmlFor={`cancel-reason-${booking.id}`}>取消原因（可选）</label>
          <textarea id={`cancel-reason-${booking.id}`} maxLength={200} value={cancelReason}
            onChange={(event) => onReasonChange(event.target.value)} /></>}
        {cancelError && <p className="booking-inline-status error" role="alert">{cancelError}</p>}
        <div><button type="submit" disabled={busy}>确认取消</button>
          <button type="button" onClick={onCancelClose}>返回</button></div>
      </form>}</div>
    <span className={`booking-status ${booking.status.toLowerCase()}`}>{statusLabel(booking.status)}</span>
  </article>)}</div>;
}
