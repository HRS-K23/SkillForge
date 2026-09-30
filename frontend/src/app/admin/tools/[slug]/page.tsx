import Link from "next/link";
import { notFound } from "next/navigation";
import { createExerciseAction, createLessonAction, createModuleAction } from "@/app/actions";
import AdminForm from "@/components/AdminForm";
import { ApiError, api, type LearningPath } from "@/lib/api";
import { requireAdmin } from "@/lib/session";

export default async function AdminToolPage({ params }: { params: Promise<{ slug: string }> }) {
  await requireAdmin();
  const { slug } = await params;
  let tool;
  try {
    tool = await api.tool(slug);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound();
    throw e;
  }
  let path: LearningPath | null = null;
  try {
    path = await api.learningPath(slug);
  } catch (e) {
    if (!(e instanceof ApiError && e.status === 404)) throw e;
  }

  return (
    <div className="space-y-8">
      <div>
        <Link href="/admin" className="text-sm text-indigo-700 hover:underline">
          ← Content admin
        </Link>
        <h1 className="mt-2 text-2xl font-bold">{tool.name}</h1>
        <Link href={`/tools/${slug}`} className="text-sm text-slate-600 hover:underline">
          View public page
        </Link>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Add a module</h2>
        <div className="max-w-xl">
          <AdminForm
            action={createModuleAction}
            hidden={{ slug }}
            submitLabel="Add module"
            fields={[
              { name: "title", label: "Title", maxLength: 150 },
              { name: "description", label: "Description", optional: true, maxLength: 500 },
            ]}
          />
        </div>
      </section>

      {(path?.modules ?? []).map((m) => (
        <section key={m.id} className="space-y-4 rounded-lg border border-slate-200 bg-white p-4">
          <div>
            <h2 className="text-lg font-semibold">
              Module {m.order}: {m.title}
            </h2>
            <p className="text-sm text-slate-600">
              {m.lessons.length} lessons, {m.exercises.length} exercises
            </p>
          </div>
          <details>
            <summary className="cursor-pointer text-sm font-medium text-indigo-700">Add a lesson</summary>
            <div className="mt-3 max-w-2xl">
              <AdminForm
                action={createLessonAction}
                hidden={{ moduleId: m.id }}
                submitLabel="Add lesson"
                fields={[
                  { name: "title", label: "Title", maxLength: 150 },
                  { name: "estimatedTime", label: "Estimated time (minutes)", type: "number", min: 0, optional: true },
                  { name: "youtubeUrl", label: "YouTube URL", type: "url", optional: true, maxLength: 300 },
                  {
                    name: "content",
                    label: "Content (Markdown)",
                    multiline: true,
                    maxLength: 100000,
                    hint: "Images: upload the file to content/<tool>/assets/ and reference /api/tools/<tool>/assets/<file>.",
                  },
                ]}
              />
            </div>
          </details>
          <details>
            <summary className="cursor-pointer text-sm font-medium text-indigo-700">Add an exercise</summary>
            <div className="mt-3 max-w-2xl">
              <AdminForm
                action={createExerciseAction}
                hidden={{ moduleId: m.id }}
                submitLabel="Add exercise"
                fields={[
                  { name: "title", label: "Title", maxLength: 150 },
                  { name: "description", label: "Task (Markdown)", multiline: true, maxLength: 10000 },
                ]}
              />
            </div>
          </details>
        </section>
      ))}
    </div>
  );
}