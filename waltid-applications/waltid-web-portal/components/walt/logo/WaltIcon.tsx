type Props = {
  height: number;
  width: number;
  className?: string;
  type?: 'primary' | 'gray' | 'black' | 'white' | 'green' | 'yellow';
  outline?: boolean;
};

export default function WaltIcon({
  width,
  height,
  className,
  type = 'primary',
  outline = false,
}: Props) {
  let firstColor = '#0573F0';
  let secondColor = '#E6F6FF';

  if (type === 'gray') {
    firstColor = '#CBD2D9';
    secondColor = '#52606D';
  }

  if (type === 'black') {
    firstColor = '#111827';
    secondColor = '#f3f4f6';
  }

  if (type === 'white') {
    firstColor = '#F5F7FA';
    secondColor = '#0573F0';
  }

  if (type === 'green') {
    firstColor = '#1CD4D4';
    secondColor = '#07818F';
  }

  if (type === 'yellow') {
    firstColor = '#F0B429';
    secondColor = '#B44D12';
  }

  if (!outline) {
    return (
      <svg
        width={width}
        height={height}
        viewBox="0 0 141 141"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <circle cx="70.5" cy="70.5" r="70.5" fill={firstColor} />
        <rect
          x="39.0461"
          y="45.5538"
          width="30.3692"
          height="8.67692"
          fill={secondColor}
        />
        <path
          d="M112.8 74.8385L105.208 73.7538C99.7846 103.038 68.3307 103.038 68.3307 103.038L67.2461 111.715C100.218 112.583 111.354 87.4923 112.8 74.8385Z"
          fill={secondColor}
        />
      </svg>
    );
  } else {
    return (
      <svg
        width={width}
        height={height}
        viewBox="0 0 47 47"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <circle
          cx="23.5"
          cy="23.5"
          r="22.5"
          stroke={firstColor}
          strokeWidth="2"
        />
        <rect
          x="13.0156"
          y="15.1846"
          width="10.1231"
          height="2.89231"
          fill={firstColor}
        />
        <path
          d="M37.6006 24.9465L35.0699 24.585C33.2622 34.3465 22.7776 34.3465 22.7776 34.3465L22.416 37.2388C33.4068 37.528 37.1186 29.1644 37.6006 24.9465Z"
          fill={firstColor}
        />
      </svg>
    );
  }
}
