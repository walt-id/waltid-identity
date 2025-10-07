import Spacer, {SpacerProps} from "@/components/walt/spacer/Spacer";

type Props = Pick<SpacerProps, 'size'>;

export default function HSpacer({ size }: Props) {
  return <Spacer type="horizontal" size={size} />;
}
