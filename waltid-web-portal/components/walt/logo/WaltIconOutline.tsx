type Props = {
  height: number;
  width: number;
  className?: string;
};

export default function WaltIconOutline({ width, height, className }: Props) {
  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 47 47"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <circle cx="23.5" cy="23.5" r="22.5" stroke="#E6F6FF" strokeWidth="2" />
      <rect
        x="13.0156"
        y="15.1846"
        width="10.1231"
        height="2.89231"
        fill="#E6F6FF"
      />
      <path
        d="M37.6006 24.9465L35.0699 24.585C33.2622 34.3465 22.7776 34.3465 22.7776 34.3465L22.416 37.2388C33.4068 37.528 37.1186 29.1644 37.6006 24.9465Z"
        fill="#E6F6FF"
      />
    </svg>
  );
}
