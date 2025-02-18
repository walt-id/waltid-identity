import Image from "next/image";

type Props = {
  height: number;
  width: number;
  className?: string;
};

export default function WaltIcon({ width, height, className }: Props) {
  return (
    <Image
      src="/logo.svg"
      alt="Logo"
      width={width}
      height={height}
      className={className}
    />
  );
}
