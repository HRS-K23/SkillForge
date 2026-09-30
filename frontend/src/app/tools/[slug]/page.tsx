import Link from "next/link";
import { notFound } from "next/navigation";
import ProgressBar from "@/components/ProgressBar";
import { ApiError, api, type LearningPath, type ToolProgress } from "@/lib/api";
import { currentUser } from "@/lib/session";

export default async function ToolPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
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

  let progress: ToolProgress | null = null;
  if (path && (await currentUser())) {
    try {
      progress = await api.toolProgress(slug);
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
    }
  }
  const done = new Set(
    progress?.modules.flatMap((m) => m.lessons.filter((l) => l.completed).map((l) => l.lessonId)),
  );
  const moduleProgress = new Map(progress?.modules.map((m) => [m.moduleId, m]));

  return (
    <div className="space-y-6">
      <div>
        <Link href="/" className="text-sm text-indigo-600 hover:underline">
          ← All tools
        </Link>
        <span className="ml-3 text-xs font-medium uppercase tracking-wide text-indigo-600">
          {tool.category}
        </span>
        <h1 className="mt-1 text-3xl font-bold">{tool.name}</h1>
        <p className="mt-2 text-slate-600">{tool.description}</p>
      </div>

      {!path || path.modules.length === 0 ? (
        <p className="rounded-md bg-amber-50 px-4 py-3 text-sm text-amber-800">
          Lessons for {tool.name} are coming soon.
        </p>
      ) : (
        <>
          {progress ? (
            <ProgressBar
              percent={progress.percent}
              label={`Your progress: ${progress.completedLessons} of ${progress.totalLessons} lessons`}
            />
          ) : (
            <p className="text-sm text-slate-600">
              <Link href="/login" className="text-indigo-600 hover:underline">
                Log in
              </Link>{" "}
              to track your progress.
            </p>
          )}

          <ol className="space-y-6">
            {path.modules.map((m) => {
              const mp = moduleProgress.get(m.id);
              return (
                <li key={m.id} className="rounded-lg border border-slate-200 bg-white p-5">
                  <h2 className="text-xl font-semibold">
                    Module {m.order}: {m.title}
                  </h2>
                  {m.description && <p className="mt-1 text-sm text-slate-600">{m.description}</p>}
                  {mp && (
                    <div className="mt-3 max-w-xs">
                      <ProgressBar percent={mp.percent} label="Module completion" />
                    </div>
                  )}
                  <ul className="mt-4 divide-y divide-slate-100">
                    {m.lessons.map((l) => (
                      <li key={l.id}>
                        <Link
                          href={`/lessons/${l.id}`}
                          className="flex items-center gap-3 py-2 hover:text-indigo-700"
                        >
                          <span
                            aria-label={done.has(l.id) ? "Completed" : "Not completed"}
                            className={`flex h-5 w-5 items-center justify-center rounded-full border text-xs ${
                              done.has(l.id)
                                ? "border-green-600 bg-green-600 text-white"
                                : "border-slate-300"
                            }`}
                          >
                            {done.has(l.id) ? "✓" : ""}
                          </span>
                          <span className="flex-1">{l.title}</span>
                          {l.estimatedTime != null && (
                            <span className="text-xs text-slate-500">{l.estimatedTime} min</span>
                          )}
                        </Link>
                      </li>
                    ))}
                  </ul>
                  {m.exercises.length > 0 && (
                    <div className="mt-4 space-y-3">
                      {m.exercises.map((e) => (
                        <div key={e.id} className="rounded-md bg-indigo-50 p-3">
                          <h3 className="text-sm font-semibold text-indigo-900">
                            Exercise: {e.title}
                          </h3>
                          <p className="mt-1 whitespace-pre-line text-sm text-indigo-900">
                            {e.description}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                </li>
              );
            })}
          </ol>
        </>
      )}
    </div>
  );
}
