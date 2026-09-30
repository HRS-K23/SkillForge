import Link from "next/link";
import { api } from "@/lib/api";

export default async function Home({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; category?: string; page?: string }>;
}) {
  const { q, category, page: pageParam } = await searchParams;
  const page = Math.max(0, Number.parseInt(pageParam ?? "0", 10) || 0);
  const [result, categories] = await Promise.all([
    api.tools(q, category, page),
    api.categories(),
  ]);
  const tools = result.items;
  const pageHref = (p: number) => {
    const params = new URLSearchParams();
    if (q) params.set("q", q);
    if (category) params.set("category", category);
    if (p > 0) params.set("page", String(p));
    const s = params.toString();
    return s ? `/?${s}` : "/";
  };

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-3xl font-bold">Learn software tools, step by step</h1>
        <p className="mt-2 text-slate-600">
          Structured learning paths with lessons and hands-on exercises.
        </p>
      </section>

      <form className="flex flex-wrap gap-2">
        <input
          name="q"
          defaultValue={q}
          placeholder="Filter tools by name or description"
          className="min-w-48 flex-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
        />
        {category && <input type="hidden" name="category" value={category} />}
        <button className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">
          Filter
        </button>
      </form>

      <nav className="flex flex-wrap gap-2" aria-label="Categories">
        <Chip href={q ? `/?q=${encodeURIComponent(q)}` : "/"} active={!category}>
          All
        </Chip>
        {categories.map((c) => (
          <Chip
            key={c}
            href={`/?category=${encodeURIComponent(c)}${q ? `&q=${encodeURIComponent(q)}` : ""}`}
            active={category?.toLowerCase() === c.toLowerCase()}
          >
            {c}
          </Chip>
        ))}
      </nav>

      {tools.length === 0 ? (
        <p className="text-slate-600">No tools match your filters.</p>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tools.map((t) => (
            <li key={t.id}>
              <Link
                href={`/tools/${t.slug}`}
                className="block h-full rounded-lg border border-slate-200 bg-white p-4 shadow-sm hover:border-indigo-400"
              >
                <span className="text-xs font-medium uppercase tracking-wide text-indigo-600">
                  {t.category}
                </span>
                <h2 className="mt-1 text-lg font-semibold">{t.name}</h2>
                <p className="mt-1 line-clamp-3 text-sm text-slate-600">{t.description}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {result.totalPages > 1 && (
        <nav className="flex items-center justify-between text-sm" aria-label="Pagination">
          {page > 0 ? <Link href={pageHref(page - 1)}>← Previous</Link> : <span />}
          <span className="text-slate-600">
            Page {page + 1} of {result.totalPages}
          </span>
          {page + 1 < result.totalPages ? <Link href={pageHref(page + 1)}>Next →</Link> : <span />}
        </nav>
      )}
    </div>
  );
}

function Chip({
  href,
  active,
  children,
}: {
  href: string;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      className={`rounded-full border px-3 py-1 text-sm ${
        active
          ? "border-indigo-600 bg-indigo-600 text-white"
          : "border-slate-300 bg-white hover:border-indigo-400"
      }`}
    >
      {children}
    </Link>
  );
}
