import Link from "next/link";
import { logoutAction } from "@/app/actions";
import { currentUser } from "@/lib/session";

export default async function Header() {
  const user = await currentUser();
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-4 px-4 py-3">
        <Link href="/" className="text-lg font-bold text-indigo-700">
          SkillForge
        </Link>
        <form action="/search" className="flex-1 min-w-40">
          <input
            name="q"
            type="search"
            placeholder="Search tools and lessons"
            minLength={2}
            maxLength={100}
            aria-label="Search"
            className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm"
          />
        </form>
        <nav className="flex items-center gap-4 text-sm">
          {user ? (
            <>
              <Link href="/progress" className="hover:underline">
                My progress
              </Link>
              <Link href="/profile" className="hover:underline">
                {user.name}
              </Link>
              <form action={logoutAction}>
                <button type="submit" className="text-slate-600 hover:underline">
                  Log out
                </button>
              </form>
            </>
          ) : (
            <>
              <Link href="/login" className="hover:underline">
                Log in
              </Link>
              <Link
                href="/register"
                className="rounded-md bg-indigo-600 px-3 py-1.5 font-semibold text-white hover:bg-indigo-700"
              >
                Sign up
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
