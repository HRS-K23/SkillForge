import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";
export const TOKEN_COOKIE = "sf_token";

export type FieldViolation = { field: string; message: string };

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: FieldViolation[] = [],
  ) {
    super(message);
  }

  /** Human readable message including field-level details. */
  get display(): string {
    return this.details.length
      ? this.details.map((d) => `${d.field}: ${d.message}`).join("; ")
      : this.message;
  }
}

export type Tool = {
  id: string;
  name: string;
  slug: string;
  description: string;
  category: string;
  logoUrl: string | null;
};

export type LessonSummary = {
  id: string;
  title: string;
  estimatedTime: number | null;
  order: number;
};
export type Exercise = { id: string; title: string; description: string };
export type Module = {
  id: string;
  title: string;
  description: string | null;
  order: number;
  lessons: LessonSummary[];
  exercises: Exercise[];
};
export type LearningPath = {
  id: string;
  toolSlug: string;
  title: string;
  description: string;
  modules: Module[];
};
export type Lesson = {
  id: string;
  moduleId: string;
  toolSlug: string;
  title: string;
  estimatedTime: number | null;
  youtubeUrl: string | null;
  youtubeEmbedUrl: string | null;
  content: string;
};

export type User = {
  id: string;
  name: string;
  email: string;
  role: string;
  createdAt: string;
};
export type AuthResponse = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
};

export type LessonProgress = {
  lessonId: string;
  completed: boolean;
  completedAt: string | null;
};
export type ModuleProgress = {
  moduleId: string;
  title: string;
  totalLessons: number;
  completedLessons: number;
  percent: number;
  lessons: LessonProgress[];
};
export type ToolProgress = {
  toolSlug: string;
  toolName: string;
  totalLessons: number;
  completedLessons: number;
  percent: number;
  modules: ModuleProgress[];
};

export type SearchType = "TOOL" | "LEARNING_PATH" | "LESSON";
export type SearchHit = {
  type: SearchType;
  id: string;
  title: string;
  snippet: string | null;
  toolSlug: string;
};
export type SearchResponse = { query: string; page: number; size: number; results: SearchHit[] };
export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

type Options = { method?: string; body?: unknown; auth?: boolean };

