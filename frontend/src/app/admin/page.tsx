import Link from "next/link";
import { createToolAction, reloadContentAction } from "@/app/actions";
import AdminForm from "@/components/AdminForm";
import { api } from "@/lib/api";
import { requireAdmin } from "@/lib/session";

export default async function AdminPage() {
  await requireAdmin();
  const all = [];
  for (let page = 0; ; page++) {
    const res = await api.tools(undefined, undefined, page);
    all.push(...res.items);
    if (page + 1 >= res.totalPages) break;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold">Content admin</h1>
        <p className="mt-1 text-sm text-slate-600">
          Changes are written as Markdown files into the content folder. Commit them with Git to keep
          them version-controlled.
        </p>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Tools</h2>
        <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
          {all.map((t) => (
            <li key={t.id} className="flex items-center justify-between px-4 py-3">
              <div>
                <span className="font-medium">{t.name}</span>{" "}
                <span className="text-xs text-slate-500">{t.category}</span>
              </div>
              <Link href={`/admin/tools/${t.slug}`} className="text-sm text-indigo-700 hover:underline">
                Manage content
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Add a tool</h2>
        <div className="max-w-xl">
          <AdminForm
            action={createToolAction}
            submitLabel="Create tool"
            fields={[
              {
                name: "slug",
                label: "Slug",
                maxLength: 50,
                pattern: "[a-z0-9]+(-[a-z0-9]+)*",
                hint: "Lowercase letters, digits and hyphens. Used in the URL and folder name.",
              },
              { name: "name", label: "Name", maxLength: 100 },
              { name: "description", label: "Description", maxLength: 500 },
              { name: "category", label: "Category", maxLength: 50, hint: "For example Development, Design, DevOps." },
              { name: "logoUrl", label: "Logo URL", type: "url", optional: true, maxLength: 300 },
            ]}
          />
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Reload from disk</h2>
        <p className="text-sm text-slate-600">
          Use this after editing or pulling content files outside the UI.
        </p>
        <AdminForm action={reloadContentAction} fields={[]} submitLabel="Reload content" />
      </section>
    </div>
  );
}