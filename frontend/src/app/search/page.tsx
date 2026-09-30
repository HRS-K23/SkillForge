import Link from "next/link";
import { ApiError, api, type SearchHit, type SearchType } from "@/lib/api";

const TYPES: { value: SearchType | ""; label: string }[] = [
  { value: "", label: "All" },
  { value: "TOOL", label: "Tools" },
  { value: "LEARNING_PATH", label: "Learning paths" },
  { value: "LESSON", label: "Lessons" },
];

const LABEL: Record<SearchType, string> = {
  TOOL: "Tool",
  LEARNING_PATH: "Learning path",
  LESSON: "Lesson",
};

const hrefFor = (h: SearchHit) => (h.type === "LESSON" ? `/lessons/${h.id}` : `/tools/${h.toolSlug}`);

const pageHref = (q: string, type: string | undefined, page: number) =>
  `/search?q=${encodeURIComponent(q)}${type ? `&type=${type}` : ""}${page > 0 ? `&page=${page}` : ""}`;

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; type?: string; page?: string }>;
}) {
  const { q = "", type, page: pageParam } = await searchParams;
  const page = Math.max(0, Number.parseInt(pageParam ?? "0", 10) || 0);
  const selected = TYPES.find((t) => t.value === type)?.value || undefined;

  let hits: SearchHit[] = [];
  let pageSize = 20;
  let error: string | null = null;
  if (q) {
    try {
      const res = await api.search(q, selected as SearchType | undefined, page);
      hits = res.results;
      pageSize = res.size;
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) error = e.message;
      else throw e;
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Search</h1>
      <form className="flex gap-2">
        <input
          name="q"
          defaultValue={q}
          minLength={2}
          maxLength={100}
          required
          aria-label="Search query"
          className="flex-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
        />
        {selected && <input type="hidden" name="type" value={selected} />}
        <button className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">
          Search
        </button>
      </form>

      {q && (
        <nav className="flex gap-2" aria-label="Result type">
          {TYPES.map((t) => (
            <Link
              key={t.label}
              href={`/search?q=${encodeURIComponent(q)}${t.value ? `&type=${t.value}` : ""}`}
              className={`rounded-full border px-3 py-1 text-sm ${
                (selected ?? "") === t.value
                  ? "border-indigo-600 bg-indigo-600 text-white"
                  : "border-slate-300 bg-white hover:border-indigo-400"
              }`}
            >
              {t.label}
            </Link>
          ))}
        </nav>
      )}

      {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {q && !error && hits.length === 0 && (
        <p className="text-slate-600">
          No results for “{q}”. Search matches whole words, so try a full word.
        </p>
      )}
      <ul className="space-y-3">
        {hits.map((h) => (
          <li key={`${h.type}-${h.id}`}>
            <Link
              href={hrefFor(h)}
              className="block rounded-lg border border-slate-200 bg-white p-4 hover:border-indigo-400"
            >
              <span className="text-xs font-medium uppercase tracking-wide text-indigo-600">
                {LABEL[h.type]}
              </span>
              <h2 className="font-semibold">{h.title}</h2>
              {h.snippet && <p className="mt-1 line-clamp-2 text-sm text-slate-600">{h.snippet}</p>}
            </Link>
          </li>
        ))}
      </ul>

      {q && !error && (page > 0 || hits.length > 0) && (
        <nav className="flex items-center justify-between text-sm" aria-label="Pagination">
          {page > 0 ? <Link href={pageHref(q, selected, page - 1)}>← Previous</Link> : <span />}
          <span className="text-slate-600">Page {page + 1}</span>
          {hits.length >= pageSize ? <Link href={pageHref(q, selected, page + 1)}>Next →</Link> : <span />}
        </nav>
      )}
    </div>
  );
}
