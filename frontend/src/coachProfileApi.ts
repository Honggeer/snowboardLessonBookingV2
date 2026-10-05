export type ProfileContent = Record<string, string>;
export type MediaPurpose = 'HERO' | 'CERTIFICATE' | 'WECHAT_QR' | 'VIDEO_POSTER' | 'HIGHLIGHT_VIDEO' | 'COURSE_COVER';
export type MediaLink = { id: string; url: string; expiresAt: string; width?: number; height?: number; duration?: number; size?: number };
export type CoachProfile = { published: boolean; version: number; publishedVersion: number; content: ProfileContent; media: Partial<Record<MediaPurpose, MediaLink>> };
export type Upload = { id: string; purpose: MediaPurpose; status: string; size: number; uploadUrl: string | null;
  headers: Record<string, string>; expiresAt: string; preview: MediaLink | null; errorCode: string | null };
export type Csrf = { token: string; headerName: string };
export class ProfileApiError extends Error {
  constructor(readonly status: number, message: string) { super(message); }
}
export async function profileRequest<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try { response = await fetch(path, { credentials: 'same-origin', ...options }); }
  catch { throw new Error('无法连接服务，请稍后重试。'); }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new ProfileApiError(response.status, body.detail ?? '操作未完成，请重试。');
  return body as T;
}
export function requestKey() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
export function socialLinkTarget(value: string | undefined) {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : undefined;
  } catch { return undefined; }
}
