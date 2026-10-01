import Link from "next/link";
import { resetPasswordAction } from "@/app/actions";
import ResetPasswordForm from "@/components/ResetPasswordForm";

export default async function ResetPasswordPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  return (
    <div className="mx-auto max-w-sm space-y-4">
      <h1 className="text-2xl font-bold">Choose a new password</h1>
      {token ? (
        <ResetPasswordForm action={resetPasswordAction} token={token} />
      ) : (
        <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          This reset link is incomplete.{" "}
          <Link href="/forgot-password" className="underline">
            Request a new one
          </Link>
          .
        </p>
      )}
    </div>
  );
}