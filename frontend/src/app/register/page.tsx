import Link from "next/link";
import { redirect } from "next/navigation";
import { registerAction } from "@/app/actions";
import AuthForm from "@/components/AuthForm";
import { currentUser } from "@/lib/session";

export default async function RegisterPage() {
  if (await currentUser()) redirect("/");
  return (
    <div className="mx-auto max-w-sm space-y-4">
      <h1 className="text-2xl font-bold">Create your account</h1>
      <AuthForm
        action={registerAction}
        submitLabel="Sign up"
        fields={[
          { name: "name", label: "Name", autoComplete: "name", maxLength: 100 },
          { name: "email", label: "Email", type: "email", autoComplete: "email" },
          {
            name: "password",
            label: "Password (8–72 characters)",
            type: "password",
            autoComplete: "new-password",
            minLength: 8,
            maxLength: 72,
          },
        ]}
      />
      <p className="text-sm text-slate-600">
        Already registered?{" "}
        <Link href="/login" className="text-indigo-600 hover:underline">
          Log in
        </Link>
      </p>
    </div>
  );
}
