import Spacer, {SpacerProps} from "@/components/walt/spacer/Spacer";

type Props = Pick<SpacerProps, 'size'>;

export default function VSpacer({ size }: Props) {
  return <Spacer type="vertical" size={size} />;
}