async function request<T>(path: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (opts.auth) {
    const token = (await cookies()).get(TOKEN_COOKIE)?.value;
    if (!token) throw new ApiError(401, "UNAUTHENTICATED", "Not logged in");
    headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch(BACKEND + path, {
    method: opts.method ?? "GET",
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    cache: "no-store",
  });
  if (!res.ok) {
    let code = "ERROR";
    let message = res.statusText || "Request failed";
    let details: FieldViolation[] = [];
    try {
      const b = await res.json();
      code = b.code ?? code;
      message = b.message ?? message;
      details = b.details ?? [];
    } catch {
      // empty body (e.g. 401 from the resource server)
    }
    throw new ApiError(res.status, code, message, details);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const qs = (params: Record<string, string | undefined>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v) p.set(k, v);
  const s = p.toString();
  return s ? `?${s}` : "";
};

export const api = {
  tools: (q?: string, category?: string, page = 0) =>
    request<Page<Tool>>(`/api/tools${qs({ q, category, page: String(page) })}`),
  categories: () => request<string[]>("/api/tools/categories"),
  tool: (slug: string) =>
    request<Tool>(`/api/tools/${encodeURIComponent(slug)}`),
  learningPath: (slug: string) =>
    request<LearningPath>(`/api/tools/${encodeURIComponent(slug)}/learning-path`),
  lesson: (id: string) => request<Lesson>(`/api/lessons/${encodeURIComponent(id)}`),
  search: (q: string, type?: SearchType, page = 0) =>
    request<SearchResponse>(`/api/search${qs({ q, type, page: String(page) })}`),

  register: (name: string, email: string, password: string) =>
    request<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: { name, email, password },
    }),
  login: (email: string, password: string) =>
    request<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: { email, password },
    }),
  me: () => request<User>("/api/users/me", { auth: true }),
  updateMe: (name: string) =>
    request<User>("/api/users/me", { method: "PUT", body: { name }, auth: true }),  changePassword: (currentPassword: string, newPassword: string) =>
    request<AuthResponse>("/api/users/me/password", {
      method: "PUT",
      body: { currentPassword, newPassword },
      auth: true,
    }),
  deleteAccount: (password: string) =>
    request<void>("/api/users/me", { method: "DELETE", body: { password }, auth: true }),
  forgotPassword: (email: string) =>
    request<void>("/api/auth/forgot-password", { method: "POST", body: { email } }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>("/api/auth/reset-password", { method: "POST", body: { token, newPassword } }),

  setCompleted: (lessonId: string, completed: boolean) =>
    request<LessonProgress>(`/api/progress/lessons/${encodeURIComponent(lessonId)}`, {
      method: "PUT",
      body: { completed },
      auth: true,
    }),
  toolProgress: (slug: string) =>
    request<ToolProgress>(`/api/progress/tools/${encodeURIComponent(slug)}`, {
      auth: true,
    }),
  overview: () => request<ToolProgress[]>("/api/progress", { auth: true }),

  adminReload: () =>
    request<{ tools: number; lessons: number }>("/api/admin/content/reload", { method: "POST", auth: true }),
  adminCreateTool: (body: {
    slug: string;
    name: string;
    description: string;
    category: string;
    logoUrl?: string;
  }) => request<{ path: string }>("/api/admin/tools", { method: "POST", body, auth: true }),
  adminCreateModule: (slug: string, body: { title: string; description?: string }) =>
    request<{ path: string }>(`/api/admin/tools/${encodeURIComponent(slug)}/modules`, {
      method: "POST",
      body,
      auth: true,
    }),
  adminCreateLesson: (
    moduleId: string,
    body: { title: string; estimatedTime?: number; youtubeUrl?: string; content: string },
  ) =>
    request<{ path: string }>(`/api/admin/modules/${encodeURIComponent(moduleId)}/lessons`, {
      method: "POST",
      body,
      auth: true,
    }),
  adminCreateExercise: (moduleId: string, body: { title: string; description: string }) =>
    request<{ path: string }>(`/api/admin/modules/${encodeURIComponent(moduleId)}/exercises`, {
      method: "POST",
      body,
      auth: true,
    }),
  adminUpdateTool: (
    slug: string,
    body: { name: string; description: string; category: string; logoUrl?: string },
  ) => request<{ path: string }>(`/api/admin/tools/${encodeURIComponent(slug)}`, { method: "PUT", body, auth: true }),
  adminDeleteTool: (slug: string) =>
    request<void>(`/api/admin/tools/${encodeURIComponent(slug)}`, { method: "DELETE", auth: true }),
  adminUpdateModule: (id: string, body: { title: string; description?: string }) =>
    request<{ path: string }>(`/api/admin/modules/${encodeURIComponent(id)}`, { method: "PUT", body, auth: true }),
  adminDeleteModule: (id: string) =>
    request<void>(`/api/admin/modules/${encodeURIComponent(id)}`, { method: "DELETE", auth: true }),
  adminUpdateLesson: (
    id: string,
    body: { title: string; estimatedTime?: number; youtubeUrl?: string; content: string },
  ) => request<{ path: string }>(`/api/admin/lessons/${encodeURIComponent(id)}`, { method: "PUT", body, auth: true }),
  adminDeleteLesson: (id: string) =>
    request<void>(`/api/admin/lessons/${encodeURIComponent(id)}`, { method: "DELETE", auth: true }),
  adminUpdateExercise: (id: string, body: { title: string; description: string }) =>
    request<{ path: string }>(`/api/admin/exercises/${encodeURIComponent(id)}`, { method: "PUT", body, auth: true }),
  adminDeleteExercise: (id: string) =>
    request<void>(`/api/admin/exercises/${encodeURIComponent(id)}`, { method: "DELETE", auth: true }),
  adminReorder: (kind: "modules" | "lessons" | "exercises", parent: string, ids: string[]) =>
    request<void>(
      kind === "modules"
        ? `/api/admin/tools/${encodeURIComponent(parent)}/modules/order`
        : `/api/admin/modules/${encodeURIComponent(parent)}/${kind}/order`,
      { method: "PUT", body: { ids }, auth: true },
    ),
};