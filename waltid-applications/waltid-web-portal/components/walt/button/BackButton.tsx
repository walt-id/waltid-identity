import {useRouter} from "next/router";

export default function BackButton() {
  const router = useRouter();
  function handleCancel(e: any) {
    e.preventDefault();
    router.back();
  }

  return (
    <div className="absolute top-0 left-0 mt-5 ml-5">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        className="h-6 w-6 cursor-pointer"
        fill="none"
        viewBox="0 0 24 24"
        stroke="#000000"
        onClick={handleCancel}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M10 19l-7-7m0 0l7-7m-7 7h18"
        />
      </svg>
    </div>
  );
}
