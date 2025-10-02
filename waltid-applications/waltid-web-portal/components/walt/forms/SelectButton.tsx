import {ReactNode} from "react";

type Props = {
  children: ReactNode;
  selected?: boolean;
  icon?: any;
  [x: string]: any;
};

const SELECTED_CLASS = 'bg-gray-50 font-semibold';
const UNSELECTED_CLASS = 'bg-gray-100 text-gray-700';
const SELECTED_CLASS_ICON = 'text-primary-400';
const UNSELECTED_CLASS_ICON = 'text-gray-700';

export default function SelectButton({
  children,
  selected = false,
  icon: Icon,
  ...otherProps
}: Props) {
  let classButton =
    'flex flex-row gap-2 justify-center items-center rounded-lg py-2 px-8';
  let classIcon = 'h-4';

  if (selected) {
    classButton = classButton + ' ' + SELECTED_CLASS;
    classIcon = classIcon + ' ' + SELECTED_CLASS_ICON;
  } else {
    classButton = classButton + ' ' + UNSELECTED_CLASS;
    classIcon = classIcon + ' ' + UNSELECTED_CLASS_ICON;
  }

  return (
    <button className={classButton} {...otherProps}>
      {Icon && <Icon className={classIcon} />}
      {children}
    </button>
  );
}
