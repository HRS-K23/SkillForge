import Link from "next/link";
import { redirect } from "next/navigation";
import { loginAction } from "@/app/actions";
import AuthForm from "@/components/AuthForm";
import { currentUser } from "@/lib/session";

export default async function LoginPage() {
  if (await currentUser()) redirect("/");
  return (
    <div className="mx-auto max-w-sm space-y-4">
      <h1 className="text-2xl font-bold">Log in</h1>
      <AuthForm
        action={loginAction}
        submitLabel="Log in"
        fields={[
          { name: "email", label: "Email", type: "email", autoComplete: "email" },
          { name: "password", label: "Password", type: "password", autoComplete: "current-password" },
        ]}
      />
      <p className="text-sm text-slate-600">
        New here?{" "}
        <Link href="/register" className="text-indigo-600 hover:underline">
          Create an account
        </Link>
      </p>
    </div>
  );
}
