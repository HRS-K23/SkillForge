import { updateProfileAction } from "@/app/actions";
import AuthForm from "@/components/AuthForm";
import { requireUser } from "@/lib/session";

export default async function ProfilePage() {
  const user = await requireUser();
  return (
    <div className="mx-auto max-w-sm space-y-4">
      <h1 className="text-2xl font-bold">Your profile</h1>
      <AuthForm
        action={updateProfileAction}
        submitLabel="Save"
        fields={[
          { name: "name", label: "Name", defaultValue: user.name, maxLength: 100 },
          { name: "email", label: "Email", type: "email", defaultValue: user.email, disabled: true },
        ]}
      />
      <p className="text-xs text-slate-500">
        Member since {new Date(user.createdAt).toLocaleDateString()}
      </p>
    </div>
  );
}
