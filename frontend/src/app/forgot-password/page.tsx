import Link from "next/link";
import { forgotPasswordAction } from "@/app/actions";
import AuthForm from "@/components/AuthForm";

export default function ForgotPasswordPage() {
  return (
    <div className="mx-auto max-w-sm space-y-4">
      <h1 className="text-2xl font-bold">Forgot your password?</h1>
      <p className="text-sm text-slate-600">Enter your email and we will send you a link to choose a new one.</p>
      <AuthForm
        action={forgotPasswordAction}
        submitLabel="Send reset link"
        fields={[{ name: "email", label: "Email", type: "email", autoComplete: "email" }]}
      />
      <p className="text-sm text-slate-600">
        <Link href="/login" className="text-indigo-600 hover:underline">
          Back to log in
        </Link>
      </p>
    </div>
  );
}