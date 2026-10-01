"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, api } from "@/lib/api";
import { clearSession, endSessionOn401, requireAdmin, setSession } from "@/lib/session";

export type FormState = { error?: string; success?: string; values?: Record<string, string> };

const str = (data: FormData, key: string) => String(data.get(key) ?? "").trim();

function fail(e: unknown): FormState {
  if (e instanceof ApiError) return { error: e.display };
  return { error: "Something went wrong. Please try again." };
}

function failAuthed(e: unknown): FormState {
  endSessionOn401(e);
  return fail(e);
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
    return failAuthed(e);
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
    endSessionOn401(e);
    throw e;
  }
  if (back.startsWith("/")) revalidatePath(back);
  revalidatePath("/progress");
}

async function adminRun(work: () => Promise<string>): Promise<FormState> {
  await requireAdmin();
  try {
    const success = await work();
    revalidatePath("/", "layout");
    return { success };
  } catch (e) {
    return failAuthed(e);
  }
}

const optional = (data: FormData, key: string) => str(data, key) || undefined;

export async function reloadContentAction(): Promise<FormState> {
  return adminRun(async () => {
    const r = await api.adminReload();
    return `Reloaded: ${r.tools} tools, ${r.lessons} lessons.`;
  });
}

export async function createToolAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    await api.adminCreateTool({
      slug: str(data, "slug"),
      name: str(data, "name"),
      description: str(data, "description"),
      category: str(data, "category"),
      logoUrl: optional(data, "logoUrl"),
    });
    return "Tool created.";
  });
}

export async function createModuleAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    await api.adminCreateModule(str(data, "slug"), {
      title: str(data, "title"),
      description: optional(data, "description"),
    });
    return "Module added.";
  });
}

export async function createLessonAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    const minutes = optional(data, "estimatedTime");
    await api.adminCreateLesson(str(data, "moduleId"), {
      title: str(data, "title"),
      estimatedTime: minutes === undefined ? undefined : Number(minutes),
      youtubeUrl: optional(data, "youtubeUrl"),
      content: String(data.get("content") ?? ""),
    });
    return "Lesson added.";
  });
}

export async function createExerciseAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    await api.adminCreateExercise(str(data, "moduleId"), {
      title: str(data, "title"),
      description: String(data.get("description") ?? ""),
    });
    return "Exercise added.";
  });
}

export async function forgotPasswordAction(_: FormState, data: FormData): Promise<FormState> {
  try {
    await api.forgotPassword(str(data, "email"));
  } catch (e) {
    return fail(e);
  }
  return { success: "If an account exists for that email, a reset link is on its way." };
}

export async function resetPasswordAction(_: FormState, data: FormData): Promise<FormState> {
  try {
    await api.resetPassword(str(data, "token"), String(data.get("newPassword") ?? ""));
  } catch (e) {
    return fail(e);
  }
  redirect("/login?reset=1");
}

export async function changePasswordAction(_: FormState, data: FormData): Promise<FormState> {
  try {
    const res = await api.changePassword(
      String(data.get("currentPassword") ?? ""),
      String(data.get("newPassword") ?? ""),
    );
    await setSession(res.accessToken, res.expiresInSeconds);
  } catch (e) {
    return failAuthed(e);
  }
  return { success: "Password changed. Other devices have been signed out." };
}

export async function deleteAccountAction(_: FormState, data: FormData): Promise<FormState> {
  try {
    await api.deleteAccount(String(data.get("password") ?? ""));
  } catch (e) {
    return failAuthed(e);
  }
  await clearSession();
  redirect("/login?deleted=1");
}

export async function updateToolAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    await api.adminUpdateTool(str(data, "slug"), {
      name: str(data, "name"),
      description: str(data, "description"),
      category: str(data, "category"),
      logoUrl: optional(data, "logoUrl"),
    });
    return "Tool saved.";
  });
}

export async function updateModuleAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    await api.adminUpdateModule(str(data, "moduleId"), {
      title: str(data, "title"),
      description: optional(data, "description"),
    });
    return "Module saved.";
  });
}

export async function updateLessonAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    const minutes = optional(data, "estimatedTime");
    await api.adminUpdateLesson(str(data, "lessonId"), {
      title: str(data, "title"),
      estimatedTime: minutes === undefined ? undefined : Number(minutes),
      youtubeUrl: optional(data, "youtubeUrl"),
      content: String(data.get("content") ?? ""),
    });
    return "Lesson saved.";
  });
}

export async function updateExerciseAction(_: FormState, data: FormData): Promise<FormState> {
  return adminRun(async () => {
    await api.adminUpdateExercise(str(data, "exerciseId"), {
      title: str(data, "title"),
      description: String(data.get("description") ?? ""),
    });
    return "Exercise saved.";
  });
}

async function adminCommand(work: () => Promise<void>) {
  await requireAdmin();
  try {
    await work();
  } catch (e) {
    endSessionOn401(e);
    throw e;
  }
  revalidatePath("/", "layout");
}

export async function deleteAdminItemAction(data: FormData) {
  const id = str(data, "id");
  const kind = str(data, "kind");
  await adminCommand(async () => {
    if (kind === "tool") await api.adminDeleteTool(id);
    else if (kind === "module") await api.adminDeleteModule(id);
    else if (kind === "lesson") await api.adminDeleteLesson(id);
    else if (kind === "exercise") await api.adminDeleteExercise(id);
  });
  if (kind === "tool") redirect("/admin");
}

export async function reorderAction(data: FormData) {
  const kind = str(data, "kind") as "modules" | "lessons" | "exercises";
  const ids = str(data, "ids").split(",").filter(Boolean);
  const i = ids.indexOf(str(data, "id"));
  const j = str(data, "dir") === "up" ? i - 1 : i + 1;
  if (i < 0 || j < 0 || j >= ids.length) return;
  [ids[i], ids[j]] = [ids[j], ids[i]];
  await adminCommand(() => api.adminReorder(kind, str(data, "parent"), ids));
}
