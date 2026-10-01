import Link from "next/link";
import { redirect } from "next/navigation";
import { loginAction } from "@/app/actions";
import AuthForm from "@/components/AuthForm";
import { currentUser } from "@/lib/session";

type Search = { expired?: string; ended?: string; reset?: string; deleted?: string };

function notice(s: Search): { tone: "warn" | "ok"; text: string } | null {
  if (s.expired) return { tone: "warn", text: "Your session has expired. Please log in again." };
  if (s.ended) return { tone: "warn", text: "You were signed out. Please log in again." };
  if (s.reset) return { tone: "ok", text: "Password updated. Log in with your new password." };
  if (s.deleted) return { tone: "ok", text: "Your account has been deleted." };
  return null;
}

export default async function LoginPage({ searchParams }: { searchParams: Promise<Search> }) {
  if (await currentUser()) redirect("/");
  const message = notice(await searchParams);
  return (
    <div className="mx-auto max-w-sm space-y-4">
      <h1 className="text-2xl font-bold">Log in</h1>
      {message && (
        <p
          role="status"
          className={`rounded-md px-3 py-2 text-sm ${
            message.tone === "warn" ? "bg-amber-50 text-amber-800" : "bg-green-50 text-green-700"
          }`}
        >
          {message.text}
        </p>
      )}
      <AuthForm
        action={loginAction}
        submitLabel="Log in"
        fields={[
          { name: "email", label: "Email", type: "email", autoComplete: "email" },
          { name: "password", label: "Password", type: "password", autoComplete: "current-password" },
        ]}
      />
      <p className="text-sm text-slate-600">
        <Link href="/forgot-password" className="text-indigo-600 hover:underline">
          Forgot your password?
        </Link>
      </p>
      <p className="text-sm text-slate-600">
        New here?{" "}
        <Link href="/register" className="text-indigo-600 hover:underline">
          Create an account
        </Link>
      </p>
    </div>
  );
}