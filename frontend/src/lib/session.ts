import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, TOKEN_COOKIE, api, type User } from "./api";

// The cookie outlives the token so an expired session can be told apart from "never logged in".
const COOKIE_GRACE_SECONDS = 7 * 24 * 60 * 60;

export async function setSession(token: string, expiresInSeconds: number) {
  (await cookies()).set(TOKEN_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.COOKIE_SECURE
      ? process.env.COOKIE_SECURE === "true"
      : process.env.NODE_ENV === "production",
    path: "/",
    maxAge: expiresInSeconds + COOKIE_GRACE_SECONDS,
  });
}

export async function clearSession() {
  (await cookies()).delete(TOKEN_COOKIE);
}

/** Sends the user to the "session expired" flow when the API rejected their token. */
export function endSessionOn401(e: unknown): void {
  if (e instanceof ApiError && e.status === 401) {
    redirect(`/session-expired?reason=${e.code === "TOKEN_EXPIRED" ? "expired" : "ended"}`);
  }
}

/** Returns the logged-in user, or null when logged out or the token is invalid/expired. */
export async function currentUser(): Promise<User | null> {
  const token = (await cookies()).get(TOKEN_COOKIE)?.value;
  if (!token) return null;
  try {
    return await api.me();
  } catch (e) {
    if (e instanceof ApiError && (e.status === 401 || e.status === 404)) return null;
    throw e;
  }
}

export async function requireUser(): Promise<User> {
  const token = (await cookies()).get(TOKEN_COOKIE)?.value;
  if (!token) redirect("/login");
  try {
    return await api.me();
  } catch (e) {
    endSessionOn401(e);
    throw e;
  }
}

export async function requireAdmin(): Promise<User> {
  const user = await requireUser();
  if (user.role !== "ADMIN") redirect("/");
  return user;
}