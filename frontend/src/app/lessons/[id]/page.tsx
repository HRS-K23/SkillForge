import Link from "next/link";
import { notFound } from "next/navigation";
import { setCompletedAction } from "@/app/actions";
import Markdown from "@/components/Markdown";
import { ApiError, api } from "@/lib/api";
import { currentUser } from "@/lib/session";

export default async function LessonPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let lesson;
  try {
    lesson = await api.lesson(id);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound();
    throw e;
  }

  const user = await currentUser();
  let completed = false;
  if (user) {
    try {
      const p = await api.toolProgress(lesson.toolSlug);
      completed = p.modules.some((m) => m.lessons.some((l) => l.lessonId === id && l.completed));
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
    }
  }

  return (
    <article className="space-y-6">
      <Link href={`/tools/${lesson.toolSlug}`} className="text-sm text-indigo-600 hover:underline">
        ← Back to learning path
      </Link>
      <header>
        <h1 className="text-3xl font-bold">{lesson.title}</h1>
        {lesson.estimatedTime != null && (
          <p className="mt-1 text-sm text-slate-500">Estimated time: {lesson.estimatedTime} minutes</p>
        )}
      </header>

      {lesson.youtubeEmbedUrl && (
        <div className="aspect-video w-full overflow-hidden rounded-lg bg-black">
          <iframe
            src={lesson.youtubeEmbedUrl}
            title={`${lesson.title} video`}
            className="h-full w-full"
            allow="accelerometer; encrypted-media; picture-in-picture"
            allowFullScreen
            referrerPolicy="strict-origin-when-cross-origin"
          />
        </div>
      )}

      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <Markdown>{lesson.content}</Markdown>
      </div>

      {user ? (
        <form action={setCompletedAction}>
          <input type="hidden" name="lessonId" value={lesson.id} />
          <input type="hidden" name="completed" value={String(!completed)} />
          <input type="hidden" name="redirectTo" value={`/lessons/${lesson.id}`} />
          <button
            className={`rounded-md px-4 py-2 text-sm font-semibold ${
              completed
                ? "border border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                : "bg-green-600 text-white hover:bg-green-700"
            }`}
          >
            {completed ? "✓ Completed — mark as not completed" : "Mark as completed"}
          </button>
        </form>
      ) : (
        <p className="text-sm text-slate-600">
          <Link href="/login" className="text-indigo-600 hover:underline">
            Log in
          </Link>{" "}
          to mark lessons as completed.
        </p>
      )}
    </article>
  );
}
