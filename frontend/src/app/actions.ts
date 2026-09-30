"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, api } from "@/lib/api";
import { clearSession, setSession } from "@/lib/session";

export type FormState = { error?: string; success?: string; values?: Record<string, string> };

const str = (data: FormData, key: string) => String(data.get(key) ?? "").trim();

function fail(e: unknown): FormState {
  if (e instanceof ApiError) return { error: e.display };
  return { error: "Something went wrong. Please try again." };
}

export async function loginAction(_: FormState, data: FormData): Promise<FormState> {
  try {
    const res = await api.login(str(data, "email"), String(data.get("password") ?? ""));
    await setSession(res.accessToken, res.expiresInSeconds);
  } catch (e) {
    return fail(e);
  }
  redirect("/");
}

export async function registerAction(_: FormState, data: FormData): Promise<FormState> {
  try {
    const res = await api.register(
      str(data, "name"),
      str(data, "email"),
      String(data.get("password") ?? ""),
    );
    await setSession(res.accessToken, res.expiresInSeconds);
  } catch (e) {
    return fail(e);
  }
  redirect("/");
}

export async function logoutAction() {
  await clearSession();
  redirect("/");
}

export async function updateProfileAction(_: FormState, data: FormData): Promise<FormState> {
  try {
    await api.updateMe(str(data, "name"));
  } catch (e) {
    return fail(e);
  }
  revalidatePath("/", "layout");
  return { success: "Profile updated." };
}

export async function setCompletedAction(data: FormData) {
  const lessonId = str(data, "lessonId");
  const completed = str(data, "completed") === "true";
  const back = str(data, "redirectTo");
  try {
    await api.setCompleted(lessonId, completed);
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) redirect("/login");
    throw e;
  }
  if (back.startsWith("/")) revalidatePath(back);
  revalidatePath("/progress");
}
