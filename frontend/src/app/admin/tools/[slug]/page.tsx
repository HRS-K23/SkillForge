import Link from "next/link";
import { notFound } from "next/navigation";
import {
  createExerciseAction,
  createLessonAction,
  createModuleAction,
  updateExerciseAction,
  updateModuleAction,
  updateToolAction,
} from "@/app/actions";
import AdminForm from "@/components/AdminForm";
import { DeleteButton, MoveButtons } from "@/components/AdminControls";
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
  const modules = path?.modules ?? [];
  const moduleIds = modules.map((m) => m.id);

  return (
    <div className="space-y-8">
      <div>
        <Link href="/admin" className="text-sm text-indigo-700 hover:underline">
          ← Content admin
        </Link>
        <h1 className="mt-2 text-2xl font-bold">{tool.name}</h1>
        <div className="flex items-center gap-4">
          <Link href={`/tools/${slug}`} className="text-sm text-slate-600 hover:underline">
            View public page
          </Link>
          <DeleteButton
            kind="tool"
            id={slug}
            label="Delete tool"
            message={`Delete ${tool.name} and all of its modules, lessons and learner progress? This cannot be undone.`}
          />
        </div>
      </div>

      <details className="rounded-lg border border-slate-200 bg-white p-4">
        <summary className="cursor-pointer text-sm font-medium text-indigo-700">Edit tool details</summary>
        <div className="mt-3 max-w-xl">
          <AdminForm
            action={updateToolAction}
            hidden={{ slug }}
            submitLabel="Save tool"
            fields={[
              { name: "name", label: "Name", maxLength: 100, defaultValue: tool.name },
              { name: "description", label: "Description", maxLength: 500, defaultValue: tool.description },
              { name: "category", label: "Category", maxLength: 50, defaultValue: tool.category },
              {
                name: "logoUrl",
                label: "Logo URL",
                type: "url",
                optional: true,
                maxLength: 300,
                defaultValue: tool.logoUrl ?? "",
              },
            ]}
          />
        </div>
      </details>

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

      {modules.map((m) => {
        const lessonIds = m.lessons.map((l) => l.id);
        const exerciseIds = m.exercises.map((x) => x.id);
        return (
          <section key={m.id} className="space-y-4 rounded-lg border border-slate-200 bg-white p-4">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-lg font-semibold">
                  Module {m.order}: {m.title}
                </h2>
                <p className="text-sm text-slate-600">
                  {m.lessons.length} lessons, {m.exercises.length} exercises
                </p>
              </div>
              <div className="flex items-center gap-2">
                <MoveButtons kind="modules" parent={slug} ids={moduleIds} id={m.id} />
                <DeleteButton
                  kind="module"
                  id={m.id}
                  message={`Delete module "${m.title}" with all its lessons and exercises? Learner progress on its lessons is lost.`}
                />
              </div>
            </div>

            <details>
              <summary className="cursor-pointer text-sm font-medium text-indigo-700">Edit module</summary>
              <div className="mt-3 max-w-xl">
                <AdminForm
                  action={updateModuleAction}
                  hidden={{ moduleId: m.id }}
                  submitLabel="Save module"
                  fields={[
                    { name: "title", label: "Title", maxLength: 150, defaultValue: m.title },
                    {
                      name: "description",
                      label: "Description",
                      optional: true,
                      maxLength: 500,
                      defaultValue: m.description ?? "",
                    },
                  ]}
                />
              </div>
            </details>

            <div>
              <h3 className="text-sm font-semibold text-slate-700">Lessons</h3>
              <ul className="mt-1 divide-y divide-slate-100 text-sm">
                {m.lessons.map((l) => (
                  <li key={l.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <span>{l.title}</span>
                    <span className="flex items-center gap-2">
                      <MoveButtons kind="lessons" parent={m.id} ids={lessonIds} id={l.id} />
                      <Link
                        href={`/admin/lessons/${l.id}`}
                        className="rounded border border-slate-300 px-2 py-0.5 text-xs text-slate-700 hover:bg-slate-100"
                      >
                        Edit
                      </Link>
                      <DeleteButton
                        kind="lesson"
                        id={l.id}
                        message={`Delete lesson "${l.title}"? Learner progress on it is lost.`}
                      />
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <h3 className="text-sm font-semibold text-slate-700">Exercises</h3>
              <ul className="mt-1 divide-y divide-slate-100 text-sm">
                {m.exercises.map((x) => (
                  <li key={x.id} className="py-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span>{x.title}</span>
                      <span className="flex items-center gap-2">
                        <MoveButtons kind="exercises" parent={m.id} ids={exerciseIds} id={x.id} />
                        <DeleteButton kind="exercise" id={x.id} message={`Delete exercise "${x.title}"?`} />
                      </span>
                    </div>
                    <details className="mt-1">
                      <summary className="cursor-pointer text-xs font-medium text-indigo-700">Edit</summary>
                      <div className="mt-2 max-w-2xl">
                        <AdminForm
                          action={updateExerciseAction}
                          hidden={{ exerciseId: x.id }}
                          submitLabel="Save exercise"
                          fields={[
                            { name: "title", label: "Title", maxLength: 150, defaultValue: x.title },
                            {
                              name: "description",
                              label: "Task (Markdown)",
                              multiline: true,
                              maxLength: 10000,
                              defaultValue: x.description,
                            },
                          ]}
                        />
                      </div>
                    </details>
                  </li>
                ))}
              </ul>
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
        );
      })}
    </div>
  );
}