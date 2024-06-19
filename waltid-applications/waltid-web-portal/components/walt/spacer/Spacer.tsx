export type SpacerProps = {
  size: number;
  type: 'horizontal' | 'vertical';
};

export default function Spacer({ size, type }: SpacerProps) {
  switch (type) {
    case 'horizontal':
      return <div className={`h-${size}`}></div>;
    case 'vertical':
      return <div className={`w-${size}`}></div>;
    default:
      throw new Error('Not implemented');
  }
}
