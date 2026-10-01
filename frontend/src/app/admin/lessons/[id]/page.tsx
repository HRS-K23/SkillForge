import Link from "next/link";
import { notFound } from "next/navigation";
import { updateLessonAction } from "@/app/actions";
import AdminForm from "@/components/AdminForm";
import { ApiError, api } from "@/lib/api";
import { requireAdmin } from "@/lib/session";

export default async function EditLessonPage({ params }: { params: Promise<{ id: string }> }) {
  await requireAdmin();
  const { id } = await params;
  let lesson;
  try {
    lesson = await api.lesson(id);
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.status === 400)) notFound();
    throw e;
  }
  return (
    <div className="max-w-2xl space-y-4">
      <Link href={`/admin/tools/${lesson.toolSlug}`} className="text-sm text-indigo-700 hover:underline">
        ← Back to {lesson.toolSlug}
      </Link>
      <h1 className="text-2xl font-bold">Edit lesson</h1>
      <AdminForm
        action={updateLessonAction}
        hidden={{ lessonId: lesson.id }}
        submitLabel="Save lesson"
        fields={[
          { name: "title", label: "Title", maxLength: 150, defaultValue: lesson.title },
          {
            name: "estimatedTime",
            label: "Estimated time (minutes)",
            type: "number",
            min: 0,
            optional: true,
            defaultValue: lesson.estimatedTime?.toString() ?? "",
          },
          {
            name: "youtubeUrl",
            label: "YouTube URL",
            type: "url",
            optional: true,
            maxLength: 300,
            defaultValue: lesson.youtubeUrl ?? "",
          },
          {
            name: "content",
            label: "Content (Markdown)",
            multiline: true,
            maxLength: 100000,
            defaultValue: lesson.content,
          },
        ]}
      />
    </div>
  );
}