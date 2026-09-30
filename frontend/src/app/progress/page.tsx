import Link from "next/link";
import ProgressBar from "@/components/ProgressBar";
import { api } from "@/lib/api";
import { requireUser } from "@/lib/session";

export default async function ProgressPage() {
  await requireUser();
  const items = await api.overview();
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">My progress</h1>
      {items.length === 0 ? (
        <p className="text-slate-600">
          You haven’t completed any lessons yet.{" "}
          <Link href="/" className="text-indigo-600 hover:underline">
            Browse tools
          </Link>
        </p>
      ) : (
        <ul className="space-y-4">
          {items.map((t) => (
            <li key={t.toolSlug} className="rounded-lg border border-slate-200 bg-white p-5">
              <div className="flex items-baseline justify-between">
                <Link href={`/tools/${t.toolSlug}`} className="text-lg font-semibold hover:text-indigo-700">
                  {t.toolName}
                </Link>
                <span className="text-sm text-slate-500">
                  {t.completedLessons}/{t.totalLessons} lessons
                </span>
              </div>
              <div className="mt-3">
                <ProgressBar percent={t.percent} label="Overall" />
              </div>
              <ul className="mt-4 space-y-2">
                {t.modules.map((m) => (
                  <li key={m.moduleId}>
                    <ProgressBar percent={m.percent} label={m.title} />
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
