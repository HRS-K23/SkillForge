import { changePasswordAction, deleteAccountAction, updateProfileAction } from "@/app/actions";
import AuthForm from "@/components/AuthForm";
import DeleteAccountForm from "@/components/DeleteAccountForm";
import { requireUser } from "@/lib/session";

export default async function ProfilePage() {
  const user = await requireUser();
  return (
    <div className="mx-auto max-w-sm space-y-8">
      <section className="space-y-4">
        <h1 className="text-2xl font-bold">Your profile</h1>
        <AuthForm
          action={updateProfileAction}
          submitLabel="Save"
          fields={[
            { name: "name", label: "Name", defaultValue: user.name, maxLength: 100 },
            { name: "email", label: "Email", type: "email", defaultValue: user.email, disabled: true },
          ]}
        />
        <p className="text-xs text-slate-500">Member since {new Date(user.createdAt).toLocaleDateString()}</p>
      </section>

      <section className="space-y-4">
        <h2 className="text-lg font-semibold">Change password</h2>
        <AuthForm
          action={changePasswordAction}
          submitLabel="Change password"
          fields={[
            { name: "currentPassword", label: "Current password", type: "password", autoComplete: "current-password" },
            {
              name: "newPassword",
              label: "New password (8–72 characters)",
              type: "password",
              autoComplete: "new-password",
              minLength: 8,
              maxLength: 72,
            },
          ]}
        />
      </section>

      <section className="space-y-3 rounded-lg border border-red-200 p-4">
        <h2 className="text-lg font-semibold text-red-700">Delete account</h2>
        <p className="text-sm text-slate-600">
          Your account will be deactivated and you will no longer be able to log in. This email address stays
          reserved.
        </p>
        <DeleteAccountForm action={deleteAccountAction} />
      </section>
    </div>
  );
}