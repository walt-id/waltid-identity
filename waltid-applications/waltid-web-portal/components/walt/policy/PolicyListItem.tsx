import Checkbox from "@/components/walt/forms/Checkbox";

type Props = {
  name: string;
  value: boolean;
  onChange: (value: boolean) => void;
};

export default function PolicyListItem({ name, value, onChange }: Props) {
  return (
    <div className="flex flex-row">
      <Checkbox value={value} onChange={onChange} />
      <span>{name}</span>
    </div>
  );
}
