import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, TOKEN_COOKIE, api, type User } from "./api";

export async function setSession(token: string, expiresInSeconds: number) {
  (await cookies()).set(TOKEN_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: expiresInSeconds,
  });
}

export async function clearSession() {
  (await cookies()).delete(TOKEN_COOKIE);
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
  const user = await currentUser();
  if (!user) redirect("/login");
  return user;
}

export async function requireAdmin(): Promise<User> {
  const user = await requireUser();
  if (user.role !== "ADMIN") redirect("/");
  return user;
}
