"use client";

import { useActionState } from "react";
import type { FormState } from "@/app/actions";
import ConfirmButton from "@/components/ConfirmButton";

export default function DeleteAccountForm({
  action,
}: {
  action: (state: FormState, data: FormData) => Promise<FormState>;
}) {
  const [state, formAction, pending] = useActionState(action, {});
  return (
    <form action={formAction} className="space-y-3">
      <label className="block text-sm font-medium">
        Confirm with your password
        <input
          name="password"
          type="password"
          autoComplete="current-password"
          required
          className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-slate-900"
        />
      </label>
      {state.error && (
        <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {state.error}
        </p>
      )}
      <ConfirmButton
        message="Delete your account? You will be signed out and will not be able to log in again."
        disabled={pending}
        className="rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
      >
        {pending ? "Please wait…" : "Delete my account"}
      </ConfirmButton>
    </form>
  );
}